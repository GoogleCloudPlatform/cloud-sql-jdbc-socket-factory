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

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RateLimitCalculatorTest {

  // An exponential backoff rate limiter.
  // Expected output:

  private final RateLimitCalculator c =
      new RateLimitCalculator(
          Duration.ofMillis(25), Duration.ofMillis(10), 2f, Duration.ofMillis(100));

  private final int failures;
  private final Duration wants;

  public RateLimitCalculatorTest(String name, int failures, Duration wants) {
    this.failures = failures;
    this.wants = wants;
  }

  @Parameters(name = "Test {0}: testRateLimit(failures={1}) == {2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"min delay between attempts", 0, Duration.ofMillis(25)},
          {"one failure, min delay wins", 1, Duration.ofMillis(25)},
          {"two failures, incremental delay * delayFactor ^ attempts", 2, Duration.ofMillis(40)},
          {"three failures, incremental delay * delayFactor ^ attempts", 3, Duration.ofMillis(80)},
          {"four failures, max delay wins", 4, Duration.ofMillis(100)},
        });
  }

  @Test
  public void testRateLimit() {
    c.recordAttempt();
    for (int i = 0; i < failures; i++) {
      c.recordFailure();
    }
    Duration got = c.nextAttemptDelayMillis();
    // Assert that it's within 2ms, errors are due to floating point math
    assertThat(got.toMillis()).isGreaterThan(wants.toMillis() - 2);
    assertThat(got.toMillis()).isLessThan(wants.toMillis() + 2);
  }

  @Test
  public void testRateLimitAfterTimePasses() {
    // Check that when the current time is a little later than the last
    // attempt, that the delay accounts for time that already passed.

    Instant now = Instant.now();
    Instant attempt = now.minusMillis(5);
    Duration wantLater = wants.minusMillis(5);
    Duration gotLater = c.calculateDelay(now, attempt, failures);

    // Assert that it's within 2ms, errors are due to floating point math
    assertThat(gotLater.toMillis()).isGreaterThan(wantLater.toMillis() - 2);
    assertThat(gotLater.toMillis()).isLessThan(wantLater.toMillis() + 2);
  }
}
