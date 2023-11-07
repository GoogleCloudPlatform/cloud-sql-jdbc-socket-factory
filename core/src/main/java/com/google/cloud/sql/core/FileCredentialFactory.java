/*
 * Copyright 2023 Google LLC
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
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.CredentialFactory;
import java.io.FileInputStream;
import java.io.IOException;

class FileCredentialFactory implements CredentialFactory {
  private final String path;

  FileCredentialFactory(String path) {
    this.path = path;
  }

  @Override
  public HttpRequestInitializer create() {
    return new HttpCredentialsAdapter(getCredentials());
  }

  @Override
  public GoogleCredentials getCredentials() {
    try {
      return GoogleCredentials.fromStream(new FileInputStream(path));
    } catch (IOException e) {
      throw new IllegalStateException("Unable to load GoogleCredentials from file " + path, e);
    }
  }
}
