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
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.cloud.sql.AuthType;
import com.google.common.base.CharMatcher;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/** Class that encapsulates all logic for interacting with SQLAdmin API. */
public class SqlAdminApiFetcher {

  private static final Logger logger = Logger.getLogger(SqlAdminApiFetcher.class.getName());
  private final SQLAdmin apiClient;

  public SqlAdminApiFetcher(SQLAdmin apiClient) {
    this.apiClient = apiClient;
  }

  private void checkDatabaseCompatibility(
      ConnectSettings instanceMetadata, AuthType authType, String connectionName) {
    if (authType == AuthType.IAM && instanceMetadata.getDatabaseVersion().contains("SQLSERVER")) {
      throw new IllegalArgumentException(
          String.format(
              "[%s] IAM Authentication is not supported for SQL Server instances.",
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

  InstanceData getInstanceData(
      CloudSqlInstanceName instanceName,
      OAuth2Credentials credentials,
      AuthType authType,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair)
      throws ExecutionException, InterruptedException {

    // Fetch the metadata
    ListenableFuture<Metadata> metadataFuture =
        executor.submit(() -> fetchMetadata(instanceName, authType));

    // Fetch the ephemeral certificates
    ListenableFuture<Certificate> ephemeralCertificateFuture =
        Futures.whenAllComplete(keyPair)
            .call(
                () ->
                    fetchEphemeralCertificate(
                        Futures.getDone(keyPair), instanceName, credentials, authType),
                executor);

    // Once the API calls are complete, construct the SSLContext for the sockets
    ListenableFuture<SslData> sslContextFuture =
        Futures.whenAllComplete(metadataFuture, ephemeralCertificateFuture)
            .call(
                () ->
                    createSslData(
                        Futures.getDone(keyPair),
                        Futures.getDone(metadataFuture),
                        Futures.getDone(ephemeralCertificateFuture),
                        instanceName,
                        authType),
                executor);

    // Once both the SSLContext and Metadata are complete, return the results
    ListenableFuture<InstanceData> done =
        Futures.whenAllComplete(metadataFuture, ephemeralCertificateFuture, sslContextFuture)
            .call(
                () -> {

                  // Get expiration value for new cert
                  Certificate ephemeralCertificate = Futures.getDone(ephemeralCertificateFuture);
                  X509Certificate x509Certificate = (X509Certificate) ephemeralCertificate;
                  Date expiration = x509Certificate.getNotAfter();

                  if (authType == AuthType.IAM) {
                    expiration =
                        getTokenExpirationTime(credentials)
                            .filter(
                                tokenExpiration ->
                                    x509Certificate.getNotAfter().after(tokenExpiration))
                            .orElse(x509Certificate.getNotAfter());
                  }

                  return new InstanceData(
                      Futures.getDone(metadataFuture),
                      Futures.getDone(sslContextFuture),
                      expiration);
                },
                executor);

    return done.get();
  }

  private Optional<Date> getTokenExpirationTime(OAuth2Credentials credentials) {
    return Optional.ofNullable(credentials.getAccessToken().getExpirationTime());
  }

  public String getApplicationName() {
    return apiClient.getApplicationName();
  }

  /** Fetches the latest version of the instance's metadata using the Cloud SQL Admin API. */
  private Metadata fetchMetadata(CloudSqlInstanceName instanceName, AuthType authType) {
    try {
      ConnectSettings instanceMetadata =
          apiClient
              .connect()
              .get(instanceName.getProjectId(), instanceName.getInstanceId())
              .execute();

      // Validate the instance will support the authenticated connection.
      if (!instanceMetadata.getRegion().equals(instanceName.getRegionId())) {
        throw new IllegalArgumentException(
            String.format(
                "[%s] The region specified for the Cloud SQL instance is"
                    + " incorrect. Please verify the instance connection name.",
                instanceName.getConnectionName()));
      }
      if (!instanceMetadata.getBackendType().equals("SECOND_GEN")) {
        throw new IllegalArgumentException(
            String.format(
                "[%s] Connections to Cloud SQL instance not supported - not a Second Generation "
                    + "instance.",
                instanceName.getConnectionName()));
      }

      checkDatabaseCompatibility(instanceMetadata, authType, instanceName.getConnectionName());

      // Verify the instance has at least one IP type assigned that can be used to connect.
      if (instanceMetadata.getIpAddresses().isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "[%s] Unable to connect to Cloud SQL instance: instance does not have an assigned "
                    + "IP address.",
                instanceName.getConnectionName()));
      }
      // Update the IP addresses and types need to connect with the instance.
      Map<String, String> ipAddrs = new HashMap<>();
      for (IpMapping addr : instanceMetadata.getIpAddresses()) {
        ipAddrs.put(addr.getType(), addr.getIpAddress());
      }

      // Update the Server CA certificate used to create the SSL connection with the instance.
      try {
        Certificate instanceCaCertificate =
            createCertificate(instanceMetadata.getServerCaCert().getCert());
        return new Metadata(ipAddrs, instanceCaCertificate);
      } catch (CertificateException ex) {
        throw new RuntimeException(
            String.format(
                "[%s] Unable to parse the server CA certificate for the Cloud SQL instance.",
                instanceName.getConnectionName()),
            ex);
      }
    } catch (IOException ex) {
      throw addExceptionContext(
          ex,
          String.format(
              "[%s] Failed to update metadata for Cloud SQL instance.",
              instanceName.getConnectionName()),
          instanceName);
    }
  }

  /**
   * Uses the Cloud SQL Admin API to create an ephemeral SSL certificate that is authenticated to
   * connect the Cloud SQL instance for up to 60 minutes.
   */
  private Certificate fetchEphemeralCertificate(
      KeyPair keyPair,
      CloudSqlInstanceName instanceName,
      OAuth2Credentials credentials,
      AuthType authType) {

    // Use the SQL Admin API to create a new ephemeral certificate.
    GenerateEphemeralCertRequest request =
        new GenerateEphemeralCertRequest().setPublicKey(generatePublicKeyCert(keyPair));

    if (authType == AuthType.IAM) {
      try {
        refreshWithRetry(credentials);
        AccessToken accessToken = credentials.getAccessToken();

        validateAccessToken(accessToken);

        String token = accessToken.getTokenValue();
        // TODO: remove this once issue with OAuth2 Tokens is resolved.
        // See: https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/565
        request.setAccessToken(CharMatcher.is('.').trimTrailingFrom(token));
      } catch (IOException ex) {
        throw addExceptionContext(
            ex, "An exception occurred while fetching IAM auth token:", instanceName);
      }
    }
    GenerateEphemeralCertResponse response;
    try {
      response =
          apiClient
              .connect()
              .generateEphemeralCert(
                  instanceName.getProjectId(), instanceName.getInstanceId(), request)
              .execute();
    } catch (IOException ex) {
      throw addExceptionContext(
          ex,
          String.format(
              "[%s] Failed to create ephemeral certificate for the Cloud SQL instance.",
              instanceName.getConnectionName()),
          instanceName);
    }

    // Parse the certificate from the response.
    Certificate ephemeralCertificate;
    try {
      ephemeralCertificate = createCertificate(response.getEphemeralCert().getCert());
    } catch (CertificateException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to parse the ephemeral certificate for the Cloud SQL instance.",
              instanceName.getConnectionName()),
          ex);
    }

    return ephemeralCertificate;
  }

