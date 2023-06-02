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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;

public class RefreshCalculatorTest {

  private static final Instant NOW = Instant.now().truncatedTo(SECONDS);
  private static final Instant SIXTY_TWO_MINUTES_FROM_NOW = NOW.plus(62, ChronoUnit.MINUTES);
  private static final Instant FIFTY_EIGHT_MINUTES_FROM_NOW = NOW.plus(58, ChronoUnit.MINUTES);
  private static final Instant THREE_MINUTES_FROM_NOW = NOW.plus(3, ChronoUnit.MINUTES);
  private static final int THIRTY_ONE_MINUTES_FROM_NOW_IN_SECONDS = 1860;
  private RefreshCalculator refreshCalculator;

  @Before
  public void setUp() {
    refreshCalculator = new RefreshCalculator();
  }

  @Test
  public void testCalculateSecondsUntilNextRefresh_whenDurationIsGreaterThanOneHour() {
    long secondsUntilNextRefresh =
        refreshCalculator.calculateSecondsUntilNextRefresh(NOW, SIXTY_TWO_MINUTES_FROM_NOW);
    // Seconds until next refresh = time remaining on certificate / 2
    assertThat(secondsUntilNextRefresh).isEqualTo(THIRTY_ONE_MINUTES_FROM_NOW_IN_SECONDS);
  }

  @Test
  public void testCalculateSecondsUntilNextRefresh_whenDurationIsLessThanOneHour() {
    long secondsUntilNextRefresh =
        refreshCalculator.calculateSecondsUntilNextRefresh(NOW, FIFTY_EIGHT_MINUTES_FROM_NOW);
    // Seconds until next refresh = 4 minutes before expiration
    assertThat(secondsUntilNextRefresh)
        .isEqualTo(SECONDS.between(NOW, FIFTY_EIGHT_MINUTES_FROM_NOW.minus(4, ChronoUnit.MINUTES)));
  }

  @Test
  public void testCalculateSecondsUntilNextRefresh_whenDurationIsLessThanFourMinutes() {
    long secondsUntilNextRefresh =
        refreshCalculator.calculateSecondsUntilNextRefresh(NOW, THREE_MINUTES_FROM_NOW);

    // Seconds until next refresh = now, certificate is expired
    assertThat(secondsUntilNextRefresh).isEqualTo(0L);
  }
}
