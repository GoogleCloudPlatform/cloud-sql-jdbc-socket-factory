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

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Rate-limits */
public class RateLimitCalculator {
  private AtomicReference<Instant> lastAttempt = new AtomicReference<>();
  private AtomicInteger consecutiveFailureCount = new AtomicInteger();
  private final Duration minDelay;
  private final Duration delayIncrement;
  private final float delayFactor;
  private final Duration maxDelay;

  public RateLimitCalculator(
      Duration minDelay, Duration delayIncrement, float delayFactor, Duration maxDelay) {
    this.minDelay = minDelay;
    this.delayIncrement = delayIncrement;
    this.delayFactor = delayFactor;
    this.maxDelay = maxDelay;
  }

  void recordSuccess() {
    consecutiveFailureCount.set(0);
  }

  void recordFailure() {
    consecutiveFailureCount.incrementAndGet();
  }

  synchronized void recordAttempt() {
    lastAttempt.set(Instant.now());
  }

  Duration nextAttemptDelayMillis() {
    return calculateDelay(Instant.now(), lastAttempt.get(), consecutiveFailureCount.get());
  }

  Duration calculateDelay(Instant now, Instant lastAttempt, int failures) {
    double multiplier = Math.pow(delayFactor, failures);

    Duration delay = Duration.ofMillis((long) (delayIncrement.toMillis() * multiplier));

    // Apply minDelay and maxDelay bounds
    if (delay.compareTo(minDelay) < 0) {
      delay = minDelay;
    }
    if (delay.compareTo(maxDelay) > 0) {
      delay = maxDelay;
    }

    Instant nextAttempt = lastAttempt.plus(delay);
    if (now.isAfter(nextAttempt)) {
      return Duration.ZERO;
    }

    return Duration.between(now, nextAttempt);
  }
}
