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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertRequest;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Class that encapsulates all logic for interacting with SQLAdmin API.
 */
public class SqlAdminApiFetcher {
  private final SQLAdmin apiClient;

  public SqlAdminApiFetcher(SQLAdmin apiClient) {
    this.apiClient = apiClient;
  }
  
  void checkDatabaseCompatibility(ConnectSettings instanceMetadata, AuthType authType,
      String connectionName) {
    if (authType == AuthType.IAM && instanceMetadata.getDatabaseVersion().contains("SQLSERVER")) {
      throw new IllegalArgumentException(
          String.format("[%s] IAM Authentication is not supported for SQL Server instances.",
              connectionName));
    }
  }

  // Creates a Certificate object from a provided string.
  private Certificate createCertificate(String cert) throws CertificateException {
    byte[] certBytes = cert.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream certStream = new ByteArrayInputStream(certBytes);
    return CertificateFactory.getInstance("X.509").generateCertificate(certStream);
  }

  private String generatePublicKeyCert(KeyPair keyPair) {
    // Format the public key into a PEM encoded Certificate.
    return "-----BEGIN RSA PUBLIC KEY-----\n"
        + BaseEncoding.base64().withSeparator("\n", 64).encode(keyPair.getPublic().getEncoded())
        + "\n"
        + "-----END RSA PUBLIC KEY-----\n";
  }

  public String getApplicationName() {
    return apiClient.getApplicationName();
  }

  /**
   * Fetches the latest version of the instance's metadata using the Cloud SQL Admin API.
   */
  Metadata fetchMetadata(CloudSqlInstanceName instanceName, AuthType authType) {
    try {
      ConnectSettings instanceMetadata = apiClient.connect()
          .get(instanceName.getProjectId(), instanceName.getInstanceId()).execute();

      // Validate the instance will support the authenticated connection.
      if (!instanceMetadata.getRegion().equals(instanceName.getRegionId())) {
        throw new IllegalArgumentException(String.format(
            "[%s] The region specified for the Cloud SQL instance is"
                + " incorrect. Please verify the instance connection name.",
            instanceName.getConnectionName()));
      }
      if (!instanceMetadata.getBackendType().equals("SECOND_GEN")) {
        throw new IllegalArgumentException(String.format(
            "[%s] Connections to Cloud SQL instance not supported - not a Second Generation "
                + "instance.", instanceName.getConnectionName()));
      }

      checkDatabaseCompatibility(instanceMetadata, authType, instanceName.getConnectionName());

      // Verify the instance has at least one IP type assigned that can be used to connect.
      if (instanceMetadata.getIpAddresses().isEmpty()) {
        throw new IllegalStateException(String.format(
            "[%s] Unable to connect to Cloud SQL instance: instance does not have an assigned "
                + "IP address.", instanceName.getConnectionName()));
      }
      // Update the IP addresses and types need to connect with the instance.
      Map<String, String> ipAddrs = new HashMap<>();
      for (IpMapping addr : instanceMetadata.getIpAddresses()) {
        ipAddrs.put(addr.getType(), addr.getIpAddress());
      }

      // Update the Server CA certificate used to create the SSL connection with the instance.
      try {
        Certificate instanceCaCertificate = createCertificate(
            instanceMetadata.getServerCaCert().getCert());
        return new Metadata(ipAddrs, instanceCaCertificate);
      } catch (CertificateException ex) {
        throw new RuntimeException(String.format(
            "[%s] Unable to parse the server CA certificate for the Cloud SQL instance.",
            instanceName.getConnectionName()), ex);
      }
    } catch (IOException ex) {
      throw addExceptionContext(ex,
          String.format("[%s] Failed to update metadata for Cloud SQL instance.",
              instanceName.getConnectionName()), instanceName);
    }
  }

