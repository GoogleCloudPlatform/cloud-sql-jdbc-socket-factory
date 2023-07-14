/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;
import static java.time.temporal.ChronoUnit.SECONDS;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RefreshCalculatorTest {

  private final Duration input;
  private final Duration want;

  @Parameters(name = "Test {0}: calculateSecondsUntilNextRefresh({1})={2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(
        new Object[][] {
          {"when expiration is greater than 1 hour", Duration.ofHours(4), Duration.ofHours(2)},
          {"when expiration is equal to 1 hour", Duration.ofHours(1), Duration.ofMinutes(30)},
          {
            "when expiration is less than 1 hour, but greater than 4 minutes",
            Duration.ofMinutes(5),
            Duration.ofMinutes(1)
          },
          {"when expiration is less than 4 minutes", Duration.ofMinutes(3), Duration.ofMinutes(0)},
          {"when expiration is now", Duration.ofMinutes(0), Duration.ofMinutes(0)},
          {"when expiration is 62 minutes", Duration.ofMinutes(62), Duration.ofMinutes(31)},
          {"when expiration is 58 minutes", Duration.ofMinutes(58), Duration.ofMinutes(54)},
        });
  }

  public RefreshCalculatorTest(String name, Duration input, Duration want) {
    this.input = input;
    this.want = want;
    this.refreshCalculator = new RefreshCalculator();
  }

  private static final Instant NOW = Instant.now().truncatedTo(SECONDS);
  private RefreshCalculator refreshCalculator;

  @Test
  public void testDuration() {
    Duration nextRefresh =
        Duration.ofSeconds(
            refreshCalculator.calculateSecondsUntilNextRefresh(NOW, NOW.plus(input)));
    assertThat(nextRefresh).isEqualTo(want);
  }
}
