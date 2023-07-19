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

import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.SettableFuture;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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

  private final ListeningScheduledExecutorService executor;
  private final InstanceDataSupplier instanceDataSupplier;
  private final AuthType authType;
  private final AccessTokenSupplier accessTokenSupplier;
  private final CloudSqlInstanceName instanceName;
  private final ListenableFuture<KeyPair> keyPair;
  private final Object instanceDataGuard = new Object();
  // Limit forced refreshes to 1 every minute.
  private final RateLimiter forcedRenewRateLimiter;

  private final RefreshCalculator refreshCalculator = new RefreshCalculator();
  private final Duration getDataTimeout;

  @GuardedBy("instanceDataGuard")
  private SettableFuture<InstanceData> currentInstanceData = SettableFuture.create();

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<?> nextInstanceData;

  @GuardedBy("instanceDataGuard")
  private boolean forceRefreshRunning;

  @GuardedBy("instanceDataGuard")
  private Throwable lastFailedAttempt;

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param instanceDataSupplier Service class for interacting with the Cloud SQL Admin API
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
  CloudSqlInstance(
      String connectionName,
      InstanceDataSupplier instanceDataSupplier,
      AuthType authType,
      CredentialFactory tokenSourceFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair,
      RateLimiter forcedRenewRateLimiter,
      Duration getDataTimeout) {
    this.instanceName = new CloudSqlInstanceName(connectionName);
    this.instanceDataSupplier = instanceDataSupplier;
    this.authType = authType;
    this.executor = executor;
    this.keyPair = keyPair;
    this.forcedRenewRateLimiter = forcedRenewRateLimiter;
    this.getDataTimeout = getDataTimeout;

    if (authType == AuthType.IAM) {
      HttpRequestInitializer source = tokenSourceFactory.create();
      this.accessTokenSupplier = new DefaultAccessTokenSupplier(source);
    } else {
      this.accessTokenSupplier = Optional::empty;
    }

    forceRefresh();
  }

  /**
   * Blocks until there is a value in currentInstanceData, and then returns the current data related
   * to the instance from {@link #performRefresh()}. May block if no valid data is currently
   * available.
   *
   * @throws InterruptedException if this operation times out after 30 seconds.
   */
  private InstanceData getInstanceData() throws InstanceDataTimeoutException {
    // Thread-safely get a local reference to the currentInstanceData future.
    SettableFuture<InstanceData> data;
    Throwable lastAttempt;
    synchronized (this.instanceDataGuard) {
      data = this.currentInstanceData;
      lastAttempt = this.lastFailedAttempt;
    }

    try {
      // Attempt to get the currentInstanceData, respecting the operation timeout
      return data.get(getDataTimeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException | InterruptedException e) {
      // When the future was not resolved within the timeout, or if this thread was interrupted
      if (lastAttempt != null) {
        // If the last attempt failed, use that attempt's exception as the cause so that
        // users can see the exceptions thrown during refresh attempts.
        throw new InstanceDataTimeoutException(
            "Timeout while fetching data for instance "
                + instanceName
                + ". The last attempt to fetch instance data failed. ",
            lastAttempt);
      } else {
        // If no previous attempt has failed, then the refresh operation has
        // never completed.
        throw new InstanceDataTimeoutException(
            "Timeout while fetching data for instance " + instanceName);
      }
    } catch (Exception e) {
      // If it is some other exception, then throw it.
      throw new InstanceDataTimeoutException(
          "Exception thrown while fetching data for instance " + instanceName, e);
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
  String getPreferredIp(List<String> preferredTypes) throws InstanceDataTimeoutException {
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
    // update instanceData fields
    synchronized (instanceDataGuard) {
      // Don't force a refresh until the current forceRefresh operation
      // has produced a successful refresh.
      if (forceRefreshRunning) {
        return;
      }

      // Put an unresolved future in currentInstanceData. Calls to getInstanceData() will block
      // until this refresh operation succeeds.
      currentInstanceData = SettableFuture.create();

      // Set forceRefresh so that only one operation runs at a time
      forceRefreshRunning = true;
      if (nextInstanceData != null) {
        // Cancel any scheduled refresh attempts
        nextInstanceData.cancel(false);
        logger.fine(
            String.format(
                "[%s] Force Refresh: the next refresh operation was cancelled.", instanceName));
      }
      logger.fine(
          String.format(
              "[%s] Force Refresh: Scheduling new refresh operation immediately.", instanceName));

      // Schedule a new refresh attempt immediately.
      nextInstanceData = executor.submit(this::performRefresh);
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API. Replaces the
   * value of currentInstanceData and schedules the next refresh shortly before the information
   * would expire.
   */
  private void performRefresh() {
    logger.fine(
        String.format("[%s] Refresh Operation: Acquiring rate limiter permit.", instanceName));
    // To avoid unreasonable SQL Admin API usage, use a rate limit to throttle our usage.
    forcedRenewRateLimiter.acquire();
    logger.fine(
        String.format(
            "[%s] Refresh Operation: Acquired rate limiter permit. Starting refresh...",
            instanceName));

    ListenableFuture<InstanceData> dataFuture =
        instanceDataSupplier.getInstanceData(
            this.instanceName, this.accessTokenSupplier, this.authType, executor, keyPair);

    // After the future is complete...
    dataFuture.addListener(
        () -> {
          try {
            // If the attempt succeeded, this will return a value.
            // If the attempt failed, this will throw an exception.
            InstanceData data = Futures.getDone(dataFuture);
            logger.fine(
                String.format(
                    "[%s] Refresh Operation: Completed refresh "
                        + "with new certificate expiration at %s.",
                    instanceName, data.getExpiration().toInstant().toString()));

            // Figure out how long until the next refresh attempt after a successful
            // request.
            long secondsToRefresh =
                refreshCalculator.calculateSecondsUntilNextRefresh(
                    Instant.now(), data.getExpiration().toInstant());

            logger.fine(
                String.format(
                    "[%s] Refresh Operation: Next operation scheduled at %s.",
                    instanceName,
                    Instant.now()
                        .plus(secondsToRefresh, ChronoUnit.SECONDS)
                        .truncatedTo(ChronoUnit.SECONDS)
                        .toString()));

            // Update the instanceData and associated fields
            synchronized (instanceDataGuard) {
              // Refresh completed successfully, reset forceRefreshRunning.
              forceRefreshRunning = false;

              // Clear lastFailedAttempt exception since this attempt succeeded.
              lastFailedAttempt = null;

              if (!currentInstanceData.isDone()) {
                // if currentInstanceData future is not yet done, then it needs this value.
                currentInstanceData.set(data);
              } else {
                // otherwise, currentInstanceData was set a while ago and
                // needs to be replaced with this new InstanceData value
                currentInstanceData = SettableFuture.create();
                currentInstanceData.set(data);
              }

              // Schedule another refresh before this InstanceData expires
              nextInstanceData =
                  executor.schedule(this::performRefresh, secondsToRefresh, TimeUnit.SECONDS);
            }

          } catch (Exception e) {
            logger.log(
                Level.FINE,
                String.format(
                    "[%s] Refresh Operation: Failed! Starting next refresh operation immediately.",
                    instanceName),
                e);
            // When the attempt fails, save the last attempt exception and
            // schedule another refresh right away.
            synchronized (instanceDataGuard) {
              lastFailedAttempt = e;
              nextInstanceData = executor.submit(this::performRefresh);
            }
          }
        },
        executor);
  }

  SslData getSslData() throws IOException {
    return getInstanceData().getSslData();
  }

  ListenableFuture<?> getNext() {
    synchronized (instanceDataGuard) {
      return this.nextInstanceData;
    }
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }
}
