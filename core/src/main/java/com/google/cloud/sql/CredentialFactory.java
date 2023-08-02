/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.cloud.sql;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.Date;

/** Factory for creating {@link Credential}s for interaction with Cloud SQL Admin API. */
public interface CredentialFactory {

  /** Name of system property that can specify an alternative credential factory. */
  String CREDENTIAL_FACTORY_PROPERTY = "cloudSql.socketFactory.credentialFactory";

  HttpRequestInitializer create();

  /**
   * Returns a GoogleCredentials to use for generating tokens and authenticating web requests. The
   * default implementation will attempt to extract the GoogleCredentials object from known
   * credential HttpRequestInitializer implementations already in used to provide tokens for IAM
   * Authentication.
   */
  default GoogleCredentials getCredentials() {
    HttpRequestInitializer requestInitializer = this.create();
    if (requestInitializer instanceof HttpCredentialsAdapter) {
      HttpCredentialsAdapter adapter = (HttpCredentialsAdapter) requestInitializer;
      Credentials c = adapter.getCredentials();
      if (c instanceof GoogleCredentials) {
        return (GoogleCredentials) c;
      }
      throw new RuntimeException(
          String.format(
              "Unable to determine GoogleCredential from HttpRequestInitializer: "
                  + "HttpCredentialsAdapter did not create valid credentials. %s, %s",
              requestInitializer.getClass().getName(), c));
    }

    if (requestInitializer instanceof Credential) {
      Credential credential = (Credential) requestInitializer;
      AccessToken accessToken =
          new AccessToken(
              credential.getAccessToken(),
              credential.getExpirationTimeMilliseconds() != null
                  ? new Date(credential.getExpirationTimeMilliseconds())
                  : null);

      return new GoogleCredentials(accessToken) {
        @Override
        public AccessToken refreshAccessToken() throws IOException {
          credential.refreshToken();

          return new AccessToken(
              credential.getAccessToken(),
              credential.getExpirationTimeMilliseconds() != null
                  ? new Date(credential.getExpirationTimeMilliseconds())
                  : null);
        }
      };
    }

    throw new RuntimeException(
        String.format(
            "Unable to determine GoogleCredential from HttpRequestInitializer: "
                + "Unsupported credentials of type %s",
            requestInitializer.getClass().getName()));
  }
}
