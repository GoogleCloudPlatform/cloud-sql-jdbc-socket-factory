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
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.util.Date;

public class StubCredentialFactory implements CredentialFactory {

  String accessToken;
  Long expirationTimeInMilliseconds;

  StubCredentialFactory() {}

  StubCredentialFactory(String accessToken, Long expirationTimeInMilliseconds) {
    this.accessToken = accessToken;
    this.expirationTimeInMilliseconds = expirationTimeInMilliseconds;
  }

  @Override
  public GoogleCredentials createGoogleCredentials() {
    return new MockGoogleCredentials(
        new AccessToken(
            accessToken,
            expirationTimeInMilliseconds != null
                ? new Date(System.currentTimeMillis() + expirationTimeInMilliseconds)
                : null));
  }
}
