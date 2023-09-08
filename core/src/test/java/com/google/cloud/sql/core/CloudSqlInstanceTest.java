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

public class CloudSqlInstanceTest {

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
    // initialize instance after mocks are set up
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    SslData gotSslData = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(gotSslData).isSameInstanceAs(instanceDataSupplier.response.getSslData());
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);
  }

  @Test
  public void testInstanceFailsOnConnectionError() {

    InstanceDataSupplier instanceDataSupplier =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) ->
            exec.submit(
                () -> {
                  throw new RuntimeException("always fails");
                });

    // initialize instance after mocks are set up
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    RuntimeException ex =
        Assert.assertThrows(RuntimeException.class, () -> instance.getSslData(TEST_TIMEOUT_MS));
    assertThat(ex).hasMessageThat().contains("always fails");
  }

  @Test
  public void testInstanceFailsOnTooLongToRetrieve() {
    PauseCondition cond = new PauseCondition();
    InstanceDataSupplier instanceDataSupplier =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) -> {
          // This is never allowed to proceed
          cond.pause();
          throw new RuntimeException("fake read timeout");
        };

    // initialize instance after mocks are set up
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            100);

    RuntimeException ex =
        Assert.assertThrows(RuntimeException.class, () -> instance.getSslData(2000));
    assertThat(ex).hasMessageThat().contains("No refresh has completed");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    InstanceData data = newFutureInstanceData();
    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              // Allow the first execution to complete immediately.
              // The second execution should pause until signaled.
              if (c == 1) {
                cond.pause();
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(data);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    instance.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000L);
    cond.waitForCondition(() -> refreshCount.get() >= 2, 1000L);

    // getSslData again, and assert the refresh operation completed.
    instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlInstanceRetriesOnInitialFailures() throws Exception {
    InstanceData data = newFutureInstanceData();

    AtomicInteger refreshCount = new AtomicInteger();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              refreshCount.incrementAndGet();
              if (c == 0) {
                throw new RuntimeException("bad request 0");
              }
              return Futures.immediateFuture(data);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first data that is about to expire
    long until = System.currentTimeMillis() + 3000;
    while (instance.getSslData(TEST_TIMEOUT_MS) != data.getSslData()
        && System.currentTimeMillis() < until) {
      Thread.sleep(100);
    }
    assertThat(refreshCount.get()).isEqualTo(2);
    assertThat(instance.getSslData(TEST_TIMEOUT_MS)).isEqualTo(data.getSslData());
  }

  private static InstanceData newFutureInstanceData() {
    return newInstanceDataExpiringIn(1, ChronoUnit.HOURS);
  }

  private static InstanceData newInstanceDataExpiringIn(long amount, ChronoUnit unit) {
    return new InstanceData(null, new SslData(null, null, null), Instant.now().plus(amount, unit));
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    InstanceData initialData = newInstanceDataExpiringIn(2, ChronoUnit.SECONDS);
    InstanceData data = newFutureInstanceData();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              InstanceData refreshResult = data;
              switch (c) {
                case 0:
                  // refresh 0 should return initialData immediately
                  refreshResult = initialData;
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
    SslData d = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData.getSslData());

    // Wait for the instance to expire
    while (Instant.now().isBefore(initialData.getExpiration())) {
      Thread.sleep(10);
    }

    // Allow the second refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(1000L);

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () -> instance.getSslData(TEST_TIMEOUT_MS) == data.getSslData(), 1000L);
  }

  @Test
  public void testThatForceRefreshBalksWhenAScheduledRefreshIsInProgress() throws Exception {
    // Set expiration 1 minute in the future, so that it will trigger a scheduled refresh
    // and won't expire during this testcase.
    InstanceData expiresInOneMinute = newInstanceDataExpiringIn(1, ChronoUnit.MINUTES);

    // Set the next refresh data expiration 1 hour in the future.
    InstanceData data = newFutureInstanceData();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();
    final PauseCondition refresh1 = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              InstanceData refreshResult = data;
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
    // Get the first data that is about to expire
    assertThat(refreshCount.get()).isEqualTo(1);
    SslData d = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(d).isSameInstanceAs(expiresInOneMinute.getSslData());

    // Because the data is about to expire, scheduled refresh will begin immediately.
    // Wait until refresh is in progress.
    refresh1.waitForPauseToStart(1000);

    // Then call forceRefresh(), which should balk because a refresh attempt is in progress.
    instance.forceRefresh();

    // Finally, allow the scheduled refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(5000);
    refresh1.waitForCondition(() -> refreshCount.get() > 1, 1000);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved.

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () -> instance.getSslData(TEST_TIMEOUT_MS) == data.getSslData(), 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    InstanceData initialData = newFutureInstanceData();
    InstanceData data = newFutureInstanceData();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
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
                  return Futures.immediateFuture(data);
                default:
                  return Futures.immediateFuture(data);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first data that is about to expire
    SslData d = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData.getSslData());

    // call forceRefresh twice, this should only result in 1 refresh fetch
    instance.forceRefresh();
    instance.forceRefresh();

    // Allow the refresh operation to complete
    refresh1.proceed();

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved.
    refresh1.waitForPauseToEnd(1000);
    refresh1.waitForCondition(() -> refreshCount.get() >= 2, 1000);

    // assert the refresh operation completed exactly once after
    // forceRefresh was called multiple times.
    refresh1.waitForCondition(
        () -> instance.getSslData(TEST_TIMEOUT_MS) == data.getSslData(), 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testRefreshRetriesOnAfterFailedAttempts() throws Exception {
    InstanceData aboutToExpireData = newInstanceDataExpiringIn(10, ChronoUnit.MILLIS);

    InstanceData data = newFutureInstanceData();

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition badRequest1 = new PauseCondition();
    final PauseCondition badRequest2 = new PauseCondition();
    final PauseCondition goodRequest = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              switch (c) {
                case 0:
                  refreshCount.incrementAndGet();
                  return Futures.immediateFuture(aboutToExpireData);
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
                  return Futures.immediateFuture(data);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            RATE_LIMIT_BETWEEN_REQUESTS);

    // Get the first data that is about to expire
    SslData d = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // Don't force a refresh, this should automatically schedule a refresh right away because
    // the token returned in the first request had less than 4 minutes before it expired.

    // Wait for the current InstanceData to actually expire.
    while (Instant.now().isBefore(aboutToExpireData.getExpiration())) {
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
        () -> instance.getSslData(TEST_TIMEOUT_MS) == data.getSslData(), 2000);
  }

  @Test
  public void testGetPreferredIpTypes() {
    SslData sslData = new SslData(null, null, null);
    InstanceData data =
        new InstanceData(
            new Metadata(
                ImmutableMap.of(
                    IpType.PUBLIC, "10.1.2.3",
                    IpType.PRIVATE, "10.10.10.10",
                    IpType.PSC, "abcde.12345.us-central1.sql.goog"),
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    InstanceDataSupplier instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return Futures.immediateFuture(data);
        };

    // initialize instance after mocks are set up
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);

    assertThat(
            instance.getPreferredIp(Arrays.asList(IpType.PUBLIC, IpType.PRIVATE), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(instance.getPreferredIp(Collections.singletonList(IpType.PUBLIC), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(
            instance.getPreferredIp(Arrays.asList(IpType.PRIVATE, IpType.PUBLIC), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList(IpType.PRIVATE), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList(IpType.PSC), TEST_TIMEOUT_MS))
        .isEqualTo("abcde.12345.us-central1.sql.goog");
  }

  @Test
  public void testGetPreferredIpTypesThrowsException() {
    SslData sslData = new SslData(null, null, null);
    InstanceData data =
        new InstanceData(
            new Metadata(ImmutableMap.of(IpType.PUBLIC, "10.1.2.3"), null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    InstanceDataSupplier instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return Futures.immediateFuture(data);
        };

    // initialize instance after mocks are set up
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            instanceDataSupplier,
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            MIN_REFERSH_DELAY_MS);
    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> instance.getPreferredIp(Collections.singletonList(IpType.PRIVATE), TEST_TIMEOUT_MS));
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
