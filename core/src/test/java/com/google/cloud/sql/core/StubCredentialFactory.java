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

import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.cloud.sql.CredentialFactory;

class StubCredentialFactory implements CredentialFactory {

  String accessToken;
  long expTime;

  StubCredentialFactory() {}

  StubCredentialFactory(String accessToken, long expTime) {
    this.accessToken = accessToken;
    this.expTime = expTime;
  }

  StubCredentialFactory(String accessToken) {
    this.accessToken = accessToken;
  }

  @Override
  public HttpRequestInitializer create() {
    MockGoogleCredential testCredential = new MockGoogleCredential.Builder().build();
    testCredential.setAccessToken(accessToken);
    testCredential.setExpirationTimeMilliseconds(expTime);
    return testCredential;
  }
}
