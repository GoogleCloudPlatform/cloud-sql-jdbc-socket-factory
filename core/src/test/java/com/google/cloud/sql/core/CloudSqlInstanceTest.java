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
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.RateLimiter;
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

  public static final long TEST_TIMEOUT_MS = 1000;

  @SuppressWarnings("UnstableApiUsage")
  public static final RateLimiter TEST_RATE_LIMITER =
      RateLimiter.create(1000 /* permits per second */);

  private final StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);
  private ListeningScheduledExecutorService executorService;
  private ListenableFuture<KeyPair> keyPairFuture;

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
  public void testCloudSqlInstanceDataRetrievedSuccessfully() throws Exception {
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
            TEST_RATE_LIMITER);

    SslData gotSslData = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(gotSslData).isSameInstanceAs(instanceDataSupplier.response.getSslData());
    assertThat(instanceDataSupplier.counter.get()).isEqualTo(1);
  }

  @Test
  public void testInstanceFailsOnConnectionError() throws Exception {

    InstanceDataSupplier instanceDataSupplier =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) -> {
          throw new RuntimeException("always fails");
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
            TEST_RATE_LIMITER);

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
            RateLimiter.create(10));

    RuntimeException ex =
        Assert.assertThrows(RuntimeException.class, () -> instance.getSslData(2000));
    assertThat(ex).hasMessageThat().contains("java.util.concurrent.TimeoutException");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    SslData sslData = new SslData(null, null, null);
    InstanceData data = new InstanceData(null, sslData, Instant.now().plus(1, ChronoUnit.HOURS));
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
              return data;
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

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
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    InstanceData initialData =
        new InstanceData(
            null, new SslData(null, null, null), Instant.now().plus(2, ChronoUnit.SECONDS));

    InstanceData data =
        new InstanceData(
            null, new SslData(null, null, null), Instant.now().plus(1, ChronoUnit.HOURS));

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
              return refreshResult;
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

    // Get the first data that is about to expire
    SslData d = instance.getSslData(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData.getSslData());

    // Wait for the instance to expire
    while (Instant.now().isBefore(initialData.getExpiration())) {
      Thread.sleep(10);
    }

    // Now that the InstanceData has expired, getInstanceData will return the same, expired
    // token until a new one is retrieved.
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(instance.getSslData(TEST_TIMEOUT_MS)).isSameInstanceAs(initialData.getSslData());

    // Allow the second refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(1000L);

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(
        () -> instance.getSslData(TEST_TIMEOUT_MS) == data.getSslData(), 1000L);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    InstanceData initialData =
        new InstanceData(
            null, new SslData(null, null, null), Instant.now().plus(1, ChronoUnit.HOURS));

    InstanceData data =
        new InstanceData(
            null, new SslData(null, null, null), Instant.now().plus(1, ChronoUnit.HOURS));

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
                  return initialData;
                case 1:
                  refresh1.pause();
                  refreshCount.incrementAndGet();
                  return data;
                default:
                  return data;
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

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
  public void testGetPreferredIpTypes() throws Exception {
    SslData sslData = new SslData(null, null, null);
    InstanceData data =
        new InstanceData(
            new Metadata(
                ImmutableMap.of(
                    "PUBLIC", "10.1.2.3",
                    "PRIVATE", "10.10.10.10",
                    "PSC", "abcde.12345.us-central1.sql.goog"),
                null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    InstanceDataSupplier instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return data;
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
            TEST_RATE_LIMITER);

    assertThat(instance.getPreferredIp(Arrays.asList("PUBLIC", "PRIVATE"), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(instance.getPreferredIp(Collections.singletonList("PUBLIC"), TEST_TIMEOUT_MS))
        .isEqualTo("10.1.2.3");
    assertThat(instance.getPreferredIp(Arrays.asList("PRIVATE", "PUBLIC"), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList("PRIVATE"), TEST_TIMEOUT_MS))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList("PSC"), TEST_TIMEOUT_MS))
        .isEqualTo("abcde.12345.us-central1.sql.goog");
  }

  @Test
  public void testGetPreferredIpTypesThrowsException() throws Exception {
    SslData sslData = new SslData(null, null, null);
    InstanceData data =
        new InstanceData(
            new Metadata(ImmutableMap.of("PUBLIC", "10.1.2.3"), null),
            sslData,
            Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();

    InstanceDataSupplier instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          refreshCount.incrementAndGet();
          return data;
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
            TEST_RATE_LIMITER);
    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> instance.getPreferredIp(Collections.singletonList("PRIVATE"), TEST_TIMEOUT_MS));
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
