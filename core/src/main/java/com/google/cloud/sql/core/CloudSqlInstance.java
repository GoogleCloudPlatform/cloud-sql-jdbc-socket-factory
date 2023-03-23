/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import dev.failsafe.RateLimiter;
import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;

/**
 * This class manages information on and creates connections to a Cloud SQL instance using the Cloud
 * SQL Admin API. The operations to retrieve information with the API are largely done
 * asynchronously, and this class should be considered threadsafe.
 */
class CloudSqlInstance {

  private static final Logger logger = Logger.getLogger(CloudSqlInstance.class.getName());

  private static final String SQL_LOGIN_SCOPE = "https://www.googleapis.com/auth/sqlservice.login";

  // defaultRefreshBuffer is the minimum amount of time for which a
  // certificate must be valid to ensure the next refresh attempt has adequate
  // time to complete.
  private static final Duration DEFAULT_REFRESH_BUFFER = Duration.ofMinutes(4);
  private final ScheduledExecutorService executor;
  private final SqlAdminApiFetcher apiFetcher;
  private final AuthType authType;
  private final Optional<OAuth2Credentials> credentials;

  private final CloudSqlInstanceName instanceName;

  private final KeyPair keyPair;
  private final Object instanceDataGuard = new Object();
  // Limit forced refreshes to 1 every minute.
  private final RateLimiter<Object> forcedRenewRateLimiter =
      RateLimiter.burstyBuilder(2, Duration.ofSeconds(30)).build();
  private final ListeningScheduledExecutorService listeningExecutor;

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> currentInstanceData;

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<ListenableFuture<InstanceData>> nextInstanceData;

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param apiFetcher Service class for interacting with the Cloud SQL Admin API
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
  CloudSqlInstance(
      String connectionName,
      SqlAdminApiFetcher apiFetcher,
      AuthType authType,
      CredentialFactory tokenSourceFactory,
      ScheduledExecutorService executor,
      KeyPair keyPair)
      throws IOException, InterruptedException, ExecutionException {

    this.instanceName = new CloudSqlInstanceName(connectionName);

    this.apiFetcher = apiFetcher;
    this.authType = authType;
    this.executor = executor;
    this.listeningExecutor = MoreExecutors.listeningDecorator(executor);
    this.keyPair = keyPair;

    if (authType == AuthType.IAM) {
      HttpRequestInitializer source = tokenSourceFactory.create();

      this.credentials = Optional.of(parseCredentials(source));
      this.credentials.get().refresh();
    } else {
      this.credentials = Optional.empty();
    }

    // Kick off initial async jobs
    synchronized (instanceDataGuard) {
      this.currentInstanceData = performRefresh();
      this.nextInstanceData = Futures.immediateFuture(currentInstanceData);
    }
  }

  /** Returns a future that blocks until the result of a nested future is complete. */
  private static <T> ListenableFuture<T> blockOnNestedFuture(
      ListenableFuture<ListenableFuture<T>> nestedFuture, ScheduledExecutorService executor) {
    SettableFuture<T> blockedFuture = SettableFuture.create();
    // Once the nested future is complete, update the blocked future to match
    Futures.addCallback(
        nestedFuture,
        new FutureCallback<ListenableFuture<T>>() {
          @Override
          public void onSuccess(ListenableFuture<T> result) {
            blockedFuture.setFuture(result);
          }

          @Override
          public void onFailure(Throwable throwable) {
            blockedFuture.setException(throwable);
          }
        },
        executor);
    return blockedFuture;
  }

  static long secondsUntilRefresh(Date expiration) {
    Duration timeUntilExp = Duration.between(Instant.now(), expiration.toInstant());

    if (timeUntilExp.compareTo(Duration.ofHours(1)) < 0) {
      if (timeUntilExp.compareTo(DEFAULT_REFRESH_BUFFER) < 0) {
        // If the time until the certificate expires is less the refresh buffer, schedule the
        // refresh immediately
        return 0;
      }
      // Otherwise schedule a refresh in (timeUntilExp - buffer) seconds
      return timeUntilExp.minus(DEFAULT_REFRESH_BUFFER).getSeconds();
    }
    // If the time until the certificate expires is longer than an hour, return timeUntilExp//2
    return timeUntilExp.dividedBy(2).getSeconds();
  }

  static GoogleCredentials getDownscopedCredentials(OAuth2Credentials credentials) {
    GoogleCredentials downscoped;
    try {
      GoogleCredentials oldCredentials = (GoogleCredentials) credentials;
      downscoped = oldCredentials.createScoped(SQL_LOGIN_SCOPE);
    } catch (ClassCastException ex) {
      throw new RuntimeException("Failed to downscope credentials for IAM Authentication:", ex);
    }
    return downscoped;
  }

  private OAuth2Credentials parseCredentials(HttpRequestInitializer source) {
    if (source instanceof HttpCredentialsAdapter) {
      HttpCredentialsAdapter adapter = (HttpCredentialsAdapter) source;
      return (OAuth2Credentials) adapter.getCredentials();
    }

    if (source instanceof Credential) {
      Credential credential = (Credential) source;
      AccessToken accessToken =
          new AccessToken(
              credential.getAccessToken(), getTokenExpirationTime(credential).orElse(null));

      return new GoogleCredentials(accessToken) {
        @Override
        public AccessToken refreshAccessToken() throws IOException {
          credential.refreshToken();

          return new AccessToken(
              credential.getAccessToken(), getTokenExpirationTime(credential).orElse(null));
        }
      };
    }

    throw new RuntimeException("Not supporting credentials of type " + source.getClass().getName());
  }

