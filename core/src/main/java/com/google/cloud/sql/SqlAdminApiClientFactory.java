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

package com.google.cloud.sql;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;
import java.util.logging.Logger;

public class SqlAdminApiClientFactory implements ApiClientFactory {

  private static final Logger logger = Logger.getLogger(SqlAdminApiClientFactory.class.getName());
  // Test properties, not for end-user use. May be changed or removed without notice.
  private static final String API_ROOT_URL_PROPERTY = "_CLOUD_SQL_API_ROOT_URL";
  private static final String API_SERVICE_PATH_PROPERTY = "_CLOUD_SQL_API_SERVICE_PATH";
  Optional<String> userAgents;

  public SqlAdminApiClientFactory(Optional<String> userAgents) {
    this.userAgents = userAgents;
  }

  private static void logTestPropertyWarning(String property) {
    logger.warning(String.format(
        "%s is a test property and may be changed or removed in a future version without "
            + "notice.", property));
  }

  @Override
  public SQLAdmin create(HttpRequestInitializer requestInitializer) {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException err) {
      throw new RuntimeException("Unable to initialize HTTP transport", err);
    }

    String rootUrl = System.getProperty(API_ROOT_URL_PROPERTY);
    String servicePath = System.getProperty(API_SERVICE_PATH_PROPERTY);

    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    SQLAdmin.Builder adminApiBuilder = new SQLAdmin.Builder(httpTransport, jsonFactory,
        requestInitializer).setApplicationName(userAgents.get());
    if (rootUrl != null) {
      logTestPropertyWarning(API_ROOT_URL_PROPERTY);
      adminApiBuilder.setRootUrl(rootUrl);
    }
    if (servicePath != null) {
      logTestPropertyWarning(API_SERVICE_PATH_PROPERTY);
      adminApiBuilder.setServicePath(servicePath);
    }
    return adminApiBuilder.build();
  }

}