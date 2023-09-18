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
import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
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

  @Test(timeout = 20000)
  public void testThatForceRefreshBalksWhenARefreshIsInProgress() throws Exception {
    MockAdminApi mockAdminApi = new MockAdminApi();
    ListenableFuture<KeyPair> keyPairFuture =
        Futures.immediateFuture(mockAdminApi.getClientKeyPair());
    ListeningScheduledExecutorService executor = CoreSocketFactory.getDefaultExecutor();
    TestDataSupplier supplier = new TestDataSupplier(true);
    CloudSqlInstance instance =
        new CloudSqlInstance(
            "a:b:c",
            supplier,
            AuthType.PASSWORD,
            new TestCredentialFactory(),
            executor,
            keyPairFuture,
            newRateLimiter());

    // Attempt to retrieve data, ensure we wait for success
    // 3 simultaneous requests can put enough pressure on the thread pool
    // to cause a deadlock.
    ListenableFuture<List<Object>> allData =
        Futures.allAsList(
            executor.submit(() -> instance.getSslData()),
            executor.submit(() -> instance.getSslData()),
            executor.submit(() -> instance.getSslData()));

    List<Object> d = allData.get();
    assertThat(d.get(0)).isNotNull();
    assertThat(d.get(1)).isNotNull();
    assertThat(d.get(2)).isNotNull();

    // Test that there was 1 successful attempt from when the CloudSqlInstance was instantiated.
    assertThat(supplier.successCounter.get()).isEqualTo(1);

    // Now, run through a number of cycles where we call forceRefresh() multiple times and make sure
    // that it only runs one successful refresh per cycle 10 times. This will prove that
    // forceRefresh() will balk when an operation is in progress, and that forceRefresh() will retry
    // after a failed attempt to get InstanceData.
    for (int i = 1; i <= FORCE_REFRESH_COUNT; i++) {
      // Assert the expected number of successful refresh operations
      assertThat(supplier.successCounter.get()).isEqualTo(i);

      // Call forceRefresh 3 times in rapid succession. This should only kick off 1 refresh
      // cycle.
      instance.forceRefresh();
      // force Java to run a different thread now. That gives the refresh task an opportunity to
      // start.
      Thread.sleep(0);
      instance.forceRefresh();
      instance.forceRefresh();

      Thread.sleep(DEFAULT_WAIT); // Wait for the refresh to occur

      // This will loop forever if CloudSqlInstance does not successfully retry after a failed
      // forceRefresh() attempt.
      while (true) {
        try {
          // Attempt to get sslData 3 times, simultaneously, in different threads.
          ListenableFuture<List<Object>> allData2 =
              Futures.allAsList(
                  executor.submit(() -> instance.getSslData()),
                  executor.submit(() -> instance.getSslData()),
                  executor.submit(() -> instance.getSslData()));

          // This should return immediately
          allData2.get();
          break;

        } catch (ExecutionException e) {
          // We expect some of these to throw an exception indicating that the refresh cycle
          // got a failed attempt. When they throw an exception,
          // sleep and try again. This shows that the refresh cycle is working.
          //noinspection BusyWait
          Thread.sleep(DEFAULT_WAIT);
        }
      }

      // Wait for the actual background refresh to complete before checking the success counter.
      Thread.sleep(DEFAULT_WAIT);

      // Assert the expected number of successful refresh operations at the end of the loop
      // is only one more than the beginning of the loop.
      // This means that only one additional refresh operation was run.
      assertThat(supplier.successCounter.get()).isEqualTo(i + 1);

      Thread.sleep(DEFAULT_WAIT);
    }

    // Refresh count should equal initial refresh plus FORCE_REFRESH_COUNT times refreshing
    assertThat(supplier.successCounter.get()).isEqualTo(FORCE_REFRESH_COUNT + 1);
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
              newRateLimiter()));
    }

    // Get SSL Data for each instance, forcing the first refresh to complete.
    instances.forEach(CloudSqlInstance::getSslData);

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
              inst.getSslData();
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

  private RateLimiter newRateLimiter() {
    return RateLimiter.create(20.0); // 20/sec = every 50 ms
  }
}
