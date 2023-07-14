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

import java.time.Duration;
import java.time.Instant;

/**
 * RefreshCalculator determines the number of seconds until the next refresh operation using the
 * same algorithm used by the other Connectors.
 */
class RefreshCalculator {

  // defaultRefreshBuffer is the minimum amount of time for which a
  // certificate must be valid to ensure the next refresh attempt has adequate
  // time to complete.
  private static final Duration DEFAULT_REFRESH_BUFFER = Duration.ofMinutes(4);

  long calculateSecondsUntilNextRefresh(Instant now, Instant expiration) {
    Duration timeUntilExp = Duration.between(now, expiration);

    if (timeUntilExp.compareTo(Duration.ofHours(1)) < 0) {
      if (timeUntilExp.compareTo(DEFAULT_REFRESH_BUFFER) < 0) {
        // If the time until the certificate expires is less the refresh buffer, schedule the
        // refresh immediately
        return 0;
      }
      // Otherwise schedule a refresh in (timeUntilExp - buffer) seconds
      return timeUntilExp.minus(DEFAULT_REFRESH_BUFFER).getSeconds();
    }

    // If the time until the certificate expires is longer than an hour, return timeUntilExp//2
    return timeUntilExp.dividedBy(2).getSeconds();
  }
}
