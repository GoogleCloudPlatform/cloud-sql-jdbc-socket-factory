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
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.SQLAdmin.Builder;
import com.google.api.services.sqladmin.SQLAdminScopes;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocket;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * Factory responsible for obtaining an ephemeral certificate, if necessary, and establishing a
 * secure connecting to a Cloud SQL instance.
 *
 * <p>This class should not be used directly, but only through the JDBC driver specific {@code
 * SocketFactory} implementations.
 *
 * <p>The API of this class is subject to change without notice.
 */
public final class CoreSocketFactory {
  public static final String CLOUD_SQL_INSTANCE_PROPERTY = "cloudSqlInstance";
  public static final String MYSQL_SOCKET_FILE_FORMAT = "/cloudsql/%s";
  public static final String POSTGRES_SOCKET_FILE_FORMAT = "/cloudsql/%s/.s.PGSQL.5432";

  /**
   * Property used to set the application name for the underlying SQLAdmin client.
   *
   * @deprecated Use {@link #setApplicationName(String)} to set the application name
   *     programmatically.
   */
  @Deprecated public static final String USER_TOKEN_PROPERTY_NAME = "_CLOUD_SQL_USER_TOKEN";

  private static final Logger logger = Logger.getLogger(CoreSocketFactory.class.getName());

  private static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";

  // Test properties, not for end-user use. May be changed or removed without notice.
  private static final String API_ROOT_URL_PROPERTY = "_CLOUD_SQL_API_ROOT_URL";
  private static final String API_SERVICE_PATH_PROPERTY = "_CLOUD_SQL_API_SERVICE_PATH";

  private static final int DEFAULT_SERVER_PROXY_PORT = 3307;
  private static final int RSA_KEY_SIZE = 2048;

  private static CoreSocketFactory coreSocketFactory;

  private final CertificateFactory certificateFactory;
  private final ListenableFuture<KeyPair> localKeyPair;
  private final Credential credential;
  private final ConcurrentHashMap<String, CloudSqlInstance> instances = new ConcurrentHashMap<>();
  private final ListeningScheduledExecutorService executor;
  private final SQLAdmin adminApi;
  private final int serverProxyPort;

  @VisibleForTesting
  CoreSocketFactory(
      ListenableFuture<KeyPair> localKeyPair,
      Credential credential,
      SQLAdmin adminApi,
      int serverProxyPort,
      ListeningScheduledExecutorService executor) {
    this.certificateFactory = x509CertificateFactory();
    this.credential = credential;
    this.adminApi = adminApi;
    this.serverProxyPort = serverProxyPort;
    this.executor = executor;
    this.localKeyPair = localKeyPair;
  }

  /** Returns the {@link CoreSocketFactory} singleton. */
  public static synchronized CoreSocketFactory getInstance() {
    if (coreSocketFactory == null) {
      logger.info("First Cloud SQL connection, generating RSA key pair.");
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
      ListeningScheduledExecutorService executor = getDefaultExecutor();

      coreSocketFactory =
          new CoreSocketFactory(
              executor.submit(CoreSocketFactory::generateRsaKeyPair),
              credential,
              adminApi,
              DEFAULT_SERVER_PROXY_PORT,
              executor);
    }
    return coreSocketFactory;
  }

  // Returns a factory used to create X.509 public certificates
  private static CertificateFactory x509CertificateFactory() {
    try {
      return CertificateFactory.getInstance("X.509");
    } catch (CertificateException err) {
      throw new RuntimeException("X509 implementation not available", err);
    }
  }

