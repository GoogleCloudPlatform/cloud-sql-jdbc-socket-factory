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

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.cloud.sql.ConnectionConfig;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Factory for creating a SQLAdmin client that interacts with the real SQL Admin API. */
public class DefaultConnectionInfoRepositoryFactory implements ConnectionInfoRepositoryFactory {
  private final String userAgents;

  /**
   * Initializes a new SQLAdminApiClientFactory class from defaults and provided userAgents.
   *
   * @param userAgents string representing userAgents for the admin API client
   */
  public DefaultConnectionInfoRepositoryFactory(String userAgents) {
    this.userAgents = userAgents;
  }

  @Override
  public DefaultConnectionInfoRepository create(
      HttpRequestInitializer requestInitializer, ConnectionConfig config) {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException err) {
      throw new RuntimeException("Unable to initialize HTTP transport", err);
    }

    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    SQLAdmin.Builder adminApiBuilder =
        new SQLAdmin.Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(userAgents);
    if (config.getAdminRootUrl() != null) {
      adminApiBuilder.setRootUrl(config.getAdminRootUrl());
    }
    if (config.getAdminServicePath() != null) {
      adminApiBuilder.setServicePath(config.getAdminServicePath());
    }
    return new DefaultConnectionInfoRepository(adminApiBuilder.build());
  }
}
