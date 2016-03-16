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

package com.google.cloud.sql.mysql;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.SslCertsCreateEphemeralRequest;
import com.google.common.annotations.VisibleForTesting;
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
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

/**
 * Factory responsible for obtaining an ephemeral certificate, if necessary, and establishing a
 * secure connecting to a Cloud SQL instance.
 *
 * <p>The implementation is separate from {@link SocketFactory} to make this code easier to test.
 */
class SslSocketFactory {
  private static final Logger logger = Logger.getLogger(SslSocketFactory.class.getName());

  static final String ADMIN_API_NOT_ENABLED_REASON = "accessNotConfigured";
  static final String INSTANCE_NOT_AUTHORIZED_REASON = "notAuthorized";

  private static final int DEFAULT_SERVER_PROXY_PORT = 3307;
  private static final int RSA_KEY_SIZE = 2048;

  private static SslSocketFactory sslSocketFactory;

  private final CertificateFactory certificateFactory;
  private final Clock clock;
  private final KeyPair localKeyPair;
  private final GoogleCredential credential;
  private final Map<String, InstanceSslInfo> cache = new HashMap<>();
  private final SQLAdmin adminApi;
  private final int serverProxyPort;
  // Protection from attempting to renew ephemeral certificate too often in case of handshake
  // error. Allow forced renewal once a minute.
  private final RateLimiter forcedRenewRateLimiter = RateLimiter.create(1.0 / 60.0);

  @VisibleForTesting
  SslSocketFactory(
      Clock clock,
      KeyPair localKeyPair,
      GoogleCredential credential,
      SQLAdmin adminApi,
      int serverProxyPort) {
    try {
      this.certificateFactory = CertificateFactory.getInstance("X.509");
    } catch (CertificateException e) {
      throw new RuntimeException("X509 implementation not available", e);
    }
    this.clock = clock;
    this.localKeyPair = localKeyPair;
    this.credential = credential;
    this.adminApi = adminApi;
    this.serverProxyPort = serverProxyPort;
  }

  static synchronized SslSocketFactory getInstance() {
    if (sslSocketFactory == null) {
      logger.info("First Cloud SQL connection, generating RSA key pair.");
      KeyPair keyPair = generateRsaKeyPair();
      GoogleCredential credential = createCredential();
      SQLAdmin adminApi = createAdminApiClient(credential);
      sslSocketFactory =
          new SslSocketFactory(
              new Clock(), keyPair, credential, adminApi, DEFAULT_SERVER_PROXY_PORT);
    }
    return sslSocketFactory;
  }

  // TODO(berezv): separate creating socket and performing connection to make it easier to test
  Socket create(String instanceName) throws IOException {
    try {
      return createAndConfigureSocket(instanceName, CertificateCaching.USE_CACHE);
    } catch (SSLHandshakeException e) {
      logger.warning(
          String.format(
              "SSL handshake failed for Cloud SQL instance [%s], "
                  + "retrying with new certificate.\n%s",
              instanceName,
              Throwables.getStackTraceAsString(e)));

      if (!forcedRenewRateLimiter.tryAcquire()) {
        logger.warning(
            String.format(
                "Renewing too often, rate limiting certificate renewal for Cloud SQL "
                    + "instance [%s].",
                instanceName));
        forcedRenewRateLimiter.acquire();
      }
      return createAndConfigureSocket(instanceName, CertificateCaching.BYPASS_CACHE);
    }
  }

  private SSLSocket createAndConfigureSocket(
      String instanceName, CertificateCaching certificateCaching) throws IOException {
    InstanceSslInfo instanceSslInfo = getInstanceSslInfo(instanceName, certificateCaching);
    String ipAddress = instanceSslInfo.getInstanceIpAddress();
    logger.info(
        String.format(
            "Connecting to Cloud SQL instance [%s] on IP [%s].", instanceName, ipAddress));
    SSLSocket sslSocket =
        (SSLSocket)
            instanceSslInfo.getSslSocketFactory().createSocket(ipAddress, serverProxyPort);

    // TODO(berezv): Support all socket related options listed here:
    // https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html
    // TODO(berezv): Make sure we have appropriate timeout for establishing connection.
    sslSocket.setKeepAlive(true);
    sslSocket.setTcpNoDelay(true);

    sslSocket.startHandshake();
    return sslSocket;
  }

