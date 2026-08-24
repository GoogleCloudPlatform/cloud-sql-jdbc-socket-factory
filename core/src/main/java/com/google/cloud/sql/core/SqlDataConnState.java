/*
 * Copyright 2026 Google LLC
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

import com.google.cloud.sql.ConnectorConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Random;

class SqlDataConnState {
  private static final double BACKOFF_MULTIPLIER = 1.618;
  private static final int MAX_BACKOFF_ATTEMPTS = 5;

  private final Duration baseCooldownPeriod;
  private final Random random;
  private boolean allowed = true;
  private Instant cooldownUntil;
  private Throwable lastErr;
  private int backoffCounter = 0;

  SqlDataConnState(Duration baseCooldownPeriod) {
    this(baseCooldownPeriod, new Random());
  }

  SqlDataConnState(Duration baseCooldownPeriod, Random random) {
    this.baseCooldownPeriod =
        baseCooldownPeriod != null && !baseCooldownPeriod.isNegative()
            ? baseCooldownPeriod
            : ConnectorConfig.DEFAULT_RESOURCE_EXHAUSTED_COOLDOWN_PERIOD;
    this.random = random != null ? random : new Random();
  }

  synchronized boolean isAllowed() {
    return allowed;
  }

  synchronized void setAllowed(boolean allowed) {
    this.allowed = allowed;
  }

  synchronized boolean isCooldownActive(Instant now) {
    return cooldownUntil != null && now.isBefore(cooldownUntil);
  }

  synchronized Throwable getLastErr() {
    return lastErr;
  }

  synchronized int getBackoffCounter() {
    return backoffCounter;
  }

  synchronized Instant getCooldownUntil() {
    return cooldownUntil;
  }

  synchronized void onResourceExhausted(Throwable err) {
    if (backoffCounter < MAX_BACKOFF_ATTEMPTS) {
      backoffCounter++;
    }
    Duration backoff = cooldownBackoff(baseCooldownPeriod, backoffCounter, random);
    cooldownUntil = Instant.now().plus(backoff);
    lastErr = err;
  }

  synchronized void onSuccess() {
    backoffCounter = 0;
    cooldownUntil = null;
  }

  static Duration cooldownBackoff(Duration base, int attempt, Random random) {
    if (base == null || base.isNegative() || base.isZero()) {
      return Duration.ZERO;
    }
    int effAttempt = Math.max(1, attempt);
    Random rnd = random != null ? random : new Random();
    double exp = (effAttempt - 1) + rnd.nextDouble();
    long millis = (long) (base.toMillis() * Math.pow(BACKOFF_MULTIPLIER, exp));
    return Duration.ofMillis(millis);
  }
}