  /**
   * Returns the current data related to the instance from {@link #performRefresh()}. May block if
   * no valid data is currently available.
   */
  private InstanceData getInstanceData() {
    ListenableFuture<InstanceData> instanceData;
    synchronized (instanceDataGuard) {
      instanceData = currentInstanceData;
    }
    try {
      // TODO(kvg): Let exceptions up to here before adding context
      return Uninterruptibles.getUninterruptibly(instanceData);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      Throwables.throwIfUnchecked(cause);
      throw new RuntimeException(cause);
    }
  }

  /**
   * Returns an unconnected {@link SSLSocket} using the SSLContext associated with the instance. May
   * block until required instance data is available.
   */
  SSLSocket createSslSocket() throws IOException {
    return (SSLSocket) getInstanceData().getSslContext().getSocketFactory().createSocket();
  }

  /**
   * Returns the first IP address for the instance, in order of the preference supplied by
   * preferredTypes.
   *
   * @param preferredTypes Preferred instance IP types to use. Valid IP types include "Public" and
   *     "Private".
   * @return returns a string representing the IP address for the instance
   * @throws IllegalArgumentException If the instance has no IP addresses matching the provided
   *     preferences.
   */
  String getPreferredIp(List<String> preferredTypes) {
    Map<String, String> ipAddrs = getInstanceData().getIpAddrs();
    for (String ipType : preferredTypes) {
      String preferredIp = ipAddrs.get(ipType);
      if (preferredIp != null) {
        return preferredIp;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "[%s] Cloud SQL instance  does not have any IP addresses matching preferences (%s)",
            instanceName.getConnectionName(), String.join(", ", preferredTypes)));
  }

  /**
   * Attempts to force a new refresh of the instance data. May fail if called too frequently or if a
   * new refresh is already in progress. If successful, other methods will block until refresh has
   * been completed.
   */
  void forceRefresh() throws InterruptedException, ExecutionException {
    synchronized (instanceDataGuard) {
      // If a scheduled refresh hasn't started, perform one immediately
      if (nextInstanceData.cancel(false)) {
        currentInstanceData = performRefresh();
        nextInstanceData = Futures.immediateFuture(currentInstanceData);
      } else {
        // Otherwise it's already running, so just block on the results
        currentInstanceData = blockOnNestedFuture(nextInstanceData, executor);
      }
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API. Replaces the
   * value of currentInstanceData and schedules the next refresh shortly before the information
   * would expire.
   */
  private ListenableFuture<InstanceData> performRefresh()
      throws InterruptedException, ExecutionException {
    // To avoid unreasonable SQL Admin API usage, use a rate limit to throttle our usage.
    forcedRenewRateLimiter.acquirePermit();

    ListenableFuture<InstanceData> refreshFuture;
    if (authType == AuthType.IAM) {
      if (credentials.isPresent()) {
        GoogleCredentials downscopedCredentials = getDownscopedCredentials(credentials.get());
        refreshFuture =
            Futures.immediateFuture(
                apiFetcher.getInstanceData(
                    instanceName, downscopedCredentials, AuthType.IAM, executor, keyPair));
      } else {
        throw new RuntimeException(
            String.format(
                "[%s] Unable to connect via automatic IAM authentication: Missing credentials.",
                instanceName.getConnectionName()));
      }

    } else {
      refreshFuture =
          Futures.immediateFuture(
              apiFetcher.getInstanceData(instanceName, null, AuthType.PASSWORD, executor, keyPair));
    }
    Futures.addCallback(
        refreshFuture,
        new FutureCallback<InstanceData>() {
          @Override
          public void onSuccess(InstanceData instanceData) {
            synchronized (instanceDataGuard) {
              // update currentInstanceData with the most recent results
              currentInstanceData = refreshFuture;
              // schedule a replacement before the SSLContext expires;
              nextInstanceData =
                  listeningExecutor.schedule(
                      () -> performRefresh(),
                      secondsUntilRefresh(getInstanceData().getExpiration()),
                      TimeUnit.SECONDS);
            }
          }

          @Override
          public void onFailure(Throwable t) {
            logger.log(
                Level.WARNING,
                "An error occurred while performing refresh. Retrying immediately.",
                t);
            synchronized (instanceDataGuard) {
              InstanceData instanceData = null;
              try {
                instanceData = getInstanceData();
              } catch (Exception e) {
                // this means the result was invalid
              }
              if (instanceData == null
                  || instanceData.getExpiration().toInstant().isBefore(Instant.now())) {
                // replace current if it is expired or invalid
                currentInstanceData = refreshFuture;
              }
              try {
                nextInstanceData = Futures.immediateFuture(performRefresh());
              } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
              }
            }
          }
        },
        executor);

    return refreshFuture;
  }

  private Optional<Date> getTokenExpirationTime(Credential credentials) {
    return Optional.ofNullable(credentials.getExpirationTimeMilliseconds()).map(Date::new);
  }

  SslData getSslData() {
    return getInstanceData().getSslData();
  }
}
