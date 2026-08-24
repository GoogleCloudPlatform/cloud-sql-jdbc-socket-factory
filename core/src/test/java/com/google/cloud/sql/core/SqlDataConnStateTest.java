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

import static com.google.common.truth.Truth.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SqlDataConnStateTest {

  @Test
  public void testInitialState() {
    SqlDataConnState state = new SqlDataConnState(Duration.ofSeconds(5));
    assertThat(state.isAllowed()).isTrue();
    assertThat(state.isCooldownActive(Instant.now())).isFalse();
    assertThat(state.getLastErr()).isNull();
    assertThat(state.getBackoffCounter()).isEqualTo(0);
    assertThat(state.getCooldownUntil()).isNull();
  }

  @Test
  public void testSetAllowed() {
    SqlDataConnState state = new SqlDataConnState(Duration.ofSeconds(5));
    state.setAllowed(false);
    assertThat(state.isAllowed()).isFalse();
    state.setAllowed(true);
    assertThat(state.isAllowed()).isTrue();
  }

  @Test
  public void testOnResourceExhausted_SetsCooldownWithBackoff() {
    // Fixed random with 0.0 jitter for deterministic calculation
    Random fixedRandom =
        new Random() {
          @Override
          public double nextDouble() {
            return 0.0;
          }
        };

    Duration base = Duration.ofSeconds(5);
    SqlDataConnState state = new SqlDataConnState(base, fixedRandom);
    Exception err1 = new RuntimeException("err1");

    Instant before = Instant.now();
    state.onResourceExhausted(err1);
    Instant after = Instant.now();

    assertThat(state.getBackoffCounter()).isEqualTo(1);
    assertThat(state.getLastErr()).isSameInstanceAs(err1);
    assertThat(state.isCooldownActive(Instant.now())).isTrue();

    // attempt 1 with jitter 0: 5000 * 1.618^(0) = 5000ms
    Duration cooldown1 = Duration.between(before, state.getCooldownUntil());
    assertThat(cooldown1.toMillis()).isAtLeast(4900L);
    assertThat(cooldown1.toMillis()).isAtMost(5200L);

    // attempt 2 with jitter 0: 5000 * 1.618^1 = 8090ms
    Exception err2 = new RuntimeException("err2");
    state.onResourceExhausted(err2);
    assertThat(state.getBackoffCounter()).isEqualTo(2);
    assertThat(state.getLastErr()).isSameInstanceAs(err2);
    Duration cooldown2 = Duration.between(Instant.now(), state.getCooldownUntil());
    assertThat(cooldown2.toMillis()).isAtLeast(7900L);
    assertThat(cooldown2.toMillis()).isAtMost(8200L);

    // Continue up to max attempts 5
    state.onResourceExhausted(new RuntimeException("err3"));
    assertThat(state.getBackoffCounter()).isEqualTo(3);
    state.onResourceExhausted(new RuntimeException("err4"));
    assertThat(state.getBackoffCounter()).isEqualTo(4);
    state.onResourceExhausted(new RuntimeException("err5"));
    assertThat(state.getBackoffCounter()).isEqualTo(5);
    // Should cap at 5
    state.onResourceExhausted(new RuntimeException("err6"));
    assertThat(state.getBackoffCounter()).isEqualTo(5);
  }

  @Test
  public void testOnSuccess_ResetsBackoffAndCooldown() {
    SqlDataConnState state = new SqlDataConnState(Duration.ofSeconds(5));
    state.onResourceExhausted(new RuntimeException("resource busy"));
    assertThat(state.getBackoffCounter()).isEqualTo(1);
    assertThat(state.getCooldownUntil()).isNotNull();

    state.onSuccess();
    assertThat(state.getBackoffCounter()).isEqualTo(0);
    assertThat(state.getCooldownUntil()).isNull();
    assertThat(state.isCooldownActive(Instant.now())).isFalse();
  }

  @Test
  public void testConstructor_NullOrNegativeDurationDefaults() {
    SqlDataConnState stateNull = new SqlDataConnState(null);
    stateNull.onResourceExhausted(new RuntimeException("err"));
    assertThat(stateNull.isCooldownActive(Instant.now())).isTrue();

    SqlDataConnState stateNegative = new SqlDataConnState(Duration.ofSeconds(-5));
    stateNegative.onResourceExhausted(new RuntimeException("err"));
    assertThat(stateNegative.isCooldownActive(Instant.now())).isTrue();
  }

  @Test
  public void testCooldownBackoff_NullOrZeroDuration() {
    assertThat(SqlDataConnState.cooldownBackoff(null, 1, new Random())).isEqualTo(Duration.ZERO);
    assertThat(SqlDataConnState.cooldownBackoff(Duration.ZERO, 1, new Random()))
        .isEqualTo(Duration.ZERO);
    assertThat(SqlDataConnState.cooldownBackoff(Duration.ofSeconds(-1), 1, new Random()))
        .isEqualTo(Duration.ZERO);
  }
}
