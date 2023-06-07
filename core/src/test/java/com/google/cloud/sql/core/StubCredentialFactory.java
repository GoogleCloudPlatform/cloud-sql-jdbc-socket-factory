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
import com.google.auth.RequestMetadataCallback;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.CredentialFactory;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class StubCredentialFactory implements CredentialFactory {

  String accessToken;
  Long expirationTimeInMilliseconds;
  OAuth2Credentials oAuth2Credentials;

  StubCredentialFactory() {}

  StubCredentialFactory(String accessToken, Long expirationTimeInMilliseconds) {
    this.accessToken = accessToken;
    this.expirationTimeInMilliseconds = expirationTimeInMilliseconds;
  }

  @Override
  public HttpRequestInitializer create() {
    if (oAuth2Credentials != null) {
      return new HttpCredentialsAdapter(new RefreshGoogleCredentials(oAuth2Credentials));
    } else {
      MockGoogleCredential testCredential = new MockGoogleCredential.Builder().build();
      testCredential.setAccessToken(accessToken);
      testCredential.setExpirationTimeMilliseconds(expirationTimeInMilliseconds);
      return testCredential;
    }
  }

  private static class RefreshGoogleCredentials extends GoogleCredentials {
    private final OAuth2Credentials oauth2;

    RefreshGoogleCredentials(OAuth2Credentials oauth2) {
      super(oauth2.getAccessToken());
      this.oauth2 = oauth2;
    }

    @Override
    public String getAuthenticationType() {
      return oauth2.getAuthenticationType();
    }

    @Override
    public boolean hasRequestMetadata() {
      return oauth2.hasRequestMetadata();
    }

    @Override
    public boolean hasRequestMetadataOnly() {
      return oauth2.hasRequestMetadataOnly();
    }

    @Override
    public void getRequestMetadata(URI uri, Executor executor, RequestMetadataCallback callback) {
      oauth2.getRequestMetadata(uri, executor, callback);
    }

    @Override
    public Map<String, List<String>> getRequestMetadata(URI uri) throws IOException {
      return oauth2.getRequestMetadata(uri);
    }

    @Override
    public void refresh() throws IOException {
      oauth2.refresh();
    }

    @Override
    public void refreshIfExpired() throws IOException {
      oauth2.refreshIfExpired();
    }

    @Override
    public AccessToken refreshAccessToken() throws IOException {
      return oauth2.refreshAccessToken();
    }

    @Override
    public Map<String, List<String>> getRequestMetadata() throws IOException {
      return oauth2.getRequestMetadata();
    }
  }
}
