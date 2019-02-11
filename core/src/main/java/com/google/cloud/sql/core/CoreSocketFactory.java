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

package com.google.cloud.sql.core;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdmin.Builder;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.SslCertsCreateEphemeralRequest;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.RateLimiter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Factory responsible for obtaining an ephemeral certificate, if necessary, and establishing a
 * secure connecting to a Cloud SQL instance.
 *
 * <p>This class should not be used directly, but only through the JDBC driver specific {@code
 * SocketFactory} implementations.
 *
 * <p>The API of this class is subject to change without notice.
 */
public class CoreSocketFactory {
  private static final Logger logger = Logger.getLogger(CoreSocketFactory.class.getName());

  public static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";
  public static final String USER_TOKEN_PROPERTY_NAME = "_CLOUD_SQL_USER_TOKEN";

  static final String ADMIN_API_NOT_ENABLED_REASON = "accessNotConfigured";
  static final String INSTANCE_NOT_AUTHORIZED_REASON = "notAuthorized";

  // Test properties, not for end-user use. May be changed or removed without notice.
  private static final String API_ROOT_URL_PROPERTY = "_CLOUD_SQL_API_ROOT_URL";
  private static final String API_SERVICE_PATH_PROPERTY = "_CLOUD_SQL_API_SERVICE_PATH";

  private static final int DEFAULT_SERVER_PROXY_PORT = 3307;
  private static final int RSA_KEY_SIZE = 2048;

  private static CoreSocketFactory sslSocketFactory;

  private final CertificateFactory certificateFactory;
  private final Clock clock;
  private final KeyPair localKeyPair;
  private final Credential credential;
  private final Map<String, InstanceLookupResult> cache = new HashMap<>();
  private final SQLAdmin adminApi;
  private final int serverProxyPort;
  // Protection from attempting to renew ephemeral certificate too often in case of handshake
  // error. Allow forced renewal once a minute.
  private final RateLimiter forcedRenewRateLimiter = RateLimiter.create(1.0 / 60.0);

  @VisibleForTesting
  CoreSocketFactory(
      Clock clock,
      KeyPair localKeyPair,
      Credential credential,
      SQLAdmin adminApi,
      int serverProxyPort) {
    try {
      this.certificateFactory = CertificateFactory.getInstance("X.509");
    } catch (CertificateException err) {
      throw new RuntimeException("X509 implementation not available", err);
    }
    this.clock = clock;
    this.localKeyPair = localKeyPair;
    this.credential = credential;
    this.adminApi = adminApi;
    this.serverProxyPort = serverProxyPort;
  }

  /**
   * Returns the CoreSocketFactory singleton, which can be used to create SslSockets to Cloud SQL.
   *
   * @return the CoreSocketFactory singleton.
   */
  public static synchronized CoreSocketFactory getInstance() {
    if (sslSocketFactory == null) {
      logger.info("First Cloud SQL connection, generating RSA key pair.");
      KeyPair keyPair = generateRsaKeyPair();
      CredentialFactory credentialFactory;
      if (System.getProperty(CredentialFactory.CREDENTIAL_FACTORY_PROPERTY) != null) {
        try {
          credentialFactory =
              (CredentialFactory)
                  Class.forName(System.getProperty(CredentialFactory.CREDENTIAL_FACTORY_PROPERTY))
                      .newInstance();
        } catch (Exception err) {
          throw new RuntimeException(err);
        }
      } else {
        credentialFactory = new ApplicationDefaultCredentialFactory();
      }

      Credential credential = credentialFactory.create();

      SQLAdmin adminApi = createAdminApiClient(credential);
      sslSocketFactory =
          new CoreSocketFactory(
              new Clock(), keyPair, credential, adminApi, DEFAULT_SERVER_PROXY_PORT);
    }
    return sslSocketFactory;
  }

