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
import com.google.cloud.sql.ConnectionConfig;
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

public class DefaultConnectionInfoCacheConcurrencyTest {

  public static final int DEFAULT_WAIT = 200;
  private static final Logger logger =
      Logger.getLogger(DefaultConnectionInfoCacheConcurrencyTest.class.getName());
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
    final long refreshDelayMs = 50;
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = InternalConnectorRegistry.getDefaultExecutor();
    TestDataSupplier supplier = new TestDataSupplier(false);
    List<DefaultConnectionInfoCache> caches = new ArrayList<>();

    final int instanceCount = 5;

    for (int i = 0; i < instanceCount; i++) {
      caches.add(
          new DefaultConnectionInfoCache(
              new ConnectionConfig.Builder().withCloudSqlInstance("a:b:instance" + i).build(),
              supplier,
              new TestCredentialFactory(),
              executor,
              keyPairFuture,
              refreshDelayMs));
    }

    // Get SSL Data for each instance, forcing the first refresh to complete.
    caches.forEach((inst) -> inst.getConnectionMetadata(2000L));

    assertThat(supplier.counter.get()).isEqualTo(instanceCount);

    // Now that everything is initialized, make the network flaky
    supplier.flaky = true;

    // Start a thread for each instance that will force refresh and get InstanceData
    // 50 times.
    List<Thread> threads =
        caches.stream().map(this::startForceRefreshThread).collect(Collectors.toList());

    for (Thread t : threads) {
      // If threads don't complete in 10 seconds, throw an exception,
      // that means there is a deadlock.
      t.join(10000);
    }

    // Check if there is a scheduled future
    int brokenLoop = 0;
    for (DefaultConnectionInfoCache i : caches) {
      if (i.getCurrent().isDone() && i.getNext().isDone()) {
        logger.warning("No future scheduled thing for instance " + i.getInstanceName());
        brokenLoop++;
      }
    }
    assertThat(brokenLoop).isEqualTo(0);
  }

  private Thread startForceRefreshThread(DefaultConnectionInfoCache connectionInfoCache) {
    Runnable forceRefreshRepeat =
        () -> {
          for (int i = 0; i < 10; i++) {
            try {
              Thread.sleep(100);
              connectionInfoCache.forceRefresh();
              connectionInfoCache.forceRefresh();
              connectionInfoCache.forceRefresh();
              Thread.sleep(0);
              connectionInfoCache.getConnectionMetadata(2000L);
            } catch (Exception e) {
              logger.info("Exception in force refresh loop.");
            }
          }
          logger.info("Done spamming");
        };

    Thread t = new Thread(forceRefreshRepeat);
    t.setName("test-" + connectionInfoCache.getInstanceName());
    t.start();
    return t;
  }
}