  /**
   * refreshWithRetry attempts to refresh the credentials 3 times, waiting 3-6 seconds between
   * attempts.
   *
   * @param credentials the credentials to refresh
   * @throws IOException when the credentials.refresh() has failed 3 times
   */
  private void refreshWithRetry(OAuth2Credentials credentials) throws IOException {
    Callable<OAuth2Credentials> refresh =
        () -> {
          try {
            credentials.refresh();
          } catch (IllegalStateException e) {
            throw new IllegalStateException(
                String.format(
                    "Illegal state while attempting to refresh credentials %s, %s %s",
                    credentials.getClass().getName(), e.getMessage(), credentials.toString()), e);
          }
          return credentials;
        };

    RetryingCallable<OAuth2Credentials> c =
        new RetryingCallable<>(refresh, 3, Duration.ofSeconds(3));
    try {
      c.call();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(
          "Unexpected exception while attempting to refresh OAuth2 credentials", e);
    }
  }

  private void validateAccessToken(AccessToken accessToken) {
    Date expirationTimeDate = accessToken.getExpirationTime();
    String tokenValue = accessToken.getTokenValue();

    if (expirationTimeDate != null) {
      Instant expirationTime = expirationTimeDate.toInstant();
      Instant now = Instant.now();

      // Is the token expired?
      if (expirationTime.isBefore(now) || expirationTime.equals(now)) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));
        String nowFormat = formatter.format(now);
        String expirationFormat = formatter.format(expirationTime);
        String errorMessage =
            "Access Token expiration time is in the past. Now = "
                + nowFormat
                + " Expiration = "
                + expirationFormat;
        logger.warning(errorMessage);
        throw new RuntimeException(errorMessage);
      }
    }

    // Is the token empty?
    if (tokenValue.length() == 0) {
      String errorMessage = "Access Token has length of zero";
      logger.warning(errorMessage);
      throw new RuntimeException(errorMessage);
    }
  }

  /**
   * Creates a new SslData based on the provided parameters. It contains a SSLContext that will be
   * used to provide new SSLSockets authorized to connect to a Cloud SQL instance. It also contains
   * a KeyManagerFactory and a TrustManagerFactory that can be used by drivers to establish an SSL
   * tunnel.
   */
  private SslData createSslData(
      KeyPair keyPair,
      Metadata metadata,
      Certificate ephemeralCertificate,
      CloudSqlInstanceName instanceName,
      AuthType authType) {
    try {
      KeyStore authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      authKeyStore.load(null, null);
      KeyStore.PrivateKeyEntry privateKey =
          new PrivateKeyEntry(keyPair.getPrivate(), new Certificate[] {ephemeralCertificate});
      authKeyStore.setEntry("ephemeral", privateKey, new PasswordProtection(new char[0]));
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(authKeyStore, new char[0]);

      KeyStore trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustedKeyStore.load(null, null);
      trustedKeyStore.setCertificateEntry("instance", metadata.getInstanceCaCertificate());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
      tmf.init(trustedKeyStore);
      SSLContext sslContext;

      try {
        sslContext = SSLContext.getInstance("TLSv1.3");
      } catch (NoSuchAlgorithmException ex) {
        if (authType == AuthType.IAM) {
          throw new RuntimeException(
              String.format(
                      "[%s] Unable to create a SSLContext for the Cloud SQL instance.",
                      instanceName.getConnectionName())
                  + " TLSv1.3 is not supported for your Java version and is required to connect"
                  + " using IAM authentication",
              ex);
        } else {
          logger.warning("TLSv1.3 is not supported for your Java version, fallback to TLSv1.2");
          sslContext = SSLContext.getInstance("TLSv1.2");
        }
      }

      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

      return new SslData(sslContext, kmf, tmf);
    } catch (GeneralSecurityException | IOException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to create a SSLContext for the Cloud SQL instance.",
              instanceName.getConnectionName()),
          ex);
    }
  }

  /**
   * Checks for common errors that can occur when interacting with the Cloud SQL Admin API, and adds
   * additional context to help the user troubleshoot them.
   *
   * @param ex exception thrown by the Admin API request
   * @param fallbackDesc generic description used as a fallback if no additional information can be
   *     provided to the user
   */
  private RuntimeException addExceptionContext(
      IOException ex, String fallbackDesc, CloudSqlInstanceName instanceName) {
    // Verify we are able to extract a reason from an exception, or fallback to a generic desc
    GoogleJsonResponseException gjrEx =
        ex instanceof GoogleJsonResponseException ? (GoogleJsonResponseException) ex : null;
    if (gjrEx == null
        || gjrEx.getDetails() == null
        || gjrEx.getDetails().getErrors() == null
        || gjrEx.getDetails().getErrors().isEmpty()) {
      return new RuntimeException(fallbackDesc, ex);
    }
    // Check for commonly occurring user errors and add additional context
    String reason = gjrEx.getDetails().getErrors().get(0).getReason();
    if ("accessNotConfigured".equals(reason)) {
      // This error occurs when the project doesn't have the "Cloud SQL Admin API" enabled
      String apiLink =
          "https://console.cloud.google.com/apis/api/sqladmin/overview?project="
              + instanceName.getProjectId();
      return new RuntimeException(
          String.format(
              "[%s] The Google Cloud SQL Admin API is not enabled for the project \"%s\". Please "
                  + "use the Google Developers Console to enable it: %s",
              instanceName.getConnectionName(), instanceName.getProjectId(), apiLink),
          ex);
    } else if ("notAuthorized".equals(reason)) {
      // This error occurs if the instance doesn't exist or the account isn't authorized
      // TODO(kvg): Add credential account name to error string.
      return new RuntimeException(
          String.format(
              "[%s] The Cloud SQL Instance does not exist or your account is not authorized to "
                  + "access it. Please verify the instance connection name and check the IAM "
                  + "permissions for project \"%s\" ",
              instanceName.getConnectionName(), instanceName.getProjectId()),
          ex);
    }
    // Fallback to the generic description
    return new RuntimeException(fallbackDesc, ex);
  }
}
