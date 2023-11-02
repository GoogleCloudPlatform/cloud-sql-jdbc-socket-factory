/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class RefresherTest {

  public static final long TEST_TIMEOUT_MS = 3000;

  private AsyncRateLimiter rateLimiter = new AsyncRateLimiter(10);

  private ListeningScheduledExecutorService executorService;

  @Before
  public void before() throws Exception {
    executorService = newTestExecutor();
  }

  @After
  public void after() {
    executorService.shutdown();
    executorService = null;
  }

  @Test
  public void testCloudSqlInstanceDataRetrievedSuccessfully() {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    Refresher r =
        new Refresher(
            "testcase", executorService, () -> Futures.immediateFuture(data), rateLimiter);
    ConnectionInfo gotInfo = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(gotInfo).isSameInstanceAs(data);
  }

  @Test
  public void testInstanceFailsOnConnectionError() {
    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> Futures.immediateFailedFuture(new RuntimeException("always fails")),
            rateLimiter);
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    assertThat(ex).hasMessageThat().contains("always fails");
  }

  @Test
  public void testInstanceFailsOnTooLongToRetrieve() {
    PauseCondition cond = new PauseCondition();
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              cond.pause();
              return Futures.immediateFuture(data);
            },
            rateLimiter);
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    assertThat(ex).hasMessageThat().contains("No refresh has completed");
  }

  @Test
  public void testCloudSqlInstanceForcesRefresh() throws Exception {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition cond = new PauseCondition();
    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              int c = refreshCount.get();
              // Allow the first execution to complete immediately.
              // The second execution should pause until signaled.
              if (c == 1) {
                cond.pause();
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(data);
            },
            rateLimiter);
    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Force refresh, which will start, but not finish the refresh process.
    r.forceRefresh();

    // Then immediately getSslData() and assert that the refresh count has not changed.
    // Refresh count hasn't changed because we re-use the existing connection info.
    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    cond.proceed();
    cond.waitForPauseToEnd(1000L);
    cond.waitForCondition(() -> refreshCount.get() >= 2, 1000L);

    // getSslData again, and assert the refresh operation completed.
    r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testCloudSqlInstanceRetriesOnInitialFailures() throws Exception {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              int c = refreshCount.get();
              refreshCount.incrementAndGet();
              if (c == 0) {
                throw new RuntimeException("bad request 0");
              }
              return Futures.immediateFuture(data);
            },
            rateLimiter);

    // Get the first data that is about to expire
    long until = System.currentTimeMillis() + 3000;
    while (r.getConnectionInfo(TEST_TIMEOUT_MS) != data && System.currentTimeMillis() < until) {
      Thread.sleep(100);
    }
    assertThat(refreshCount.get()).isEqualTo(2);
    assertThat(r.getConnectionInfo(TEST_TIMEOUT_MS)).isEqualTo(data);
  }

  @Test
  public void testCloudSqlRefreshesExpiredData() throws Exception {
    ExampleData initialData = new ExampleData(Instant.now().plus(2, ChronoUnit.SECONDS));
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              int c = refreshCount.get();
              ExampleData refreshResult = data;
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
            rateLimiter);

    // Get the first data that is about to expire
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(r.getConnectionInfo(TEST_TIMEOUT_MS));

    // Wait for the instance to expire
    while (Instant.now().isBefore(initialData.getExpiration())) {
      Thread.sleep(10);
    }

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved. In this case, the new data has not yet been retrieved, so the operation
    // should time out after 100ms and throw an exception.
    assertThrows(RuntimeException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    assertThat(refreshCount.get()).isEqualTo(1);

    // Allow the second refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(1000L);

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(() -> r.getConnectionInfo(TEST_TIMEOUT_MS) == data, 1000L);
  }

  @Test
  public void testThatForceRefreshBalksWhenAScheduledRefreshIsInProgress() throws Exception {
    // Set expiration 1 minute in the future, so that it will trigger a scheduled refresh
    // and won't expire during this testcase.
    ExampleData expiresInOneMinute = new ExampleData(Instant.now().plus(1, ChronoUnit.MINUTES));

    // Set the next refresh data expiration 1 hour in the future.
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();
    final PauseCondition refresh1 = new PauseCondition();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              int c = refreshCount.get();
              ExampleData refreshResult = data;
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
            rateLimiter);

    refresh0.proceed();
    refresh0.waitForPauseToEnd(1000);
    refresh0.waitForCondition(() -> refreshCount.get() > 0, 1000);
    // Get the first data that is about to expire
    assertThat(refreshCount.get()).isEqualTo(1);
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(d).isSameInstanceAs(expiresInOneMinute);

    // Because the data is about to expire, scheduled refresh will begin immediately.
    // Wait until refresh is in progress.
    refresh1.waitForPauseToStart(1000);

    // Then call forceRefresh(), which should balk because a refresh attempt is in progress.
    r.forceRefresh();

    // Finally, allow the scheduled refresh operation to complete
    refresh1.proceed();
    refresh1.waitForPauseToEnd(5000);
    refresh1.waitForCondition(() -> refreshCount.get() > 1, 1000);

    // Now that the ConnectionInfo has expired, this getSslData should pause until new data
    // has been retrieved.

    // getSslData again, and assert the refresh operation completed.
    refresh1.waitForCondition(() -> r.getConnectionInfo(TEST_TIMEOUT_MS) == data, 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testThatForceRefreshBalksWhenAForceRefreshIsInProgress() throws Exception {
    ExampleData initialData = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh1 = new PauseCondition();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
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
            rateLimiter);

    // Get the first data that is about to expire
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(initialData);

    // call forceRefresh twice, this should only result in 1 refresh fetch
    r.forceRefresh();
    r.forceRefresh();

    // Allow the refresh operation to complete
    refresh1.proceed();

    // Now that the ConnectionInfo has expired, this getSslData should pause until new data
    // has been retrieved.
    refresh1.waitForPauseToEnd(1000);
    refresh1.waitForCondition(() -> refreshCount.get() >= 2, 1000);

    // assert the refresh operation completed exactly once after
    // forceRefresh was called multiple times.
    refresh1.waitForCondition(() -> r.getConnectionInfo(TEST_TIMEOUT_MS) == data, 1000L);
    assertThat(refreshCount.get()).isEqualTo(2);
  }

  @Test
  public void testRefreshRetriesOnAfterFailedAttempts() throws Exception {
    ExampleData aboutToExpireData = new ExampleData(Instant.now().plus(10, ChronoUnit.MILLIS));
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition badRequest1 = new PauseCondition();
    final PauseCondition badRequest2 = new PauseCondition();
    final PauseCondition goodRequest = new PauseCondition();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
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
            rateLimiter);

    // Get the first data that is about to expire
    ConnectionInfo d = r.getConnectionInfo(TEST_TIMEOUT_MS);
    assertThat(refreshCount.get()).isEqualTo(1);
    assertThat(d).isSameInstanceAs(aboutToExpireData);

    // Don't force a refresh, this should automatically schedule a refresh right away because
    // the token returned in the first request had less than 4 minutes before it expired.

    // Wait for the current ConnectionInfo to actually expire.
    while (Instant.now().isBefore(aboutToExpireData.getExpiration())) {
      Thread.sleep(10);
    }

    // Start a thread to getSslData(). This will eventually return after the
    // failed attempts.
    AtomicReference<ConnectionInfo> earlyFetchAttempt = new AtomicReference<>();
    Thread t = new Thread(() -> earlyFetchAttempt.set(r.getConnectionInfo(TEST_TIMEOUT_MS)));
    t.start();

    // Orchestrate the failed attempts

    // Allow the second refresh operation to complete
    badRequest1.proceed();
    badRequest1.waitForPauseToEnd(5000);
    badRequest1.waitForCondition(() -> refreshCount.get() == 2, 2000);

    // Now that the InstanceData has expired, this getSslData should pause until new data
    // has been retrieved. In this case, the new data has not yet been retrieved, so the operation
    // should time out after 10ms.
    assertThrows(RuntimeException.class, () -> r.getConnectionInfo(10));

    // Allow the second bad request completes
    badRequest2.proceed();
    badRequest2.waitForCondition(() -> refreshCount.get() == 3, 2000);

    // Allow the final good request to complete
    goodRequest.proceed();
    goodRequest.waitForCondition(() -> refreshCount.get() == 4, 2000);

    // Try getSslData() again, and assert the refresh operation eventually completes.
    goodRequest.waitForCondition(() -> r.getConnectionInfo(TEST_TIMEOUT_MS) == data, 2000);

    // Wait for the thread to exit, meaning getSslData() finally returned
    // after several failed attempts. Assert that the correct InstanceData
    // eventually returned.
    t.join();
    assertThat(earlyFetchAttempt.get()).isSameInstanceAs(data);
  }

  @Test
  public void testClosedCloudSqlInstanceDataThrowsException() {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));
    Refresher r =
        new Refresher(
            "testcase", executorService, () -> Futures.immediateFuture(data), rateLimiter);
    r.close();

    assertThrows(IllegalStateException.class, () -> r.getConnectionInfo(TEST_TIMEOUT_MS));
    assertThrows(IllegalStateException.class, () -> r.forceRefresh());
  }

  @Test
  public void testClosedCloudSqlInstanceDataStopsRefreshTasks() throws Exception {
    ExampleData data = new ExampleData(Instant.now().plus(1, ChronoUnit.HOURS));

    AtomicInteger refreshCount = new AtomicInteger();
    final PauseCondition refresh0 = new PauseCondition();

    Refresher r =
        new Refresher(
            "testcase",
            executorService,
            () -> {
              int c = refreshCount.get();
              if (c == 0) {
                refresh0.pause();
              }
              refreshCount.incrementAndGet();
              return Futures.immediateFuture(data);
            },
            rateLimiter);

    // Wait for the first refresh attempt to complete.
    refresh0.proceed();
    refresh0.waitForPauseToEnd(TEST_TIMEOUT_MS);

    // Assert that refresh gets instance data before it is closed
    refresh0.waitForCondition(() -> refreshCount.get() == 1, TEST_TIMEOUT_MS);

    // Assert that the next refresh task is scheduled in the future
    assertThat(r.getNext().isDone()).isFalse();

    // Close the instance
    r.close();

    // Assert that the next refresh task is canceled
    assertThat(r.getNext().isDone()).isTrue();
    assertThat(r.getNext().isCancelled()).isTrue();
  }

  private static class ExampleData extends ConnectionInfo {

    ExampleData(Instant expiration) {
      super(new InstanceMetadata(null, null), new SslData(null, null, null), expiration);
    }
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
