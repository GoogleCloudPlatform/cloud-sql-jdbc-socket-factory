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

import com.google.auth.oauth2.AccessToken;
import java.io.IOException;
import java.util.Optional;

/** Supplies an AccessToken to use when authenticating with the Google API. */
@FunctionalInterface
interface AccessTokenSupplier {

  /**
   * Returns a valid access token or Optional.empty() when no token is available.
   *
   * @return the access token
   * @throws IOException when an error occurs attempting to refresh the token.
   */
  Optional<AccessToken> get() throws IOException;
}