  /**
   * Creates a secure socket representing a connection to a Cloud SQL instance.
   *
   * @param instanceName Name of the Cloud SQL instance.
   * @param ipTypes Preferred type of IP to use ("PRIVATE", "PUBLIC")
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  // TODO(berezv): separate creating socket and performing connection to make it easier to test
  public Socket create(String instanceName, List<String> ipTypes) throws IOException {
    try {
      return createAndConfigureSocket(instanceName, ipTypes, CertificateCaching.USE_CACHE);
    } catch (SSLHandshakeException err) {
      logger.warning(
          String.format(
              "SSL handshake failed for Cloud SQL instance [%s], "
                  + "retrying with new certificate.\n%s",
              instanceName, Throwables.getStackTraceAsString(err)));

      if (!forcedRenewRateLimiter.tryAcquire()) {
        logger.warning(
            String.format(
                "Renewing too often, rate limiting certificate renewal for Cloud SQL "
                    + "instance [%s].",
                instanceName));
        forcedRenewRateLimiter.acquire();
      }
      return createAndConfigureSocket(instanceName, ipTypes, CertificateCaching.BYPASS_CACHE);
    }
  }

  private static void logTestPropertyWarning(String property) {
    logger.warning(
        String.format(
            "%s is a test property and may be changed or removed in a future version without "
                + "notice.",
            property));
  }

  private SSLSocket createAndConfigureSocket(
      String instanceName, List<String> ipTypes, CertificateCaching certificateCaching)
      throws IOException {
    InstanceSslInfo instanceSslInfo = getInstanceSslInfo(instanceName, certificateCaching);
    String ipAddress = getPreferredIp(instanceName, ipTypes, instanceSslInfo);

    logger.info(
        String.format(
            "Connecting to Cloud SQL instance [%s] on IP [%s].", instanceName, ipAddress));
    SSLSocket sslSocket =
        (SSLSocket) instanceSslInfo.getSslSocketFactory().createSocket(ipAddress, serverProxyPort);

    // TODO(berezv): Support all socket related options listed here:
    // https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html
    // TODO(berezv): Make sure we have appropriate timeout for establishing connection.
    sslSocket.setKeepAlive(true);
    sslSocket.setTcpNoDelay(true);

    sslSocket.startHandshake();
    return sslSocket;
  }

  /**
   * Converts the string property of IP types to a list by splitting by commas, and upper-casing.
   */
  public static List<String> listIpTypes(String cloudSqlIpTypes) {
    String[] rawTypes = cloudSqlIpTypes.split(",");
    ArrayList<String> result = new ArrayList<>(rawTypes.length);
    for (int i = 0; i < rawTypes.length; i++) {
      if (rawTypes[i].trim().equalsIgnoreCase("PUBLIC")) {
        result.add(i, "PRIMARY");
      } else {
        result.add(i, rawTypes[i].trim().toUpperCase());
      }
    }
    return result;
  }

  @Nullable
  private String getPreferredIp(
      String instanceName, List<String> ipTypes, InstanceSslInfo instanceSslInfo) {
    String ipAddress = null;
    for (String ipType : ipTypes) {
      ipAddress = instanceSslInfo.getInstanceIpAddress(ipType);
      if (ipAddress != null) {
        break;
      }
    }

    if (ipAddress == null) {
      throw new RuntimeException(
          String.format(
              "Cloud SQL instance [%s] does not have any IP addresses matching preference: [ %s ]",
              instanceName, String.join(", ", ipTypes)));
    }

    return ipAddress;
  }

