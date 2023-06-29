/*
 * Copyright 2022 Google LLC. All Rights Reserved.
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
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.KeyPair;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {

  private InstanceDataSupplier instanceDataSupplier;
  private InstanceData data;
  private SslData sslData;

  private int refreshCount;
  private ListeningScheduledExecutorService executorService;
  private ListenableFuture<KeyPair> keyPairFuture;

  private StubCredentialFactory stubCredentialFactory =
      new StubCredentialFactory("my-token", System.currentTimeMillis() + 3600L);

  private CloudSqlInstance instance;

  @Before
  public void setup() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    this.keyPairFuture = Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    executorService = newTestExecutor();
    refreshCount = 0;
    sslData = new SslData(null, null, null);
    data = new InstanceData(null, sslData, null);
  }

  @After
  public void teardown() {
    executorService.shutdownNow();
  }

  @Test
  public void testCloudSqlInstanceDataRetrievedSuccessfully() throws Exception {
    instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          Thread.sleep(100);
          refreshCount++;
          this.data =
              new InstanceData(null, sslData, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
          return data;
        };
    // initialize instance after mocks are set up
    instance = newCloudSqlInstance();

    SslData gotSslData = instance.getSslData();
    Truth.assertThat(gotSslData).isSameInstanceAs(this.sslData);
    Truth.assertThat(refreshCount).isEqualTo(1);
  }

  @Test
  public void testInstanceFailsOnConnectionError() throws Exception {
    instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          throw new ExecutionException(new IOException("Fake connection error"));
        };

    // initialize instance after mocks are set up
    instance = newCloudSqlInstance();

    RuntimeException ex = Assert.assertThrows(RuntimeException.class, instance::getSslData);
    Truth.assertThat(ex).hasMessageThat().contains("Fake connection error");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          Thread.sleep(100);
          refreshCount++;
          this.data =
              new InstanceData(null, sslData, Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
          return data;
        };

    instance = newCloudSqlInstance();

    SslData gotSslData = instance.getSslData();
    Truth.assertThat(gotSslData).isSameInstanceAs(this.sslData);
    instance.forceRefresh();
    instance.getSslData();
    Truth.assertThat(refreshCount).isEqualTo(2);
  }

  @Test
  public void testGetPreferredIpTypes() throws Exception {
    instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          Thread.sleep(100);
          refreshCount++;
          this.data =
              new InstanceData(
                  new Metadata(
                      ImmutableMap.of(
                          "PUBLIC", "10.1.2.3",
                          "PRIVATE", "10.10.10.10"),
                      null),
                  sslData,
                  Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
          return data;
        };

    // initialize instance after mocks are set up
    instance = newCloudSqlInstance();
    Truth.assertThat(instance.getPreferredIp(Arrays.asList("PUBLIC", "PRIVATE")))
        .isEqualTo("10.1.2.3");
    Truth.assertThat(instance.getPreferredIp(Arrays.asList("PUBLIC"))).isEqualTo("10.1.2.3");
    Truth.assertThat(instance.getPreferredIp(Arrays.asList("PRIVATE", "PUBLIC")))
        .isEqualTo("10.10.10.10");
    Truth.assertThat(instance.getPreferredIp(Arrays.asList("PRIVATE"))).isEqualTo("10.10.10.10");
  }

  @Test
  public void testGetPreferredIpTypesThrowsException() throws Exception {
    instanceDataSupplier =
        (instanceName, accessTokenSupplier, authType, executor, keyPair) -> {
          Thread.sleep(100);
          refreshCount++;
          this.data =
              new InstanceData(
                  new Metadata(ImmutableMap.of("PUBLIC", "10.1.2.3"), null),
                  sslData,
                  Date.from(Instant.now().plus(1, ChronoUnit.HOURS)));
          return data;
        };

    // initialize instance after mocks are set up
    instance = newCloudSqlInstance();
    Assert.assertThrows(
        IllegalArgumentException.class, () -> instance.getPreferredIp(Arrays.asList("PRIVATE")));
  }

  private CloudSqlInstance newCloudSqlInstance() {
    return new CloudSqlInstance(
        "project:region:instance",
        instanceDataSupplier,
        AuthType.PASSWORD,
        stubCredentialFactory,
        executorService,
        keyPairFuture);
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }
}
