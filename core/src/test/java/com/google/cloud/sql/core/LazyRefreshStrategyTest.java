/*
 * Copyright 2024 Google LLC
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class LazyRefreshStrategyTest {
  public static final long TEST_TIMEOUT_MS = 3000;

  @Test
  public void testCloudSqlInstanceDataRetrievedSuccessfully() {
    final ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "LazyRefresherTest.testCloudSqlInstanceDataRetrievedSuccessfully", () -> data);
    ConnectionInfo gotInfo = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(gotInfo).isSameInstanceAs(data);
  }

  @Test
  public void testInstanceFailsOnConnectionError() {
    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "LazyRefresherTest.testInstanceFailsOnConnectionError",
            () -> {
              throw new RuntimeException("always fails");
            });

    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));

    assertThat(ex.getCause()).hasMessageThat().contains("always fails");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();
    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "LazyRefresherTest.testCloudSqlInstanceForcesRefresh",
            () -> {
              refreshCount.incrementAndGet();
              return data;
            });

    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    r.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);

    // getSslData again, and assert the refresh operation completed.
    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    ExampleData initialData = new ExampleData(Instant.now().plus(2, ChronoUnit.SECONDS));
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();

    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "LazyRefresherTest.testCloudSqlRefreshesExpiredData",
            () -> {
              int c = refreshCount.getAndIncrement();
              if (c == 0) {
                return initialData;
              }
              return data;
            });

    // Get the first data that is about to expire
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData);

    waitForExpiration(initialData);

    assertThat(r.getConnectionInfo(TEST_TIMEOUT_MS)).isSameInstanceAs(data);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  private static void waitForExpiration(ExampleData initialData) throws InterruptedException {
    // Wait for the instance to expire
    while (!Instant.now().isAfter(initialData.getExpiration())) {
      Thread.sleep(10);
    }
    // Sleep a few more ms to make sure that Instant.now() really is after expiration.
    // Fixes a date math race condition only present in Java 8.
    Thread.sleep(10);
  }

  @Test
  public void testThatConcurrentRequestsDontCauseDuplicateRefreshAttempts() throws Exception {
    ExampleData initialData = new ExampleData(Instant.now().plus(2, ChronoUnit.SECONDS));
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();

    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "LazyRefresherTest.testThatConcurrentRequestsDontCauseDuplicateRefreshAttempts",
            () -> {
              int c = refreshCount.getAndIncrement();
              if (c == 0) {
                return initialData;
              }
              return data;
            });

    // Get the first data that is about to expire
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData);

    // Wait for the instance to expire
    waitForExpiration(initialData);

    // Start multiple threads and request connection info
    Thread t1 = new Thread(() -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    Thread t2 = new Thread(() -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    Thread t3 = new Thread(() -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    t1.start();
    t2.start();
    t3.start();
    t1.join();
    t2.join();
    t3.join();

    // Assert that only one more refresh operation was performed.
    assertThat(refreshCount.get()).isEqualTo(2);
    assertThat(r.getConnectionInfo(TEST_TIMEOUT_MS)).isSameInstanceAs(data);
  }

  @Test
  public void testClosedCloudSqlInstanceDataThrowsException() {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    LazyRefreshStrategy r =
        new LazyRefreshStrategy(
            "RefresherTest.testClosedCloudSqlInstanceDataThrowsException", () -> data);
    r.close();

    assertThrows(IllegalStateException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    assertThrows(IllegalStateException.class, () -> r.forceRefresh());
  }

  private static class ExampleData extends ConnectionInfo {
    ExampleData(Instant expiration) {
      super(new InstanceMetadata(null, null), new SslData(null, null, null), expiration);
    }
  }
}