  // TODO(berezv): synchronize per instance, instead of globally
  @VisibleForTesting
  synchronized InstanceSslInfo getInstanceSslInfo(
      String instanceConnectionString, CertificateCaching certificateCaching) {

    if (certificateCaching.equals(CertificateCaching.USE_CACHE)) {
      InstanceLookupResult lookupResult = cache.get(instanceConnectionString);
      if (lookupResult != null) {
        if (!lookupResult.isSuccessful()
            && (clock.now() - lookupResult.getLastFailureMillis()) < 60 * 1000) {
          logger.warning(
              "Re-throwing cached exception due to attempt to refresh instance information too "
                  + "soon after error.");
          throw lookupResult.getException().get();
        } else if (lookupResult.isSuccessful()) {
          InstanceSslInfo details = lookupResult.getInstanceSslInfo().get();
          // Check if the cached certificate is still valid.
          if (details != null) {
            GregorianCalendar calendar = new GregorianCalendar();
            calendar.setTimeInMillis(clock.now());
            calendar.add(Calendar.MINUTE, 5);
            try {
              details.getEphemeralCertificate().checkValidity(calendar.getTime());
            } catch (CertificateException err) {
              logger.info(
                  String.format(
                      "Ephemeral certificate for Cloud SQL instance [%s] is about to expire, "
                          + "obtaining new one.",
                      instanceConnectionString));
              details = null;
            }
          }

          if (details != null) {
            return details;
          }
        }
      }
    }

    String invalidInstanceError =
        String.format(
            "Invalid Cloud SQL instance [%s], expected value in form [project:region:name].",
            instanceConnectionString);

    int beforeNameIndex = instanceConnectionString.lastIndexOf(':');
    if (beforeNameIndex <= 0) {
      throw new IllegalArgumentException(invalidInstanceError);
    }

    int beforeRegionIndex = instanceConnectionString.lastIndexOf(':', beforeNameIndex - 1);
    if (beforeRegionIndex <= 0) {
      throw new IllegalArgumentException(invalidInstanceError);
    }

    String projectId = instanceConnectionString.substring(0, beforeRegionIndex);
    String region = instanceConnectionString.substring(beforeRegionIndex + 1, beforeNameIndex);
    String instanceName = instanceConnectionString.substring(beforeNameIndex + 1);

    InstanceLookupResult instanceLookupResult;
    InstanceSslInfo details;
    try {
      details = fetchInstanceSslInfo(instanceConnectionString, projectId, region, instanceName);
      instanceLookupResult = new InstanceLookupResult(details);
      cache.put(instanceConnectionString, instanceLookupResult);
    } catch (RuntimeException err) {
      instanceLookupResult = new InstanceLookupResult(err);
      cache.put(instanceConnectionString, instanceLookupResult);
      throw err;
    }

    return details;
  }

