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
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.io.IOException;
import java.security.KeyPair;
import java.time.Duration;
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
  // Rate limit delay calculator
  private final RateLimitCalculator rateLimitCalculator =
      new RateLimitCalculator(
          Duration.ofMillis(50), Duration.ofMillis(250), 2.0f, Duration.ofMinutes(2));
  private final RefreshCalculator refreshCalculator = new RefreshCalculator();

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> currentInstanceData;

  @GuardedBy("instanceDataGuard")
  private SettableFuture<InstanceData> initialInstanceData = SettableFuture.create();

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<InstanceData> nextInstanceData;

  @GuardedBy("instanceDataGuard")
  private ListenableFuture<?> nextScheduledRefresh;

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
      ListenableFuture<KeyPair> keyPair) {
    this.instanceName = new CloudSqlInstanceName(connectionName);
    this.instanceDataSupplier = instanceDataSupplier;
    this.authType = authType;
    this.executor = executor;
    this.keyPair = keyPair;

    if (authType == AuthType.IAM) {
      HttpRequestInitializer source = tokenSourceFactory.create();
      this.accessTokenSupplier = new DefaultAccessTokenSupplier(source);
    } else {
      this.accessTokenSupplier = Optional::empty;
    }
    forceRefresh();
  }

  /**
   * Returns the current data related to the instance from {@link #performRefresh()}. May block if
   * no valid data is currently available.
   */
  private InstanceData getInstanceData() {
    ListenableFuture<InstanceData> instanceDataFuture;
    synchronized (instanceDataGuard) {
      if (currentInstanceData == null) {
        instanceDataFuture = initialInstanceData;
      } else {
        instanceDataFuture = currentInstanceData;
      }
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
    // nextInstanceData holds either:
    //   - an in-progress future created like this: `executor.submit(this::performRefresh)`
    //   - a completed future with the result (or exception) of the refresh`
    synchronized (instanceDataGuard) {
      // When no refresh is in progress, start a new one
      if (nextInstanceData == null || nextInstanceData.isDone()) {
        nextInstanceData = executor.submit(this::performRefresh);
        ;
      }

      // nextScheduledRefresh may hold a scheduled refresh in the future. If so, cancel
      // it. nextScheduledRefresh will be set to a new future refresh after the next successful
      // performRefresh() attempt.
      if (this.nextScheduledRefresh != null && !this.nextScheduledRefresh.isDone()) {
        this.nextScheduledRefresh.cancel(false);
      }
    }
  }

  /**
   * Triggers an update of internal information obtained from the Cloud SQL Admin API. Replaces the
   * value of currentInstanceData and schedules the next refresh shortly before the information
   * would expire.
   */
  private InstanceData performRefresh() throws InterruptedException, ExecutionException {
    logger.fine("Refresh Operation: Attempting refresh.");
    rateLimitCalculator.recordAttempt();

    try {
      InstanceData data =
          instanceDataSupplier.getInstanceData(
              this.instanceName, this.accessTokenSupplier, this.authType, executor, keyPair);

      logger.fine(
          String.format(
              "Refresh Operation: Completed refresh with new certificate expiration at %s.",
              data.getExpiration().toInstant().toString()));
      long secondsToRefresh =
          refreshCalculator.calculateSecondsUntilNextRefresh(
              Instant.now(), data.getExpiration().toInstant());

      logger.fine(
          String.format(
              "Refresh Operation: Next operation scheduled at %s.",
              Instant.now()
                  .plus(secondsToRefresh, ChronoUnit.SECONDS)
                  .truncatedTo(ChronoUnit.SECONDS)
                  .toString()));

      synchronized (instanceDataGuard) {
        // If currentInstanceData == null, then this is the first successful attempt,
        // so we must also resolve initialInstanceData to satisfy any callers who requested
        // a certificate before the first successful attempt
        if (currentInstanceData == null) {
          initialInstanceData.set(data);
        }

        // After successfully fetching the data, update currentInstanceData with the new value
        currentInstanceData = Futures.immediateFuture(data);

        // Then schedule a refresh in the future.
        nextScheduledRefresh =
            executor.schedule(this::forceRefresh, secondsToRefresh, TimeUnit.SECONDS);
      }

      rateLimitCalculator.recordSuccess();

      return data;
    } catch (ExecutionException | InterruptedException e) {
      rateLimitCalculator.recordFailure();
      logger.log(
          Level.FINE, "Refresh Operation: Failed! Starting next refresh operation immediately.", e);
      synchronized (instanceDataGuard) {
        // After a failed refresh, schedule a future refresh
        // at after an appropriate delay calculated by the rate limiter.
        Duration delay = rateLimitCalculator.nextAttemptDelayMillis();
        nextScheduledRefresh =
            executor.schedule(this::forceRefresh, delay.toMillis(), TimeUnit.MILLISECONDS);
      }
      throw e;
    }
  }

  SslData getSslData() {
    return getInstanceData().getSslData();
  }
}
