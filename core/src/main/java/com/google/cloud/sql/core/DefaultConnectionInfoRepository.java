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
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class that encapsulates all logic for interacting with SQLAdmin API. */
class DefaultConnectionInfoRepository implements ConnectionInfoRepository {

  private static final String USER_PROJECT_HEADER_NAME = "X-Goog-User-Project";
  private static final Logger logger =
      LoggerFactory.getLogger(DefaultConnectionInfoRepository.class);
  private final SQLAdmin apiClient;
  private static final List<Integer> TERMINAL_STATUS_CODES = Arrays.asList(400, 401, 403, 404);

  DefaultConnectionInfoRepository(SQLAdmin apiClient) {
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
  private List<Certificate> parseCertificateChain(String cert) throws CertificateException {
    byte[] certBytes = cert.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream certStream = new ByteArrayInputStream(certBytes);
    List<Certificate> certificates = new ArrayList<>();
    while (certStream.available() > 0) {
      Certificate c = CertificateFactory.getInstance("X.509").generateCertificate(certStream);
      certificates.add(c);
    }
    return certificates;
  }

  private String generatePublicKeyCert(KeyPair keyPair) {
    // Format the public key into a PEM encoded Certificate.
    return "-----BEGIN RSA PUBLIC KEY-----\n"
        + BaseEncoding.base64().withSeparator("\n", 64).encode(keyPair.getPublic().getEncoded())
        + "\n"
        + "-----END RSA PUBLIC KEY-----\n";
  }

  /** Internal Use Only: Gets the instance data for the CloudSqlInstance from the API. */
  @Override
  public ConnectionInfo getConnectionInfoSync(
      CloudSqlInstanceName instanceName,
      AccessTokenSupplier accessTokenSupplier,
      AuthType authType,
      KeyPair keyPair) {
    Optional<AccessToken> token = null;
    try {
      token = accessTokenSupplier.get();
    } catch (IOException e) {
      throw new RuntimeException("Unable to create IAM Auth access token", e);
    }
    InstanceMetadata metadata = fetchMetadata(instanceName, authType);
    Certificate ephemeralCertificate =
        fetchEphemeralCertificate(keyPair, instanceName, token, authType);

    SslData sslContext =
        createSslData(keyPair, metadata, ephemeralCertificate, instanceName, authType);

    return createConnectionInfo(
        instanceName, authType, token, metadata, ephemeralCertificate, sslContext);
  }

  /** Internal Use Only: Gets the instance data for the CloudSqlInstance from the API. */
  @Override
  public ListenableFuture<ConnectionInfo> getConnectionInfo(
      CloudSqlInstanceName instanceName,
      AccessTokenSupplier accessTokenSupplier,
      AuthType authType,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair) {

    ListenableFuture<Optional<AccessToken>> token = executor.submit(accessTokenSupplier::get);

    // Fetch the metadata
    ListenableFuture<InstanceMetadata> metadataFuture =
        executor.submit(() -> fetchMetadata(instanceName, authType));

    // Fetch the ephemeral certificates
    ListenableFuture<Certificate> ephemeralCertificateFuture =
        Futures.whenAllComplete(keyPair, token)
            .call(
                () ->
                    fetchEphemeralCertificate(
                        Futures.getDone(keyPair), instanceName, Futures.getDone(token), authType),
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
    ListenableFuture<ConnectionInfo> done =
        Futures.whenAllComplete(metadataFuture, ephemeralCertificateFuture, sslContextFuture)
            .call(
                () ->
                    createConnectionInfo(
                        instanceName,
                        authType,
                        Futures.getDone(token),
                        Futures.getDone(metadataFuture),
                        Futures.getDone(ephemeralCertificateFuture),
                        Futures.getDone(sslContextFuture)),
                executor);

    done.addListener(
        () -> logger.debug(String.format("[%s] ALL FUTURES DONE", instanceName)), executor);
    return done;
  }

  private static ConnectionInfo createConnectionInfo(
      CloudSqlInstanceName instanceName,
      AuthType authType,
      Optional<AccessToken> token,
      InstanceMetadata metadata,
      Certificate ephemeralCertificate,
      SslData sslContext) {
    // Get expiration value for new cert
    X509Certificate x509Certificate = (X509Certificate) ephemeralCertificate;
    Instant expiration = x509Certificate.getNotAfter().toInstant();

    if (authType == AuthType.IAM) {
      expiration =
          DefaultAccessTokenSupplier.getTokenExpirationTime(token)
              .filter(
                  tokenExpiration ->
                      x509Certificate.getNotAfter().toInstant().isAfter(tokenExpiration))
              .orElse(x509Certificate.getNotAfter().toInstant());
    }

    logger.debug(
        "[{}] INSTANCE DATA DONE - Ephemeral cert id: {} cert expiration: {} token expiration: {}",
        instanceName,
        Base64.getEncoder().encodeToString(((X509Certificate) ephemeralCertificate).getSignature()),
        token
            .map(tok -> tok.getExpirationTime())
            .filter(time -> time != null)
            .map(time -> time.toInstant().toString())
            .orElse("(none)"));

    return new ConnectionInfo(metadata, sslContext, expiration);
  }

  String getApplicationName() {
    return apiClient.getApplicationName();
  }

  String getQuotaProject(String connectionName) {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName(connectionName);
    try {
      List<String> values =
          apiClient
              .connect()
              .get(instanceName.getProjectId(), instanceName.getInstanceId())
              .getRequestHeaders()
              .getHeaderStringValues(USER_PROJECT_HEADER_NAME);

      if (!values.isEmpty()) {
        return values.get(0);
      }

      return null;
    } catch (IOException ex) {
      throw addExceptionContext(ex, "[%s] Failed to get Quota Project", instanceName);
    }
  }

  /** Fetches the latest version of the instance's metadata using the Cloud SQL Admin API. */
  private InstanceMetadata fetchMetadata(CloudSqlInstanceName instanceName, AuthType authType) {
    try {
      ConnectSettings instanceMetadata =
          new ApiClientRetryingCallable<>(
                  () ->
                      apiClient
                          .connect()
                          .get(instanceName.getProjectId(), instanceName.getInstanceId())
                          .execute())
              .call();

      // Validate the instance will support the authenticated connection.
      if (!instanceMetadata.getRegion().equals(instanceName.getRegionId())) {
        throw new TerminalException(
            String.format(
                "[%s] The region specified for the Cloud SQL instance is"
                    + " incorrect. Please verify the instance connection name.",
                instanceName.getConnectionName()));
      }
      if (!instanceMetadata.getBackendType().equals("SECOND_GEN")) {
        throw new TerminalException(
            String.format(
                "[%s] Connections to Cloud SQL instance not supported - not a Second Generation "
                    + "instance.",
                instanceName.getConnectionName()));
      }

      checkDatabaseCompatibility(instanceMetadata, authType, instanceName.getConnectionName());

      Map<IpType, String> ipAddrs = new HashMap<>();
      if (instanceMetadata.getIpAddresses() != null) {
        // Update the IP addresses and types need to connect with the instance.
        for (IpMapping addr : instanceMetadata.getIpAddresses()) {
          if ("PRIVATE".equals(addr.getType())) {
            ipAddrs.put(IpType.PRIVATE, addr.getIpAddress());
          } else if ("PRIMARY".equals(addr.getType())) {
            ipAddrs.put(IpType.PUBLIC, addr.getIpAddress());
          }
          // otherwise, we don't know how to handle this type, ignore it.
        }
      }

      // If PSC is enabled, resolve DnsName into IP address for PSC
      boolean pscEnabled =
          instanceMetadata.getPscEnabled() != null
              && instanceMetadata.getPscEnabled().booleanValue();
      if (pscEnabled
          && instanceMetadata.getDnsName() != null
          && !instanceMetadata.getDnsName().isEmpty()) {
        ipAddrs.put(IpType.PSC, instanceMetadata.getDnsName());
      }

      // Verify the instance has at least one IP type assigned that can be used to connect.
      if (ipAddrs.isEmpty()) {
        throw new TerminalException(
            String.format(
                "[%s] Unable to connect to Cloud SQL instance: instance does not have an assigned "
                    + "IP address.",
                instanceName.getConnectionName()));
      }
      // Update the Server CA certificate used to create the SSL connection with the instance.
      try {
        List<Certificate> instanceCaCertificates =
            parseCertificateChain(instanceMetadata.getServerCaCert().getCert());

        logger.debug(String.format("[%s] METADATA DONE", instanceName));

        return new InstanceMetadata(
            instanceName,
            ipAddrs,
            instanceCaCertificates,
            "GOOGLE_MANAGED_CAS_CA".equals(instanceMetadata.getServerCaMode()),
            instanceMetadata.getDnsName(),
            pscEnabled);
      } catch (CertificateException ex) {
        throw new RuntimeException(
            String.format(
                "[%s] Unable to parse the server CA certificate for the Cloud SQL instance.",
                instanceName.getConnectionName()),
            ex);
      }
    } catch (Exception ex) {
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
      Optional<AccessToken> accessTokenOptional,
      AuthType authType) {

    // Use the SQL Admin API to create a new ephemeral certificate.
    GenerateEphemeralCertRequest request =
        new GenerateEphemeralCertRequest().setPublicKey(generatePublicKeyCert(keyPair));

    if (authType == AuthType.IAM && accessTokenOptional.isPresent()) {
      AccessToken accessToken = accessTokenOptional.get();

      String token = accessToken.getTokenValue();
      request.setAccessToken(token);
    }
    GenerateEphemeralCertResponse response;
    try {
      response =
          new ApiClientRetryingCallable<>(
                  () ->
                      apiClient
                          .connect()
                          .generateEphemeralCert(
                              instanceName.getProjectId(), instanceName.getInstanceId(), request)
                          .execute())
              .call();
    } catch (Exception ex) {
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
      // The response contains a single certificate. This uses the parseCertificateChain method
      // to parse the response, and then uses the first, and only, certificate.
      ephemeralCertificate = parseCertificateChain(response.getEphemeralCert().getCert()).get(0);
    } catch (CertificateException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to parse the ephemeral certificate for the Cloud SQL instance.",
              instanceName.getConnectionName()),
          ex);
    }

    logger.debug(String.format("[%s %d] CERT DONE", instanceName, Thread.currentThread().getId()));

    return ephemeralCertificate;
  }

  /**
   * Creates a new SslData based on the provided parameters. It contains a SSLContext that will be
   * used to provide new SSLSockets authorized to connect to a Cloud SQL instance. It also contains
   * a KeyManagerFactory and a TrustManagerFactory that can be used by drivers to establish an SSL
   * tunnel.
   */
  private SslData createSslData(
      KeyPair keyPair,
      InstanceMetadata instanceMetadata,
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

      TrustManagerFactory tmf = InstanceCheckingTrustManagerFactory.newInstance(instanceMetadata);

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
          logger.debug("TLSv1.3 is not supported for your Java version, fallback to TLSv1.2");
          sslContext = SSLContext.getInstance("TLSv1.2");
        }
      }
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