  // TODO(berezv): synchronize per instance, instead of globally
  @VisibleForTesting
  synchronized InstanceSslInfo getInstanceSslInfo(
      String instanceConnectionString, CertificateCaching certificateCaching) {

    if (certificateCaching.equals(CertificateCaching.USE_CACHE)) {
      InstanceSslInfo details = cache.get(instanceConnectionString);
      // Check if the cached certificate is still valid.
      if (details != null) {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.setTimeInMillis(clock.now());
        calendar.add(Calendar.MINUTE, 5);
        try {
          details.getEphemeralCertificate().checkValidity(calendar.getTime());
        } catch (CertificateException e) {
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

    logger.info(
        String.format(
            "Obtaining ephemeral certificate for Cloud SQL instance [%s].",
            instanceConnectionString));

    DatabaseInstance instance =
        obtainInstanceMetadata(adminApi, instanceConnectionString, projectId, instanceName);
    if (instance.getIpAddresses().isEmpty()) {
      throw
          new RuntimeException(
              String.format(
                  "Cloud SQL instance [%s] does not have any external IP addresses",
                  instanceConnectionString));
    }
    if (!instance.getRegion().equals(region)) {
      throw
          new IllegalArgumentException(
              String.format(
                  "Incorrect region value [%s] for Cloud SQL instance [%s], should be [%s]",
                  region,
                  instanceConnectionString,
                  instance.getRegion()));
    }

    X509Certificate ephemeralCertificate =
        obtainEphemeralCertificate(adminApi, instanceConnectionString, projectId, instanceName);

    Certificate instanceCaCertificate;
    try {
      instanceCaCertificate =
          certificateFactory.generateCertificate(
              new ByteArrayInputStream(
                  instance.getServerCaCert().getCert().getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException e) {
      throw
          new RuntimeException(
              String.format(
                  "Unable to parse certificate for Cloud SQL instance [%s]",
                  instanceConnectionString),
              e);
    }

    SSLContext sslContext = createSslContext(ephemeralCertificate, instanceCaCertificate);

    InstanceSslInfo details =
        new InstanceSslInfo(
            instance.getIpAddresses().get(0).getIpAddress(),
            ephemeralCertificate,
            sslContext.getSocketFactory());

    cache.put(instanceConnectionString, details);

    return details;
  }

  private SSLContext createSslContext(
      Certificate ephemeralCertificate, Certificate instanceCaCertificate) {
    KeyStore authKeyStore;
    try {
      authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      authKeyStore.load(null, null);
      KeyStore.PrivateKeyEntry pk =
          new KeyStore.PrivateKeyEntry(
              localKeyPair.getPrivate(), new Certificate[]{ephemeralCertificate});
      authKeyStore.setEntry("ephemeral", pk, new PasswordProtection(new char[0]));
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("There was a problem initializing the auth key store", e);
    }

    KeyStore trustKeyStore;
    try {
      trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustKeyStore.load(null, null);
      trustKeyStore.setCertificateEntry("instance", instanceCaCertificate);
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("There was a problem initializing the trust key store", e);
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
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("There was a problem initializing the SSL context", e);
    }
  }

  private DatabaseInstance obtainInstanceMetadata(
      SQLAdmin adminApi, String instanceConnectionString, String projectId, String instanceName) {
    DatabaseInstance instance;
    try {
      instance = adminApi.instances().get(projectId, instanceName).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getDetails() == null || e.getDetails().getErrors().isEmpty()) {
        throw
            new RuntimeException(
                String.format(
                    "Unable to retrieve information about Cloud SQL instance [%s]",
                    instanceConnectionString),
                e);
      }

      String reason = e.getDetails().getErrors().get(0).getReason();
      if (ADMIN_API_NOT_ENABLED_REASON.equals(reason)) {
        String apiLink =
            "https://console.developers.google.com/apis/api/sqladmin/overview?project=" + projectId;
        throw
            new RuntimeException(
                String.format(
                    "The Google Cloud SQL API is not enabled for project [%s]. Please "
                        + "use the Google Developers Console to enable it: %s",
                    projectId,
                    apiLink));
      } else if (INSTANCE_NOT_AUTHORIZED_REASON.equals(reason)) {
        // TODO(berezv): check if this works on Compute Engine / App Engine
        String who = "you are";
        if (credential.getServiceAccountId() != null) {
          who = "[" + credential.getServiceAccountId() + "] is";
        }
        throw
            new RuntimeException(
                String.format(
                    "Cloud SQL Instance [%s] does not exist or %s not authorized to "
                        + "access it. Please check the instance and project names to make "
                        + "sure they are correct.",
                    instanceConnectionString,
                    who));
      } else {
        throw
            new RuntimeException(
                String.format(
                    "Unable to retrieve information about Cloud SQL instance [%s]",
                    instanceConnectionString),
                e);
      }
    } catch (IOException e) {
      throw
          new RuntimeException(
              String.format(
                  "Unable to retrieve information about Cloud SQL instance [%s]",
                  instanceConnectionString),
              e);
    }

    if (!instance.getBackendType().equals("SECOND_GEN")) {
      throw
          new IllegalArgumentException(
              "This client only supports connections to Second Generation Cloud SQL "
                  + "instances");
    }

    return instance;
  }

  private X509Certificate obtainEphemeralCertificate(
      SQLAdmin adminApi, String instanceConnectionString, String projectId, String instanceName) {

    StringBuilder publicKeyPemBuilder = new StringBuilder();
    publicKeyPemBuilder.append("-----BEGIN RSA PUBLIC KEY-----\n");
    publicKeyPemBuilder.append(
        DatatypeConverter.printBase64Binary(localKeyPair.getPublic().getEncoded())
            .replaceAll("(.{64})", "$1\n"));
    publicKeyPemBuilder.append("\n");
    publicKeyPemBuilder.append("-----END RSA PUBLIC KEY-----\n");

    SslCertsCreateEphemeralRequest req = new SslCertsCreateEphemeralRequest();
    req.setPublicKey(publicKeyPemBuilder.toString());

    SslCert response;
    try {
      response = adminApi.sslCerts().createEphemeral(projectId, instanceName, req).execute();
    } catch (GoogleJsonResponseException e) {
      if (e.getDetails() == null || e.getDetails().getErrors().isEmpty()) {
        throw
            new RuntimeException(
                String.format(
                    "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
                    instanceConnectionString),
                e);
      }

      String reason = e.getDetails().getErrors().get(0).getReason();
      if (INSTANCE_NOT_AUTHORIZED_REASON.equals(reason)) {
        String who = "you have";
        if (credential.getServiceAccountId() != null) {
          who = "[" + credential.getServiceAccountId() + "] has";
        }
        throw
            new RuntimeException(
                String.format(
                    "Unable to obtain ephemeral certificate for Cloud SQL Instance [%s]. "
                        + "Make sure %s Editor or Owner role on the project.",
                    instanceConnectionString,
                    who));
      } else {
        throw
            new RuntimeException(
                String.format(
                    "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
                    instanceConnectionString),
                e);
      }
    } catch (IOException e) {
      throw
          new RuntimeException(
              String.format(
                  "Unable to obtain ephemeral certificate for Cloud SQL instance [%s]",
                  instanceConnectionString),
              e);
    }

    try {
      return
          (X509Certificate) certificateFactory.generateCertificate(
              new ByteArrayInputStream(response.getCert().getBytes(StandardCharsets.UTF_8)));
    } catch (CertificateException e) {
      throw
          new RuntimeException(
              String.format(
                  "Unable to parse ephemeral certificate for Cloud SQL instance [%s]",
                  instanceConnectionString),
              e);
    }
  }

  private static SQLAdmin createAdminApiClient(GoogleCredential credential) {
    HttpTransport httpTransport;
    try {
      httpTransport = GoogleNetHttpTransport.newTrustedTransport();
    } catch (Exception e) {
      throw new RuntimeException("Unable to initialize HTTP transport", e);
    }

    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    return
        new SQLAdmin.Builder(httpTransport, jsonFactory, credential)
            .setApplicationName("Cloud SQL Java Socket Factory")
            .build();
  }

  private static GoogleCredential createCredential() {
    GoogleCredential credential;
    try {
      credential = GoogleCredential.getApplicationDefault();
    } catch (IOException e) {
      throw
          new RuntimeException(
              "Unable to obtain credentials to communicate with the Cloud SQL API", e);
    }
    if (credential.createScopedRequired()) {
      credential = credential.createScoped(
          Collections.singletonList(SQLAdminScopes.SQLSERVICE_ADMIN));
    }
    return credential;
  }

  private static KeyPair generateRsaKeyPair() {
    KeyPairGenerator generator;
    try {
      generator = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(
          "Unable to initialize Cloud SQL socket factory because no RSA implementation is "
              + "available.");
    }
    generator.initialize(RSA_KEY_SIZE);
    return generator.generateKeyPair();
  }

  private static class InstanceSslInfo {
    private final String instanceIpAddress;
    private final X509Certificate ephemeralCertificate;
    private final SSLSocketFactory sslSocketFactory;

    InstanceSslInfo(
        String instanceIpAddress,
        X509Certificate ephemeralCertificate,
        SSLSocketFactory sslSocketFactory) {
      this.instanceIpAddress = instanceIpAddress;
      this.ephemeralCertificate = ephemeralCertificate;
      this.sslSocketFactory = sslSocketFactory;
    }

    public String getInstanceIpAddress() {
      return instanceIpAddress;
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
