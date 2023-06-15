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
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
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
import java.util.concurrent.TimeUnit;
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

  private final ListeningScheduledExecutorService executor;
  private final SqlAdminApiFetcher apiFetcher;
  private final AuthType authType;
  private final Optional<OAuth2Credentials> iamAuthnCredentials;
  private final Optional<HttpRequestInitializer> tokenSource;
  private final CloudSqlInstanceName instanceName;
  private final ListenableFuture<KeyPair> keyPair;
  private final Object instanceDataGuard = new Object();
  // Limit forced refreshes to 1 every minute.
  private final RateLimiter<Object> forcedRenewRateLimiter =
      RateLimiter.burstyBuilder(2, Duration.ofSeconds(30)).build();

  private final RefreshCalculator refreshCalculator = new RefreshCalculator();

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> currentInstanceData;

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> nextInstanceData;

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
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair) {
    this.instanceName = new CloudSqlInstanceName(connectionName);
    this.apiFetcher = apiFetcher;
    this.authType = authType;
    this.executor = executor;
    this.keyPair = keyPair;

    if (authType == AuthType.IAM) {
      HttpRequestInitializer source = tokenSourceFactory.create();
      this.tokenSource = Optional.ofNullable(source);
      this.iamAuthnCredentials = Optional.of(parseCredentials(tokenSource.get()));
    } else {
      this.tokenSource = Optional.empty();
      this.iamAuthnCredentials = Optional.empty();
    }

    synchronized (instanceDataGuard) {
      this.currentInstanceData = executor.submit(this::performRefresh);
      this.nextInstanceData = currentInstanceData;
    }
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
      Credentials c = adapter.getCredentials();
      if (c != null && c instanceof OAuth2Credentials) {
        return (OAuth2Credentials) c;
      }
      throw new RuntimeException(
          String.format(
              "[%s] Unable to connect via automatic IAM authentication: "
                  + "HttpCredentialsAdapter did not create valid credentials. %s, %s",
              instanceName.getConnectionName(), source.getClass().getName(), c));
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
    throw new RuntimeException(
        String.format(
            "[%s] Unable to connect via automatic IAM authentication: "
                + "Unsupported credentials of type %s",
            instanceName.getConnectionName(), source.getClass().getName()));
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
  void forceRefresh() {
    synchronized (instanceDataGuard) {
      // If a scheduled refresh hasn't started, perform one immediately
      if (nextInstanceData.cancel(false)) {
        currentInstanceData = executor.submit(this::performRefresh);
        nextInstanceData = currentInstanceData;
      } else {
        // Otherwise it's already running, so just move next to current.
        currentInstanceData = nextInstanceData;
      }
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API. Replaces the
   * value of currentInstanceData and schedules the next refresh shortly before the information
   * would expire.
   */
  private InstanceData performRefresh() throws InterruptedException, ExecutionException {
    // To avoid unreasonable SQL Admin API usage, use a rate limit to throttle our usage.
    forcedRenewRateLimiter.acquirePermit();

    try {
      InstanceData data =
          apiFetcher.getInstanceData(
              this.instanceName, this::accessTokenSupplier, this.authType, executor, keyPair);

      synchronized (instanceDataGuard) {
        // update currentInstanceData with the most recent results
        currentInstanceData = Futures.immediateFuture(data);

        // schedule a replacement before the SSLContext expires;
        nextInstanceData =
            executor.schedule(
                this::performRefresh,
                refreshCalculator.calculateSecondsUntilNextRefresh(
                    Instant.now(), data.getExpiration().toInstant()),
                TimeUnit.SECONDS);
      }

      return data;
    } catch (ExecutionException | InterruptedException e) {
      synchronized (instanceDataGuard) {
        nextInstanceData = executor.submit(this::performRefresh);
      }
      throw e;
    }
  }

  private Optional<AccessToken> accessTokenSupplier() throws IOException {
    final GoogleCredentials credentials;
    if (iamAuthnCredentials.isPresent()) {
      credentials = (GoogleCredentials) parseCredentials(tokenSource.get());
      credentials.refreshIfExpired();
      GoogleCredentials downscoped = getDownscopedCredentials(credentials);
      logger.info("Credentials type: " + credentials.getClass().getName() + " " + credentials);
      logger.info("Downscoped type: " + downscoped.getClass().getName() + " " + downscoped);
      return Optional.ofNullable(downscoped.getAccessToken());
    }
    return Optional.empty();
  }

  private Optional<Date> getTokenExpirationTime(Credential credentials) {
    return Optional.ofNullable(credentials.getExpirationTimeMilliseconds()).map(Date::new);
  }

  SslData getSslData() {
    return getInstanceData().getSslData();
  }
}
