/*
 * Copyright 2022 Google Inc. All Rights Reserved.
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

package com.google.cloud.sql.core;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.io.IOException;
import java.util.Arrays;

class ApplicationDefaultCredentialFactory implements CredentialFactory {

  @Override
  public HttpRequestInitializer create() {
    GoogleCredentials credentials;
    try {
      credentials = GoogleCredentials.getApplicationDefault();
    } catch (IOException err) {
      throw new RuntimeException(
          "Unable to obtain credentials to communicate with the Cloud SQL API", err);
    }
    if (credentials.createScopedRequired()) {
      credentials =
          credentials.createScoped(Arrays.asList(
              SQLAdminScopes.SQLSERVICE_ADMIN,
              SQLAdminScopes.CLOUD_PLATFORM)
          );
    }
    return new HttpCredentialsAdapter(credentials);
  }
}