  private InstanceSslInfo fetchInstanceSslInfo(
      String instanceConnectionString, String projectId, String region, String instanceName) {
    logger.info(
        String.format(
            "Obtaining ephemeral certificate for Cloud SQL instance [%s].",
            instanceConnectionString));

    DatabaseInstance instance =
        obtainInstanceMetadata(adminApi, instanceConnectionString, projectId, instanceName);
    if (instance.getIpAddresses().isEmpty()) {
      throw new RuntimeException(
          String.format(
              "Cloud SQL instance [%s] does not have any IP addresses", instanceConnectionString));
    }
    if (!instance.getRegion().equals(region)) {
      throw new IllegalArgumentException(
          String.format(
              "Incorrect region value [%s] for Cloud SQL instance [%s], should be [%s]",
              region, instanceConnectionString, instance.getRegion()));
    }

    X509Certificate ephemeralCertificate =
        obtainEphemeralCertificate(adminApi, instanceConnectionString, projectId, instanceName);

    Certificate instanceCaCertificate;
    try {
      instanceCaCertificate =
          certificateFactory.generateCertificate(
              new ByteArrayInputStream(
                  instance.getServerCaCert().getCert().getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException err) {
      throw new RuntimeException(
          String.format(
              "Unable to parse certificate for Cloud SQL instance [%s]", instanceConnectionString),
          err);
    }

    SSLContext sslContext = createSslContext(ephemeralCertificate, instanceCaCertificate);

    InstanceSslInfo info = new InstanceSslInfo(ephemeralCertificate, sslContext.getSocketFactory());

    for (IpMapping ip : instance.getIpAddresses()) {
      info.putInstanceIpAddress(ip.getType(), ip.getIpAddress());
    }

    return info;
  }

  private SSLContext createSslContext(
      Certificate ephemeralCertificate, Certificate instanceCaCertificate) {
    KeyStore authKeyStore;
    try {
      authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      authKeyStore.load(null, null);
      KeyStore.PrivateKeyEntry pk =
          new KeyStore.PrivateKeyEntry(
              localKeyPair.getPrivate(), new Certificate[] {ephemeralCertificate});
      authKeyStore.setEntry("ephemeral", pk, new PasswordProtection(new char[0]));
    } catch (GeneralSecurityException | IOException err) {
      throw new RuntimeException("There was a problem initializing the auth key store", err);
    }

    KeyStore trustKeyStore;
    try {
      trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustKeyStore.load(null, null);
      trustKeyStore.setCertificateEntry("instance", instanceCaCertificate);
    } catch (GeneralSecurityException | IOException err) {
      throw new RuntimeException("There was a problem initializing the trust key store", err);
    }

    try {
      SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      KeyManagerFactory keyManagerFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagerFactory.init(authKeyStore, new char[0]);
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
      tmf.init(trustKeyStore);
      sslContext.init(
          keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
      return sslContext;
    } catch (GeneralSecurityException err) {
      throw new RuntimeException("There was a problem initializing the SSL context", err);
    }
  }

  private DatabaseInstance obtainInstanceMetadata(
      SQLAdmin adminApi, String instanceConnectionString, String projectId, String instanceName) {
    DatabaseInstance instance;
    try {
      instance = adminApi.instances().get(projectId, instanceName).execute();
    } catch (GoogleJsonResponseException err) {
      if (err.getDetails() == null || err.getDetails().getErrors().isEmpty()) {
        throw new RuntimeException(
            String.format(
                "Unable to retrieve information about Cloud SQL instance [%s]",
                instanceConnectionString),
            err);
      }

      String reason = err.getDetails().getErrors().get(0).getReason();
      if (ADMIN_API_NOT_ENABLED_REASON.equals(reason)) {
        String apiLink =
            "https://console.cloud.google.com/apis/api/sqladmin/overview?project=" + projectId;
        throw new RuntimeException(
            String.format(
                "The Google Cloud SQL API is not enabled for project [%s]. Please "
                    + "use the Google Developers Console to enable it: %s",
                projectId, apiLink));
      } else if (INSTANCE_NOT_AUTHORIZED_REASON.equals(reason)) {
        // TODO(berezv): check if this works on Compute Engine / App Engine
        String who = "you are";
        if (getCredentialServiceAccount(credential) != null) {
          who = "[" + getCredentialServiceAccount(credential) + "] is";
        }
        throw new RuntimeException(
            String.format(
                "Cloud SQL Instance [%s] does not exist or %s not authorized to "
                    + "access it. Please check the instance and project names to make "
                    + "sure they are correct.",
                instanceConnectionString, who));
      } else {
        throw new RuntimeException(
            String.format(
                "Unable to retrieve information about Cloud SQL instance [%s]",
                instanceConnectionString),
            err);
      }
    } catch (IOException err) {
      throw new RuntimeException(
          String.format(
              "Unable to retrieve information about Cloud SQL instance [%s]",
              instanceConnectionString),
          err);
    }

    if (!instance.getBackendType().equals("SECOND_GEN")) {
      throw new IllegalArgumentException(
          "This client only supports connections to Second Generation Cloud SQL " + "instances");
    }

    return instance;
  }

  private X509Certificate obtainEphemeralCertificate(
      SQLAdmin adminApi, String instanceConnectionString, String projectId, String instanceName) {

    StringBuilder publicKeyPemBuilder = new StringBuilder();
    publicKeyPemBuilder.append("-----BEGIN RSA PUBLIC KEY-----\n");
    publicKeyPemBuilder.append(
        Base64.getEncoder()
            .encodeToString(localKeyPair.getPublic().getEncoded())
            .replaceAll("(.{64})", "$1\n"));
    publicKeyPemBuilder.append("\n");
    publicKeyPemBuilder.append("-----END RSA PUBLIC KEY-----\n");

    SslCertsCreateEphemeralRequest req = new SslCertsCreateEphemeralRequest();
    req.setPublicKey(publicKeyPemBuilder.toString());

    SslCert response;
    try {
      response = adminApi.sslCerts().createEphemeral(projectId, instanceName, req).execute();
    } catch (GoogleJsonResponseException err) {
      if (err.getDetails() == null || err.getDetails().getErrors().isEmpty()) {
        throw new RuntimeException(
            String.format(
                "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
                instanceConnectionString),
            err);
      }

      String reason = err.getDetails().getErrors().get(0).getReason();
      if (INSTANCE_NOT_AUTHORIZED_REASON.equals(reason)) {
        String who = "you have";
        if (getCredentialServiceAccount(credential) != null) {
          who = "[" + getCredentialServiceAccount(credential) + "] has";
        }
        throw new RuntimeException(
            String.format(
                "Unable to obtain ephemeral certificate for Cloud SQL Instance [%s]. "
                    + "Ensure %s the sql.instances.connect permission "
                    + "(included in Cloud SQL Client role, or primitive Editor or Owner "
                    + "roles).",
                instanceConnectionString, who));
      } else {
        throw new RuntimeException(
            String.format(
                "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
                instanceConnectionString),
            err);
      }
    } catch (IOException err) {
      throw new RuntimeException(
          String.format(
              "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
              instanceConnectionString),
          err);
    }

    try {
      return (X509Certificate)
          certificateFactory.generateCertificate(
              new ByteArrayInputStream(response.getCert().getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException err) {
      throw new RuntimeException(
          String.format(
              "Unable to parse ephemeral certificate for Cloud SQL instance [%s]",
              instanceConnectionString),
          err);
    }
  }

  @Nullable
  private String getCredentialServiceAccount(Credential credential) {
    return credential instanceof GoogleCredential
        ? ((GoogleCredential) credential).getServiceAccountId()
        : null;
  }

  private static SQLAdmin createAdminApiClient(HttpRequestInitializer requestInitializer) {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (GeneralSecurityException | IOException err) {
      throw new RuntimeException("Unable to initialize HTTP transport", err);
    }

    String rootUrl = System.getProperty(API_ROOT_URL_PROPERTY);
    String servicePath = System.getProperty(API_SERVICE_PATH_PROPERTY);
    String userToken = System.getProperty(USER_TOKEN_PROPERTY_NAME);

    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    SQLAdmin.Builder adminApiBuilder =
        new Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(userToken != null ? userToken : "Cloud SQL Java Socket Factory");
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

  private static class ApplicationDefaultCredentialFactory implements CredentialFactory {
    @Override
    public Credential create() {
      GoogleCredential credential;
      try {
        credential = GoogleCredential.getApplicationDefault();
      } catch (IOException err) {
        throw new RuntimeException(
            "Unable to obtain credentials to communicate with the Cloud SQL API", err);
      }
      if (credential.createScopedRequired()) {
        credential =
            credential.createScoped(Collections.singletonList(SQLAdminScopes.SQLSERVICE_ADMIN));
      }
      return credential;
    }
  }

  private static KeyPair generateRsaKeyPair() {
    KeyPairGenerator generator;
    try {
      generator = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException err) {
      throw new RuntimeException(
          "Unable to initialize Cloud SQL socket factory because no RSA implementation is "
              + "available.");
    }
    generator.initialize(RSA_KEY_SIZE);
    return generator.generateKeyPair();
  }

  private class InstanceLookupResult {
    private final Optional<RuntimeException> exception;
    private final long lastFailureMillis;
    private final Optional<InstanceSslInfo> instanceSslInfo;

    public InstanceLookupResult(RuntimeException exception) {
      this.exception = Optional.of(exception);
      this.lastFailureMillis = clock.now();
      this.instanceSslInfo = Optional.absent();
    }

    public InstanceLookupResult(InstanceSslInfo instanceSslInfo) {
      this.instanceSslInfo = Optional.of(instanceSslInfo);
      this.exception = Optional.absent();
      this.lastFailureMillis = 0;
    }

    public boolean isSuccessful() {
      return !exception.isPresent();
    }

    public Optional<InstanceSslInfo> getInstanceSslInfo() {
      return instanceSslInfo;
    }

    public Optional<RuntimeException> getException() {
      return exception;
    }

    public long getLastFailureMillis() {
      return lastFailureMillis;
    }
  }

  private static class InstanceSslInfo {
    private final HashMap<String, String> instanceIpAddresses = new HashMap<>();
    private final X509Certificate ephemeralCertificate;
    private final SSLSocketFactory sslSocketFactory;

    InstanceSslInfo(X509Certificate ephemeralCertificate, SSLSocketFactory sslSocketFactory) {
      this.ephemeralCertificate = ephemeralCertificate;
      this.sslSocketFactory = sslSocketFactory;
    }

    public void putInstanceIpAddress(String type, String ipAddress) {
      instanceIpAddresses.put(type == null ? "" : type.toUpperCase(), ipAddress);
    }

    public String getInstanceIpAddress(String type) {
      return instanceIpAddresses.get(type.toUpperCase());
    }

    public X509Certificate getEphemeralCertificate() {
      return ephemeralCertificate;
    }

    public SSLSocketFactory getSslSocketFactory() {
      return sslSocketFactory;
    }
  }

  @VisibleForTesting
  enum CertificateCaching {
    USE_CACHE,
    BYPASS_CACHE
  }

  static class Clock {
    long now() {
      return System.currentTimeMillis();
    }
  }
}
