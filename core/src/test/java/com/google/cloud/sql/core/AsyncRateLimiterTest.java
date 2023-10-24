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

import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Test;

public class AsyncRateLimiterTest {

  @Test
  public void firstCallShouldReturnNoDelay() {
    RateLimiterTestHarness th = new RateLimiterTestHarness(100);
    ListenableFuture<?> f = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f.isDone()).isTrue();
  }

  @Test
  public void subsequentCallsShouldReturnDelays() {
    RateLimiterTestHarness th = new RateLimiterTestHarness(100);
    ListenableFuture<?> f1 = th.rateLimiter.acquireAsync(th.ex);
    ListenableFuture<?> f2 = th.rateLimiter.acquireAsync(th.ex);
    // When calls occur at the same timestamp, one will return 0 and
    // the other will return the full delay.
    assertThat(f1.isDone()).isTrue();
    assertThat(f2.isDone()).isFalse();
  }

  @Test
  public void delayBeforeExpiration() throws InterruptedException {
    RateLimiterTestHarness th = new RateLimiterTestHarness(100);
    ListenableFuture<?> f1 = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f1.isDone()).isTrue();

    // Before the expiration, isDone should be false
    th.tickMs(99);
    ListenableFuture<?> f2 = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f2.isDone()).isFalse();

    // Exactly at the expiration, isDone should be true
    th.tickMs(1);
    assertThat(f2.isDone()).isTrue();
  }

  @Test
  public void noDelayExactlyAtExpiration() throws InterruptedException {
    RateLimiterTestHarness th = new RateLimiterTestHarness(100);
    ListenableFuture<?> f1 = th.rateLimiter.acquireAsync(th.ex);
    ListenableFuture<?> f2 = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f1.isDone()).isTrue();
    assertThat(f2.isDone()).isFalse();

    th.tickMs(50);
    assertThat(f2.isDone()).isFalse();

    th.tickMs(100);
    assertThat(f2.isDone()).isTrue();
  }

  @Test
  public void noDelayAfterExpiration() throws InterruptedException {
    RateLimiterTestHarness th = new RateLimiterTestHarness(100);
    ListenableFuture<?> f1 = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f1.isDone()).isTrue();

    th.tickMs(101);
    ListenableFuture<?> f2 = th.rateLimiter.acquireAsync(th.ex);
    assertThat(f2.isDone()).isTrue();
  }

  @Test
  public void testAsyncWorks() {
    final long delay = 100;

    RateLimiterTestHarness th = new RateLimiterTestHarness(delay);

    List<ListenableFuture<?>> futures = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      futures.add(th.rateLimiter.acquireAsync(th.ex));
    }

    // First attempt happens without any delay, because nothing is run yet.
    assertThat(futures.stream().mapToInt(f -> f.isDone() ? 1 : 0).sum()).isEqualTo(1);

    // Tick forward less than the delay.
    th.tickMs(50);

    // When all futures are evaluated again immediately, still only 1 request has finished.
    // because not enough time has elapsed.
    assertThat(futures.stream().mapToInt(f -> f.isDone() ? 1 : 0).sum()).isEqualTo(1);

    // Tick forward more than the delay. Now 2 attempts should have finished.
    th.tickMs(100);

    assertThat(futures.stream().mapToInt(f -> f.isDone() ? 1 : 0).sum()).isEqualTo(2);

    // Tick forward more than the delay. Now 3 attempts should have finished.
    th.tickMs(100);
    assertThat(futures.stream().mapToInt(f -> f.isDone() ? 1 : 0).sum()).isEqualTo(3);
  }

  private static class RateLimiterTestHarness {

    final AtomicLong now = new AtomicLong(System.currentTimeMillis());
    final DeterministicScheduler ex = new DeterministicScheduler();
    final AsyncRateLimiter rateLimiter;

    RateLimiterTestHarness(long delay) {
      rateLimiter = new AsyncRateLimiter(delay, now::get);
    }

    private void tickMs(long ms) {
      now.addAndGet(ms);
      ex.tick(ms, TimeUnit.MILLISECONDS);
    }
  }
}
