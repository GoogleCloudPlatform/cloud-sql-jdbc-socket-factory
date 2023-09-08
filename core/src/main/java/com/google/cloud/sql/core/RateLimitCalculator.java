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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A simple, constant-time rate limit calculator. Ensures that there is always at least
 * delayBetweenAttempts milliseconds between attempts.
 */
class RateLimitCalculator {
  private volatile long nextOperationTimestamp;
  private final long delayBetweenAttempts;

  RateLimitCalculator(long delayBetweenAttempts) {
    this.delayBetweenAttempts = delayBetweenAttempts;
  }

  /**
   * Returns the number of milliseconds to delay before proceeding with the rate limited operation.
   * If this returns <= 0, the operation must call "acquire" again until it returns -1 to avoid race
   * conditions.
   */
  synchronized long acquire() {
    long now = System.currentTimeMillis();

    if (nextOperationTimestamp < now) {
      nextOperationTimestamp = now + delayBetweenAttempts;
      return -1;
    }

    return nextOperationTimestamp - now;
  }

  /**
   * Returns a future that will be done when the rate limit has been acquired.
   *
   * @param executor the executor to use to schedule future checks for available rate limits.
   */
  public ListenableFuture<Long> acquireAsync(ScheduledExecutorService executor) {
    long limit = this.acquire();
    if (limit >= 0) {
      return Futures.scheduleAsync(
          () -> this.acquireAsync(executor), limit, TimeUnit.MILLISECONDS, executor);
    }

    System.out.println("Acquired! " + limit);
    return Futures.immediateFuture(Long.valueOf(-1));
  }
}
