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
  public ListenableFuture<?> acquireAsync(ScheduledExecutorService executor) {
    return acquireAsync(new RateLimitAcquisition(), executor);
  }

  @VisibleForTesting
  ListenableFuture<RateLimitAcquisition> acquireAsync(
      RateLimitAcquisition rla, ScheduledExecutorService executor) {
    long limit = this.nextDelayMs(System.currentTimeMillis());
    if (limit > 0) {
      return Futures.scheduleAsync(
          () -> this.acquireAsync(rla.retry(), executor), limit, TimeUnit.MILLISECONDS, executor);
    }
    return Futures.immediateFuture(rla.done());
  }

  @VisibleForTesting
  static class RateLimitAcquisition {
    long attempts;
    long acquireTimestampMs;

    private RateLimitAcquisition retry() {
      attempts++;
      return this;
    }

    private RateLimitAcquisition done() {
      acquireTimestampMs = System.currentTimeMillis();
      return this;
    }

    long getAttempts() {
      return attempts;
    }

    long getAcquireTimestampMs() {
      return acquireTimestampMs;
    }
  }
}
