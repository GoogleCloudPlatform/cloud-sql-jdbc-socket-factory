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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * RefreshCalculator determines the number of seconds until the next refresh operation using the
 * same algorithm used by the other Connectors.
 */
class RefreshCalculator {

  private static final int ONE_HOUR_IN_SECONDS = 3600;
  private static final int REFRESH_BUFFER_IN_SECONDS = 240; // Four minutes

  long calculateSecondsUntilNextRefresh(Instant now, Instant clientCertificateExpiration) {
    long secondsUntilExpiration = ChronoUnit.SECONDS.between(now, clientCertificateExpiration);
    if (secondsUntilExpiration < ONE_HOUR_IN_SECONDS) {
      if (secondsUntilExpiration < REFRESH_BUFFER_IN_SECONDS) {
        return 0;
      }
      return secondsUntilExpiration - REFRESH_BUFFER_IN_SECONDS;
    }
    return secondsUntilExpiration / 2;
  }
}
