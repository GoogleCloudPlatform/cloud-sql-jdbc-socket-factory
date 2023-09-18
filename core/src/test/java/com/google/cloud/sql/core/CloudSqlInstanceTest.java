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
import static org.junit.Assert.assertThrows;

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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class CloudSqlInstanceTest {

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

    SslData gotSslData = instance.getSslData();
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
          return exec.submit(
              () -> {
                throw new RuntimeException("always fails");
              });
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

    RuntimeException ex = Assert.assertThrows(RuntimeException.class, instance::getSslData);
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
            ListenableFuture<KeyPair> keyPair) ->
            exec.submit(
                () -> {
                  // This is never allowed to proceed
                  cond.pause();
                  throw new RuntimeException("fake read timeout");
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
            RateLimiter.create(10));

    RuntimeException ex = Assert.assertThrows(RuntimeException.class, instance::getSslData);
    assertThat(ex).hasMessageThat().contains("No refresh has completed");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    SslData sslData = new SslData(null, null, null);
    InstanceData data =
        new InstanceData(null, sslData, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              // Allow the first execution to complete immediately.
              // Subsequent executions should wait until the PauseCondition
              // is signaled by the main testcase thread.
              if (c > 0) {
                cond.pause();
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(data);
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

    instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    instance.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    cond.proceedWhen(() -> refreshCount.get() >= 2);

    // getSslData again, and assert the refresh operation completed.
    instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlInstanceRetriesOnInitialFailures() throws Exception {
    InstanceData data =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              System.out.println("Attempt " + c);
              refreshCount.incrementAndGet();
              switch (c) {
                case 0:
                  cond.pause();
                  throw new RuntimeException("bad request 0");
                case 1:
                  throw new RuntimeException("bad request 1");
                default:
                  return Futures.immediateFuture(data);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

    cond.proceed();

    // Get the first data that is about to expire
    SslData d = instance.getSslData(4000);
    assertThat(refreshCount.get()).isEqualTo(3);
    assertThat(d).isSameInstanceAs(data.getSslData());
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    InstanceData aboutToExpireData =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(10, ChronoUnit.MILLIS)));

    InstanceData data =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              int c = refreshCount.get();
              if (c > 0) {
                // Allow the first execution to complete immediately.
                // Subsequent executions should wait until the PauseCondition
                // is signaled by the main testcase thread.
                cond.pause();
              }
              refreshCount.incrementAndGet();
              switch (c) {
                case 0:
                  return Futures.immediateFuture(aboutToExpireData);
                default:
                  return Futures.immediateFuture(data);
              }
            },
            AuthType.PASSWORD,
            stubCredentialFactory,
            executorService,
            keyPairFuture,
            TEST_RATE_LIMITER);

    // Get the first data that is about to expire
    SslData d = instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // Wait for the instance to expire
    while (System.currentTimeMillis() <= aboutToExpireData.getExpiration().getTime()) {
      Thread.sleep(10);
    }

    // Allow the second refresh operation to complete
    cond.proceedWhen(() -> refreshCount.get() == 1);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved. In this case, the new data has not yet been retrieved, so the operation
    // should time out after 100ms.
    assertThrows(RuntimeException.class, () -> instance.getSslData(100));
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // Allow the second refresh operation to complete
    cond.proceedWhen(() -> refreshCount.get() > 1);

    // getSslData again, and assert the refresh operation completed.
    SslData d2 = instance.getSslData(1000);
    assertThat(d2).isSameInstanceAs(data.getSslData());
  }

  @Test
  public void testThatForceRefreshBalksWhenAScheduledRefreshIsInProgress() throws Exception {
    InstanceData aboutToExpireData =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(10, ChronoUnit.MILLIS)));

    InstanceData data =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

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
                  cond.pause();
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
            TEST_RATE_LIMITER);

    // Get the first data that is about to expire
    SslData d = instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // call forceRefresh twice, this should only result in 1 refresh fetch
    instance.forceRefresh();
    instance.forceRefresh();

    // Allow the refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved.

    // getSslData again, and assert the refresh operation completed.
    SslData d2 = instance.getSslData(5000);
    assertThat(d2).isSameInstanceAs(data.getSslData());
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    InstanceData aboutToExpireData =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    InstanceData data =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();

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
                  cond.pause();
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
            TEST_RATE_LIMITER);

    // Get the first data that is about to expire
    SslData d = instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // call forceRefresh twice, this should only result in 1 refresh fetch
    instance.forceRefresh();
    instance.forceRefresh();

    // Allow the refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved.
    while (refreshCount.get() < 2) {
      Thread.yield();
    }

    // getSslData again, and assert the refresh operation completed exactly once
    SslData d2 = instance.getSslData(1000);
    assertThat(d2).isSameInstanceAs(data.getSslData());
  }

  @Test
  public void testRefreshRetriesOnAfterFailedAttempts() throws Exception {
    InstanceData aboutToExpireData =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(10, ChronoUnit.MILLIS)));

    InstanceData data =
        new InstanceData(
            null,
            new SslData(null, null, null),
            Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));

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
            TEST_RATE_LIMITER);

    // Get the first data that is about to expire
    SslData d = instance.getSslData();
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // Don't force a refresh, this should automatically schedule a refresh right away because
    // the token returned in the first request had less than 4 minutes before it expired.

    // Wait for the current InstanceData to actually expire.
    while (System.currentTimeMillis() <= aboutToExpireData.getExpiration().getTime()) {
      Thread.sleep(10);
    }

    // Start a thread to getSslData(). This will eventually return after the
    // failed attempts.
    AtomicReference<SslData> earlyFetchAttempt = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              earlyFetchAttempt.set(instance.getSslData());
            });
    t.start();

    // Orchestrate the failed attempts

    // Allow the second refresh operation to complete
    badRequest1.proceedWhen(() -> refreshCount.get() == 2);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved. In this case, the new data has not yet been retrieved, so the operation
    // should time out after 10ms.
    assertThrows(RuntimeException.class, () -> instance.getSslData(10));
    assertThat(d).isSameInstanceAs(aboutToExpireData.getSslData());

    // Allow the second bad request completes
    badRequest2.proceedWhen(() -> refreshCount.get() == 3);

    // Allow the third bad request to complete
    goodRequest.proceedWhen(() -> refreshCount.get() == 4);

    // Wait for the thread to exit, meaning getSslData() finally returned
    // after several failed attempts. Assert that the correct InstanceData
    // eventually returned.
    t.join();
    assertThat(earlyFetchAttempt.get()).isSameInstanceAs(data.getSslData());

    // Try getSslData() again, and assert the refresh operation completed.
    SslData d2 = instance.getSslData();
    assertThat(d2).isSameInstanceAs(data.getSslData());
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
            TEST_RATE_LIMITER);

    assertThat(instance.getPreferredIp(Arrays.asList("PUBLIC", "PRIVATE"))).isEqualTo("10.1.2.3");
    assertThat(instance.getPreferredIp(Collections.singletonList("PUBLIC"))).isEqualTo("10.1.2.3");
    assertThat(instance.getPreferredIp(Arrays.asList("PRIVATE", "PUBLIC")))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList("PRIVATE")))
        .isEqualTo("10.10.10.10");
    assertThat(instance.getPreferredIp(Collections.singletonList("PSC")))
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
            TEST_RATE_LIMITER);
    Assert.assertThrows(
        IllegalArgumentException.class,
        () -> instance.getPreferredIp(Collections.singletonList("PRIVATE")));
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }
}
