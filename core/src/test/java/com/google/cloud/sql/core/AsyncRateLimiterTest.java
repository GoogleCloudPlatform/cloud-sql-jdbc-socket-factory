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

import com.google.cloud.sql.core.AsyncRateLimiter.RateLimitAcquisition;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.junit.Test;

public class AsyncRateLimiterTest {

  @Test
  public void firstCallShouldReturnNoDelay() {
    long now = System.currentTimeMillis();
    AsyncRateLimiter c = new AsyncRateLimiter(100);
    assertThat(c.nextDelayMs(now)).isEqualTo(0);
  }

  @Test
  public void subsequentCallsShouldReturnDelays() {
    long now = System.currentTimeMillis();
    AsyncRateLimiter c = new AsyncRateLimiter(100);
    // When calls occur at the same timestamp, one will return 0 and
    // the other will return the full delay.
    assertThat(c.nextDelayMs(now)).isEqualTo(0);
    assertThat(c.nextDelayMs(now)).isEqualTo(100);
  }

  @Test
  public void delayBeforeExpiration() throws InterruptedException {
    long now = System.currentTimeMillis();
    AsyncRateLimiter c = new AsyncRateLimiter(20);
    assertThat(c.nextDelayMs(now)).isEqualTo(0);
    // delay before the expiration will result in the difference between
    // time passed and the delay.
    assertThat(c.nextDelayMs(now + 15)).isEqualTo(5);
  }

  @Test
  public void noDelayExactlyAtExpiration() throws InterruptedException {
    long now = System.currentTimeMillis();
    AsyncRateLimiter c = new AsyncRateLimiter(20);
    assertThat(c.nextDelayMs(now)).isEqualTo(0);
    // delay at exactly the expiration time is 0 for the first call,
    assertThat(c.nextDelayMs(now + 20)).isEqualTo(0);
    // and the full delay for subsequent calls
    assertThat(c.nextDelayMs(now + 20)).isEqualTo(20);
  }

  @Test
  public void noDelayAfterExpiration() throws InterruptedException {
    long now = System.currentTimeMillis();
    AsyncRateLimiter c = new AsyncRateLimiter(20);
    assertThat(c.nextDelayMs(now)).isEqualTo(0);

    // delay after the expiration time is 0 for the first call,
    assertThat(c.nextDelayMs(now + 22)).isEqualTo(0);
    // and the full delay for subsequent calls
    assertThat(c.nextDelayMs(now + 22)).isEqualTo(20);
  }

  @Test
  public void asyncWorks() throws InterruptedException, ExecutionException {
    // During refresh, each instance consumes 2 threads from the thread pool. By using 8 threads,
    // there should be enough free threads so that there will not be a deadlock. Most users
    // configure 3 or fewer instances, requiring 6 threads during refresh. By setting
    // this to 8, it's enough threads for most users, plus a safety factor of 2.

    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(8);

    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);

    ListeningScheduledExecutorService ex =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));
    long delay = 200;

    // Set up a rate limiter that enforces 100ms minimum time between requests.
    AsyncRateLimiter c = new AsyncRateLimiter(delay);

    final long start = System.currentTimeMillis();
    List<ListenableFuture<RateLimitAcquisition>> futures = new ArrayList<>();

    for (int i = 0; i < 10; i++) {
      futures.add(c.acquireAsync(new RateLimitAcquisition(), ex));
    }

    // Wait for all futures to finish.
    Futures.whenAllComplete(futures).run(() -> {}, executor).get();

    // Make 3 async attempts to acquire the rate limit.
    // these should each resolve 1 second appart.
    // futures[0] should resolve immediately
    // futures[1] should resolve after ~200ms
    // futures[2] should resolve after ~400ms
    // ...etc

    // Find the offset from start for the first and last request processed
    List<RateLimitAcquisition> trackers = new ArrayList<>();
    for (ListenableFuture<RateLimitAcquisition> future : futures) {
      RateLimitAcquisition rla = future.get();
      trackers.add(rla);
    }
    trackers.sort((a, b) -> (int) (a.getAcquireTimestampMs() - b.getAcquireTimestampMs()));

    // Assert that the first attempt did not have to wait a full delay to run.
    long firstAttempt = trackers.get(0).getAcquireTimestampMs();
    assertThat(firstAttempt - start).isLessThan((long) (delay * .8));

    // For the rest of the attempts, make sure that the tracker
    // waited longer than the prior attempt before running
    for (int i = 1; i < trackers.size(); i++) {
      long doneTs = trackers.get(i).getAcquireTimestampMs();
      long priorDoneTs = trackers.get(i - 1).getAcquireTimestampMs();

      // It had to retry at least once
      assertThat(trackers.get(i).getAttempts()).isGreaterThan(0);

      // It happened after the prior attempt.
      assertThat(doneTs - priorDoneTs).isGreaterThan((long) (delay * .8));
    }
  }
}
