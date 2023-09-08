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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

public class RateLimitCalculatorTest {

  @Test
  public void firstCallShouldReturnNoDelay() {
    RateLimitCalculator c = new RateLimitCalculator(100);
    assertThat(c.acquire()).isEqualTo(0);
  }

  @Test
  public void subsequentCallsShouldReturnDelays() {
    RateLimitCalculator c = new RateLimitCalculator(100);
    assertThat(c.acquire()).isEqualTo(0);
    long next1 = c.acquire();
    assertThat(next1).isGreaterThan(95);
    assertThat(next1).isLessThan(105);
    long next2 = c.acquire();
    assertThat(next2).isGreaterThan(95);
    assertThat(next2).isLessThan(105);
  }

  @Test
  public void onlyOneShouldProceedAfterDelay() throws InterruptedException {
    RateLimitCalculator c = new RateLimitCalculator(20);
    assertThat(c.acquire()).isEqualTo(0);
    assertThat(c.acquire()).isGreaterThan(0);
    assertThat(c.acquire()).isGreaterThan(0);

    // Wait for the 20ms delay to expire
    Thread.sleep(30);

    // Attempt to acquire the rate limit again.
    // First call succeeds
    assertThat(c.acquire()).isEqualTo(0);

    // Second and third calls return another delay
    assertThat(c.acquire()).isGreaterThan(0);
    assertThat(c.acquire()).isGreaterThan(0);
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
    ListeningScheduledExecutorService ex =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));

    // Set up a rate limiter that enforces 100ms minimum time between requests.
    RateLimitCalculator c = new RateLimitCalculator(100);

    List<Long> delays = new ArrayList<>();
    List<ListenableFuture<?>> futures = new ArrayList<>();
    AtomicLong last = new AtomicLong(System.currentTimeMillis());

    for (int i = 0; i < 10; i++) {
      futures.add(
          Futures.whenAllComplete(c.acquireAsync(ex, 1))
              .run(
                  () -> {
                    long now = System.currentTimeMillis();
                    delays.add(now - last.getAndSet(now));
                  },
                  executor));
    }
    Futures.whenAllComplete(futures).run(() -> {}, executor).get();

    // Make 3 async attempts to acquire the rate limit.
    // these should each resolve 1 second appart.
    // futures[0] should resolve immediately
    // futures[1] should resolve after ~1 sec
    // futures[2] should resolve after ~2 sec
    // ...etc

    // Compute the first delay to acquire a rate limit lock,
    // and the average time between subsequent rate locks.
    long total = 0;
    long count = 0;
    long min = Long.MAX_VALUE;
    for (long delay : delays) {
      min = Math.min(min, delay);
      if (delay < 20) {
        continue;
      }
      total += delay;
      count++;
    }

    // Assert that the shortest time is almost immediately
    assertThat(min).isLessThan(20);

    // Make sure the average time between acquisitions that had to wait between
    // rate limit acquisitions is about 100 millis
    assertThat(total / count).isGreaterThan(90);
    assertThat(total / count).isLessThan(110);
  }
}
