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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A simple, constant-time rate limit calculator. Ensures that there is always at least
 * delayBetweenAttempts milliseconds between attempts.
 */
class AsyncRateLimiter {
  private long nextOperationTimestamp;
  private final long delayBetweenAttempts;

  AsyncRateLimiter(long delayBetweenAttempts) {
    this.delayBetweenAttempts = delayBetweenAttempts;
  }

  /**
   * Returns the number of milliseconds to delay before proceeding with the rate limited operation.
   * If this returns > 0, the operation must call "acquire" again until it returns 0.
   */
  @VisibleForTesting
  synchronized long nextDelayMs(long nowTimestampMs) {
    // allow exactly 1 operation to pass the timestamp.
    if (nextOperationTimestamp <= nowTimestampMs) {
      nextOperationTimestamp = nowTimestampMs + delayBetweenAttempts;
      return 0;
    }

    return nextOperationTimestamp - nowTimestampMs;
  }

  /**
   * Returns a future that will be done when the rate limit has been acquired.
   *
   * @param executor the executor to use to schedule future checks for available rate limits.
   */
  public ListenableFuture<RateLimitTracker> acquireAsync(ScheduledExecutorService executor) {
    RateLimitTracker t = new RateLimitTracker(System.currentTimeMillis());
    return acquireWithTracker(t, executor);
  }

  private ListenableFuture<RateLimitTracker> acquireWithTracker(
      RateLimitTracker t, ScheduledExecutorService executor) {
    long limit = this.nextDelayMs(System.currentTimeMillis());
    if (limit > 0) {
      return Futures.scheduleAsync(
          () -> this.acquireWithTracker(t.nextAttempt(), executor),
          limit,
          TimeUnit.MILLISECONDS,
          executor);
    }
    return Futures.immediateFuture(t.done());
  }

  public static class RateLimitTracker {
    final long startTimestampMs;
    long acquireAttempts;
    long doneTimestampMs;

    private RateLimitTracker(long startTimestampMs) {
      this.startTimestampMs = startTimestampMs;
    }

    private RateLimitTracker nextAttempt() {
      acquireAttempts++;
      return this;
    }

    private RateLimitTracker done() {
      this.doneTimestampMs = System.currentTimeMillis();
      return this;
    }

    public long getStartTimestampMs() {
      return startTimestampMs;
    }

    public long getAcquireAttempts() {
      return acquireAttempts;
    }

    public long getDoneTimestampMs() {
      return doneTimestampMs;
    }

    public long getElapsedMs() {
      return doneTimestampMs - startTimestampMs;
    }

    @Override
    public String toString() {
      return "RateLimitTracker{"
          + "elapsedMs="
          + (doneTimestampMs - startTimestampMs)
          + ", acquireAttempts="
          + acquireAttempts
          + '}';
    }
  }
}