  // TODO(kvg): Figure out better executor to use for testing
  @VisibleForTesting
  // Returns a listenable, scheduled executor that exits upon shutdown.
  static ListeningScheduledExecutorService getDefaultExecutor() {
    // TODO(kvg): Figure out correct way to determine number of threads
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  /**
   * Creates a socket representing a connection to a Cloud SQL instance.
   *
   * <p>Depending on the environment, it may return either a SSL Socket or a Unix Socket.
   *
   * @param props Properties used to configure the connection.
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  public static Socket connect(Properties props, String socketPathFormat) throws IOException {
    // Gather parameters
    final String csqlInstanceName = props.getProperty(CLOUD_SQL_INSTANCE_PROPERTY);
    final List<String> ipTypes = listIpTypes(props.getProperty("ipTypes", DEFAULT_IP_TYPES));
    final boolean forceUnixSocket = System.getenv("CLOUD_SQL_FORCE_UNIX_SOCKET") != null;

    // Validate parameters
    Preconditions.checkArgument(
        csqlInstanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or the "
            + "connection Properties with value in form \"project:region:instance\"");

    // GAE Standard + GCF provide a connection path at "/cloudsql/<CONNECTION_NAME>"
    if (forceUnixSocket || runningOnGaeStandard() || runningOnGoogleCloudFunctions()) {
      logger.info(
          String.format(
              "Connecting to Cloud SQL instance [%s] via unix socket.", csqlInstanceName));
      UnixSocketAddress socketAddress =
          new UnixSocketAddress(new File(String.format(socketPathFormat, csqlInstanceName)));
      return UnixSocketChannel.open(socketAddress).socket();
    }

    logger.info(
        String.format("Connecting to Cloud SQL instance [%s] via SSL socket.", csqlInstanceName));
    return getInstance().createSslSocket(csqlInstanceName, ipTypes);
  }

  /** Returns {@code true} if running in a Google App Engine Standard runtime. */
  private static boolean runningOnGaeStandard() {
    // gaeEnv="standard" indicates standard instances
    String gaeEnv = System.getenv("GAE_ENV");
    // runEnv="Production" requires to rule out Java 8 emulated environments
    String runEnv = System.getProperty("com.google.appengine.runtime.environment");
    // gaeRuntime="java11" in Java 11 environments (no emulated environments)
    String gaeRuntime = System.getenv("GAE_RUNTIME");

    return "standard".equals(gaeEnv)
        && ("Production".equals(runEnv) || "java11".equals(gaeRuntime));
  }

  /** Returns {@code true} if running in a Google Cloud Functions runtime. */
  private static boolean runningOnGoogleCloudFunctions() {
    // Functions automatically sets a few variables we can use to guess the env:
    // See https://cloud.google.com/functions/docs/env-var#nodejs_10_and_subsequent_runtimes
    return System.getenv("K_SERVICE") != null && System.getenv("K_REVISION") != null;
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
  @VisibleForTesting
  Socket createSslSocket(String instanceName, List<String> ipTypes) throws IOException {
    CloudSqlInstance instance =
        instances.computeIfAbsent(
            instanceName, k -> new CloudSqlInstance(k, adminApi, executor, localKeyPair));

    try {
      SSLSocket socket = instance.createSslSocket();

      // TODO(kvg): Support all socket related options listed here:
      // https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html
      socket.setKeepAlive(true);
      socket.setTcpNoDelay(true);

      String instanceIp = instance.getPreferredIp(ipTypes);

      socket.connect(new InetSocketAddress(instanceIp, serverProxyPort));
      socket.startHandshake();

      return socket;
    } catch (Exception ex) {
      // TODO(kvg): Let user know about the rate limit
      instance.forceRefresh();
      throw ex;
    }
  }

  private static void logTestPropertyWarning(String property) {
    logger.warning(
        String.format(
            "%s is a test property and may be changed or removed in a future version without "
                + "notice.",
            property));
  }

  /**
   * Converts the string property of IP types to a list by splitting by commas, and upper-casing.
   */
  private static List<String> listIpTypes(String cloudSqlIpTypes) {
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

    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    SQLAdmin.Builder adminApiBuilder =
        new Builder(httpTransport, jsonFactory, requestInitializer)
            .setApplicationName(getApplicationName());
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

  /**
   * Sets the User-Agent header for requests made using the underlying SQLAdmin API client.
   *
   * @throws IllegalStateException if the SQLAdmin client has already been initialized
   */
  public static void setApplicationName(String applicationName) {
    if (coreSocketFactory != null) {
      throw new IllegalStateException(
          "Unable to set ApplicationName - SQLAdmin client already initialized.");
    }
    System.setProperty(USER_TOKEN_PROPERTY_NAME, applicationName);
  }

  /** Returns the current User-Agent header set for the underlying SQLAdmin API client. */
  public static String getApplicationName() {
    if (coreSocketFactory != null) {
      return coreSocketFactory.adminApi.getApplicationName();
    }
    return System.getProperty(USER_TOKEN_PROPERTY_NAME, "Cloud SQL Java Socket Factory");
  }
}
