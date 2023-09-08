/*
 * Copyright 2023 Google LLC
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

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.Test;

public class CloudSqlInstanceConcurrencyTest {

  public static final int DEFAULT_WAIT = 200;
  private static final Logger logger =
      Logger.getLogger(CloudSqlInstanceConcurrencyTest.class.getName());
  public static final int FORCE_REFRESH_COUNT = 10;

  private static class TestCredentialFactory implements CredentialFactory, HttpRequestInitializer {

    @Override
    public HttpRequestInitializer create() {
      return this;
    }

    @Override
    public void initialize(HttpRequest var1) throws IOException {
      // do nothing
    }
  }

  @Test(timeout = 20000) // 45 seconds timeout in case of deadlock
  public void testForceRefreshDoesNotCauseADeadlockOrBrokenRefreshLoop() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = CoreSocketFactory.getDefaultExecutor();
    TestDataSupplier supplier = new TestDataSupplier(false);
    List<CloudSqlInstance> instances = new ArrayList<>();

    final int instanceCount = 5;

    for (int i = 0; i < instanceCount; i++) {
      instances.add(
          new CloudSqlInstance(
              "a:b:instance" + i,
              supplier,
              AuthType.PASSWORD,
              new TestCredentialFactory(),
              executor,
              keyPairFuture,
              50L));
    }

    // Get SSL Data for each instance, forcing the first refresh to complete.
    instances.forEach((inst) -> inst.getSslData(2000L));

    assertThat(supplier.counter.get()).isEqualTo(instanceCount);

    // Now that everything is initialized, make the network flaky
    supplier.flaky = true;

    // Start a thread for each instance that will force refresh and get InstanceData
    // 50 times.
    List<Thread> threads =
        instances.stream().map(this::startForceRefreshThread).collect(Collectors.toList());

    for (Thread t : threads) {
      // If threads don't complete in 10 seconds, throw an exception,
      // that means there is a deadlock.
      t.join(10000);
    }

    // Check if there is a scheduled future
    int brokenLoop = 0;
    for (CloudSqlInstance inst : instances) {
      if (inst.getCurrent().isDone() && inst.getNext().isDone()) {
        logger.warning("No future scheduled thing for instance " + inst.getInstanceName());
        brokenLoop++;
      }
    }
    assertThat(brokenLoop).isEqualTo(0);
  }

  private Thread startForceRefreshThread(CloudSqlInstance inst) {
    Runnable forceRefreshRepeat =
        () -> {
          for (int i = 0; i < 10; i++) {
            try {
              Thread.sleep(100);
              inst.forceRefresh();
              inst.forceRefresh();
              inst.forceRefresh();
              Thread.sleep(0);
              inst.getSslData(2000L);
            } catch (Exception e) {
              logger.info("Exception in force refresh loop.");
            }
          }
          logger.info("Done spamming");
        };

    Thread t = new Thread(forceRefreshRepeat);
    t.setName("test-" + inst.getInstanceName());
    t.start();
    return t;
  }
}