  /**
   * Uses the Cloud SQL Admin API to create an ephemeral SSL certificate that is authenticated to
   * connect the Cloud SQL instance for up to 60 minutes.
   */
  Certificate fetchEphemeralCertificate(KeyPair keyPair, CloudSqlInstanceName instanceName,
      OAuth2Credentials credentials, AuthType authType) {

    // Use the SQL Admin API to create a new ephemeral certificate.
    GenerateEphemeralCertRequest request = new GenerateEphemeralCertRequest().setPublicKey(
        generatePublicKeyCert(keyPair));

    if (authType == AuthType.IAM) {
      try {
        credentials.refresh();
        String token = credentials.getAccessToken().getTokenValue();
        // TODO: remove this once issue with OAuth2 Tokens is resolved.
        // See: https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/565
        request.setAccessToken(CharMatcher.is('.').trimTrailingFrom(token));
      } catch (IOException ex) {
        throw addExceptionContext(ex, "An exception occurred while fetching IAM auth token:",
            instanceName);
      }
    }
    GenerateEphemeralCertResponse response;
    try {
      response = apiClient.connect()
          .generateEphemeralCert(instanceName.getProjectId(), instanceName.getInstanceId(), request)
          .execute();
    } catch (IOException ex) {
      throw addExceptionContext(ex,
          String.format("[%s] Failed to create ephemeral certificate for the Cloud SQL instance.",
              instanceName.getConnectionName()), instanceName);
    }

    // Parse the certificate from the response.
    Certificate ephemeralCertificate;
    try {
      ephemeralCertificate = createCertificate(response.getEphemeralCert().getCert());
    } catch (CertificateException ex) {
      throw new RuntimeException(String.format(
          "[%s] Unable to parse the ephemeral certificate for the Cloud SQL instance.",
          instanceName.getConnectionName()), ex);
    }

    return ephemeralCertificate;
  }

  /**
   * Checks for common errors that can occur when interacting with the Cloud SQL Admin API, and adds
   * additional context to help the user troubleshoot them.
   *
   * @param ex exception thrown by the Admin API request
   * @param fallbackDesc generic description used as a fallback if no additional information can be
   *     provided to the user
   */
  private RuntimeException addExceptionContext(IOException ex, String fallbackDesc,
      CloudSqlInstanceName instanceName) {
    // Verify we are able to extract a reason from an exception, or fallback to a generic desc
    GoogleJsonResponseException gjrEx =
        ex instanceof GoogleJsonResponseException ? (GoogleJsonResponseException) ex : null;
    if (gjrEx == null || gjrEx.getDetails() == null || gjrEx.getDetails().getErrors() == null
        || gjrEx.getDetails().getErrors().isEmpty()) {
      return new RuntimeException(fallbackDesc, ex);
    }
    // Check for commonly occurring user errors and add additional context
    String reason = gjrEx.getDetails().getErrors().get(0).getReason();
    if ("accessNotConfigured".equals(reason)) {
      // This error occurs when the project doesn't have the "Cloud SQL Admin API" enabled
      String apiLink = "https://console.cloud.google.com/apis/api/sqladmin/overview?project="
          + instanceName.getProjectId();
      return new RuntimeException(String.format(
          "[%s] The Google Cloud SQL Admin API is not enabled for the project \"%s\". Please "
              + "use the Google Developers Console to enable it: %s",
          instanceName.getConnectionName(), instanceName.getProjectId(), apiLink), ex);
    } else if ("notAuthorized".equals(reason)) {
      // This error occurs if the instance doesn't exist or the account isn't authorized
      // TODO(kvg): Add credential account name to error string.
      return new RuntimeException(String.format(
          "[%s] The Cloud SQL Instance does not exist or your account is not authorized to "
              + "access it. Please verify the instance connection name and check the IAM "
              + "permissions for project \"%s\" ", instanceName.getConnectionName(),
          instanceName.getProjectId()), ex);
    }
    // Fallback to the generic description
    return new RuntimeException(fallbackDesc, ex);
  }
}
