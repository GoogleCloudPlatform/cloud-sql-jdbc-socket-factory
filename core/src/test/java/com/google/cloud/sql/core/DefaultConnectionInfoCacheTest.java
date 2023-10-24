/*
 * Copyright 2022 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.KeyPair;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DefaultConnectionInfoCacheTest {

  public static final long TEST_TIMEOUT_MS = 3000;

  public static final long MIN_REFERSH_DELAY_MS = 1;

  private final StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);
  private ListeningScheduledExecutorService executorService;
  private ListenableFuture<KeyPair> keyPairFuture;

  private final long RATE_LIMIT_BETWEEN_REQUESTS = 10L;

  @Before
  public void setup() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    this.keyPairFuture = Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    executorService = newTestExecutor();
  }

  @After
  public void teardown() {
    executorService.shutdownNow();
  }

  @Test
  public void testCloudSqlInstanceDataRetrievedSuccessfully() {
    TestDataSupplier instanceDataSupplier = new TestDataSupplier(false);
    // initialize connectionInfoCache after mocks are set up
    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    SslData gotSslData = connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(gotSslData).isSameInstanceAs(instanceDataSupplier.response.getSslData());
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);
  }

  @Test
  public void testInstanceFailsOnConnectionError() {

    ConnectionInfoRepository connectionInfoRepository =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) ->
            exec.submit(
                () -> {
                  throw new RuntimeException("always fails");
                });

    // initialize connectionInfoCache after mocks are set up
    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            connectionInfoRepository,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    RuntimeException ex =
        Assert.assertThrows(
            RuntimeException.class, () -> connectionInfoCache.getSslData(TEST_TIMEOUT_MS));
    assertThat(ex).hasMessageThat().contains("always fails");
  }

  @Test
  public void testInstanceFailsOnTooLongToRetrieve() {
    PauseCondition cond = new PauseCondition();
    ConnectionInfoRepository connectionInfoRepository =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) -> {
          // This is never allowed to proceed
          cond.pause();
          throw new RuntimeException("fake read timeout");
        };

    // initialize connectionInfoCache after mocks are set up
    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            connectionInfoRepository,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            100);

    RuntimeException ex =
        Assert.assertThrows(RuntimeException.class, () -> connectionInfoCache.getSslData(2000));
    assertThat(ex).hasMessageThat().contains("No refresh has completed");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    ConnectionInfo connectionInfo = newFutureConnectionInfo();
    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              // Allow the first execution to complete immediately.
              // The second execution should pause until signaled.
              if (c == 1) {
                cond.pause();
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(connectionInfo);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    connectionInfoCache.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000L);
    cond.waitForCondition(() -> refreshCount.get() >= 2, 1000L);

    // getSslData again, and assert the refresh operation completed.
    connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlInstanceRetriesOnInitialFailures() throws Exception {
    ConnectionInfo connectionInfo = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              refreshCount.incrementAndGet();
              if (c == 0) {
                throw new RuntimeException("bad request 0");
              }
              return Futures.immediateFuture(connectionInfo);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first connectionInfo that is about to expire
    long until = System.currentTimeMillis() + 3000;
    while (connectionInfoCache.getSslData(TEST_TIMEOUT_MS) != connectionInfo.getSslData()
        && System.currentTimeMillis() < until) {
      Thread.sleep(100);
    }
    assertThat(refreshCount.get()).isEqualTo(2);
    assertThat(connectionInfoCache.getSslData(TEST_TIMEOUT_MS))
        .isEqualTo(connectionInfo.getSslData());
  }

  private static ConnectionInfo newFutureConnectionInfo() {
    return newConnectionInfo(1, ChronoUnit.HOURS);
  }

  private static ConnectionInfo newConnectionInfo(long amount, ChronoUnit unit) {
    return new ConnectionInfo(
        null, new SslData(null, null, null), Instant.now().plus(amount, unit));
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    ConnectionInfo initial = newConnectionInfo(2, ChronoUnit.SECONDS);
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              ConnectionInfo refreshResult = info;
              switch (c) {
                case 0:
                  // refresh 0 should return initial immediately
                  refreshResult = initial;
                  break;
                case 1:
                  // refresh 1 should pause
                  refresh1.pause();
                  break;
              }
              // refresh 2 and on should return data immediately
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(refreshResult);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first data that is about to expire
    SslData d = connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initial.getSslData());

    // Wait for the connectionInfoCache to expire
    while (Instant.now().isBefore(initial.getExpiration())) {
      Thread.sleep(10);
    }

    // Allow the second refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(1000L);

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () -> connectionInfoCache.getSslData(TEST_TIMEOUT_MS) == info.getSslData(), 1000L);
  }

  @Test
  public void testThatForceRefreshBalksWhenAScheduledRefreshIsInProgress() throws Exception {
    // Set expiration 1 minute in the future, so that it will trigger a scheduled refresh
    // and won't expire during this testcase.
    ConnectionInfo expiresInOneMinute = newConnectionInfo(1, ChronoUnit.MINUTES);

    // Set the next refresh info expiration 1 hour in the future.
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();
    final PauseCondition refresh1 = new PauseCondition();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              ConnectionInfo refreshResult = info;
              switch (c) {
                case 0:
                  refresh0.pause();
                  refreshResult = expiresInOneMinute;
                  break;
                case 1:
                  refresh1.pause();
                  break;
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(refreshResult);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    refresh0.proceed();
    refresh0.waitForPauseToEnd(1000);
    refresh0.waitForCondition(() -> refreshCount.get() > 0, 1000);
    // Get the first info that is about to expire
    assertThat(refreshCount.get()).isEqualTo(1);
    SslData d = connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(d).isSameInstanceAs(expiresInOneMinute.getSslData());

    // Because the info is about to expire, scheduled refresh will begin immediately.
    // Wait until refresh is in progress.
    refresh1.waitForPauseToStart(1000);

    // Then call forceRefresh(), which should balk because a refresh attempt is in progress.
    connectionInfoCache.forceRefresh();

    // Finally, allow the scheduled refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(5000);
    refresh1.waitForCondition(() -> refreshCount.get() > 1, 1000);

    // Now that the InstanceData has expired, this getSslData should pause until new info
    // has been retrieved.

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () -> connectionInfoCache.getSslData(TEST_TIMEOUT_MS) == info.getSslData(), 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    ConnectionInfo initialData = newFutureConnectionInfo();
    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              switch (c) {
                case 0:
                  refreshCount.incrementAndGet();
                  return Futures.immediateFuture(initialData);
                case 1:
                  refresh1.pause();
                  refreshCount.incrementAndGet();
                  return Futures.immediateFuture(info);
                default:
                  return Futures.immediateFuture(info);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first info that is about to expire
    SslData d = connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData.getSslData());

    // call forceRefresh twice, this should only result in 1 refresh fetch
    connectionInfoCache.forceRefresh();
    connectionInfoCache.forceRefresh();

    // Allow the refresh operation to complete
    refresh1.proceed();

    // Now that the InstanceData has expired, this getSslData should pause until new info
    // has been retrieved.
    refresh1.waitForPauseToEnd(1000);
    refresh1.waitForCondition(() -> refreshCount.get() >= 2, 1000);

    // assert the refresh operation completed exactly once after
    // forceRefresh was called multiple times.
    refresh1.waitForCondition(
        () -> connectionInfoCache.getSslData(TEST_TIMEOUT_MS) == info.getSslData(), 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testRefreshRetriesOnAfterFailedAttempts() throws Exception {
    ConnectionInfo aboutToExpire = newConnectionInfo(10, ChronoUnit.MILLIS);

    ConnectionInfo info = newFutureConnectionInfo();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition badRequest1 = new PauseCondition();
    final PauseCondition badRequest2 = new PauseCondition();
    final PauseCondition goodRequest = new PauseCondition();

    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              switch (c) {
                case 0:
                  refreshCount.incrementAndGet();
                  return Futures.immediateFuture(aboutToExpire);
                case 1:
                  badRequest1.pause();
                  refreshCount.incrementAndGet();
                  throw new RuntimeException("bad request 1");
                case 2:
                  badRequest2.pause();
                  refreshCount.incrementAndGet();
                  throw new RuntimeException("bad request 2");
                default:
                  goodRequest.pause();
                  refreshCount.incrementAndGet();
                  return Futures.immediateFuture(info);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first info that is about to expire
    SslData d = connectionInfoCache.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpire.getSslData());

    // Don't force a refresh, this should automatically schedule a refresh right away because
    // the token returned in the first request had less than 4 minutes before it expired.

    // Wait for the current InstanceData to actually expire.
    while (Instant.now().isBefore(aboutToExpire.getExpiration())) {
      Thread.sleep(10);
    }

    // Orchestrate the failed attempts

    // Allow the second refresh operation to complete
    badRequest1.proceed();
    badRequest1.waitForPauseToEnd(5000);
    badRequest1.waitForCondition(() -> refreshCount.get() == 2, 2000);

    // Allow the second bad request completes
    badRequest2.proceed();
    badRequest2.waitForCondition(() -> refreshCount.get() == 3, 2000);

    // Allow the final good request to complete
    goodRequest.proceed();
    goodRequest.waitForCondition(() -> refreshCount.get() == 4, 2000);

    // Try getSslData() again, and assert the refresh operation eventually completes.
    goodRequest.waitForCondition(
        () -> connectionInfoCache.getSslData(TEST_TIMEOUT_MS) == info.getSslData(), 2000);
  }

  @Test
  public void testGetPreferredIpTypes() {
    SslData sslData = new SslData(null, null, null);
    ConnectionInfo info =
        new ConnectionInfo(
            new Metadata(
                ImmutableMap.of(
                    IpType.PUBLIC, "10.1.2.3",
                    IpType.PRIVATE, "10.10.10.10",
                    IpType.PSC, "abcde.12345.us-central1.sql.goog"),
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    ConnectionInfoRepository connectionInfoRepository =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return Futures.immediateFuture(info);
        };

    // initialize connectionInfoCache after mocks are set up
    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            connectionInfoRepository,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    assertThat(
            connectionInfoCache.getPreferredIp(
                Arrays.asList(IpType.PUBLIC, IpType.PRIVATE), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(
            connectionInfoCache.getPreferredIp(
                Collections.singletonList(IpType.PUBLIC), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(
            connectionInfoCache.getPreferredIp(
                Arrays.asList(IpType.PRIVATE, IpType.PUBLIC), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(
            connectionInfoCache.getPreferredIp(
                Collections.singletonList(IpType.PRIVATE), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(
            connectionInfoCache.getPreferredIp(
                Collections.singletonList(IpType.PSC), TEST_TIMEOUT_MS))
        .isEqualTo("abcde.12345.us-central1.sql.goog");
  }

  @Test
  public void testGetPreferredIpTypesThrowsException() {
    SslData sslData = new SslData(null, null, null);
    ConnectionInfo info =
        new ConnectionInfo(
            new Metadata(ImmutableMap.of(IpType.PUBLIC, "10.1.2.3"), null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    ConnectionInfoRepository connectionInfoRepository =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return Futures.immediateFuture(info);
        };

    // initialize connectionInfoCache after mocks are set up
    DefaultConnectionInfoCache connectionInfoCache =
        new DefaultConnectionInfoCache(
            "project:region:instance",
            connectionInfoRepository,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);
    Assert.assertThrows(
        IllegalArgumentException.class,
        () ->
            connectionInfoCache.getPreferredIp(
                Collections.singletonList(IpType.PRIVATE), TEST_TIMEOUT_MS));
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }
}