      logger.debug(
          String.format("[%s %d] SSL CONTEXT", instanceName, Thread.currentThread().getId()));

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
      Exception ex, String fallbackDesc, CloudSqlInstanceName instanceName) {
    String reason = fallbackDesc;
    int statusCode = 0;

    // Verify we are able to extract a reason from an exception and the status code, or fallback to
    // a generic desc
    if (ex instanceof GoogleJsonResponseException) {
      GoogleJsonResponseException gjrEx = (GoogleJsonResponseException) ex;
      reason = gjrEx.getMessage();
      statusCode = gjrEx.getStatusCode();
    } else if (ex instanceof UnknownHostException) {
      statusCode = 404;
      reason = String.format("Host \"%s\" not found", ex.getMessage());
    } else {
      Matcher matcher = Pattern.compile("Error code (\\d+)").matcher(ex.getMessage());
      reason = ex.getMessage();
      if (matcher.find()) {
        statusCode = Integer.parseInt(matcher.group(1));
      }
    }

    // Check for commonly occurring user errors and add additional context
    String message =
        String.format(
            "[%s] The Google Cloud SQL Admin API failed for the project \"%s\". Reason: %s",
            instanceName.getConnectionName(), instanceName.getProjectId(), reason);

    if (TERMINAL_STATUS_CODES.contains(statusCode)) {
      return new TerminalException(message, ex);
    }
    // Fallback to the generic description
    return new RuntimeException(message, ex);
  }
}
