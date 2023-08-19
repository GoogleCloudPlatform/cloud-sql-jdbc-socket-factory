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

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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

  private final ListeningScheduledExecutorService executor;
  private final InstanceDataSupplier instanceDataSupplier;
  private final AuthType authType;
  private final AccessTokenSupplier accessTokenSupplier;
  private final CloudSqlInstanceName instanceName;
  private final ListenableFuture<KeyPair> keyPair;
  private final Object instanceDataGuard = new Object();
  @SuppressWarnings("UnstableApiUsage")
  private final RateLimiter forcedRenewRateLimiter;

  private final RefreshCalculator refreshCalculator = new RefreshCalculator();

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> currentInstanceData;

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> nextInstanceData;

  @GuardedBy("instanceDataGuard")
  private boolean forceRefreshRunning;

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
      @SuppressWarnings("UnstableApiUsage") RateLimiter forcedRenewRateLimiter) {
    this.instanceName = new CloudSqlInstanceName(connectionName);
    this.instanceDataSupplier = instanceDataSupplier;
    this.authType = authType;
    this.executor = executor;
    this.keyPair = keyPair;
    this.forcedRenewRateLimiter = forcedRenewRateLimiter;

    if (authType == AuthType.IAM) {
      this.accessTokenSupplier = new DefaultAccessTokenSupplier(tokenSourceFactory);
    } else {
      this.accessTokenSupplier = Optional::empty;
    }

    synchronized (instanceDataGuard) {
      this.currentInstanceData = executor.submit(this::performRefresh);
      this.nextInstanceData = currentInstanceData;
    }
  }

  /**
   * Returns the current data related to the instance from {@link #performRefresh()}. May block if
   * no valid data is currently available.
   */
  private InstanceData getInstanceData() {
    ListenableFuture<InstanceData> instanceDataFuture;
    synchronized (instanceDataGuard) {
      instanceDataFuture = currentInstanceData;
    }
    try {
      return Uninterruptibles.getUninterruptibly(instanceDataFuture);
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
      // Don't force a refresh until the current forceRefresh operation
      // has produced a successful refresh.
      if (forceRefreshRunning) {
        return;
      }

      forceRefreshRunning = true;
      nextInstanceData.cancel(false);
      logger.fine(
          String.format(
              "[%s] Force Refresh: the next refresh operation was cancelled."
                  + " Scheduling new refresh operation immediately.",
              instanceName));
      nextInstanceData = executor.submit(this::performRefresh);
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API. Replaces the
   * value of currentInstanceData and schedules the next refresh shortly before the information
   * would expire.
   */
  private InstanceData performRefresh() throws InterruptedException, ExecutionException {
    logger.fine(
        String.format("[%s] Refresh Operation: Acquiring rate limiter permit.", instanceName));
    // To avoid unreasonable SQL Admin API usage, use a rate limit to throttle our usage.
    //noinspection UnstableApiUsage
    forcedRenewRateLimiter.acquire();
    logger.fine(
        String.format(
            "[%s] Refresh Operation: Acquired rate limiter permit. Starting refresh...",
            instanceName));

    try {
      InstanceData data =
          instanceDataSupplier.getInstanceData(
              this.instanceName, this.accessTokenSupplier, this.authType, executor, keyPair);

      logger.fine(
          String.format(
              "[%s] Refresh Operation: Completed refresh with new certificate expiration at %s.",
              instanceName, data.getExpiration().toInstant().toString()));
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

      synchronized (instanceDataGuard) {
        currentInstanceData = Futures.immediateFuture(data);
        nextInstanceData =
            executor.schedule(this::performRefresh, secondsToRefresh, TimeUnit.SECONDS);
        // Refresh completed successfully, reset forceRefreshRunning.
        forceRefreshRunning = false;
      }
      return data;
    } catch (ExecutionException | InterruptedException e) {
      logger.log(
          Level.FINE,
          String.format(
              "[%s] Refresh Operation: Failed! Starting next refresh operation immediately.",
              instanceName),
          e);
      synchronized (instanceDataGuard) {
        nextInstanceData = executor.submit(this::performRefresh);
      }
      throw e;
    }
  }

  SslData getSslData() {
    return getInstanceData().getSslData();
  }

  ListenableFuture<InstanceData> getNext() {
    synchronized (instanceDataGuard) {
      return this.nextInstanceData;
    }
  }

  ListenableFuture<InstanceData> getCurrent() {
    synchronized (instanceDataGuard) {
      return this.currentInstanceData;
    }
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }
}
