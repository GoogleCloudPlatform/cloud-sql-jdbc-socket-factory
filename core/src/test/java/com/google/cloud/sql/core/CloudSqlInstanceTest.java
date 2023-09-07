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
import static org.junit.Assert.fail;

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
  public void testInstanceFailsOnTooLongToRetrieve() throws Exception {

    InstanceDataSupplier instanceDataSupplier =
        (CloudSqlInstanceName instanceName,
            AccessTokenSupplier accessTokenSupplier,
            AuthType authType,
            ListeningScheduledExecutorService exec,
            ListenableFuture<KeyPair> keyPair) -> {
          return exec.submit(
              () -> {
                try {
                  // stop for 2 minutes, which is longer than the timeout
                  // to wait for getInstanceData()
                  Thread.sleep(120 * 1000);
                } catch (InterruptedException e) {
                }
                throw new RuntimeException("fake read timeout");
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

    CloudSqlInstance instance =
        new CloudSqlInstance(
            "project:region:instance",
            (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
              Thread.sleep(100);
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

    instance.forceRefresh();

    instance.getSslData();
    // refresh count hasn't changed because we re-use the existing connection info
    assertThat(refreshCount.get()).isEqualTo(1);

    for (int i = 0; i < 10; i++) {
      instance.getSslData();
      if (refreshCount.get() > 1) {
        return;
      }
      Thread.sleep(100);
    }

    fail(String.format("refresh count should be 2, got = %d", refreshCount.get()));
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
          Thread.sleep(100);
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
          Thread.sleep(100);
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
