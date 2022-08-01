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

import com.google.api.services.sqladmin.SQLAdmin;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;
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
  private static final String UNIX_SOCKET_PROPERTY = "unixSocketPath";

  private static final Logger logger = Logger.getLogger(CoreSocketFactory.class.getName());

  private static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";


  private static final int DEFAULT_SERVER_PROXY_PORT = 3307;
  private static final int RSA_KEY_SIZE = 2048;

  private static CoreSocketFactory coreSocketFactory;

  private final ListenableFuture<KeyPair> localKeyPair;
  private final ConcurrentHashMap<CloudSqlInstanceKey, CloudSqlInstance> instances = new ConcurrentHashMap<>();
  private final ListeningScheduledExecutorService executor;

  /* package*/ static List<String> userAgents = new ArrayList<String>();
  private static String version = getVersion();

  private final Optional<CredentialFactory> credentialFactory;
  private final Optional<SQLAdmin> adminApi;
  private final Optional<Integer> serverProxyPort;


  /**
   * Only used for mock testing.
   * @param localKeyPair
   * @param adminApi
   * @param credentialFactory
   * @param serverProxyPort
   * @param executor
   */
  @VisibleForTesting
  CoreSocketFactory(
      ListenableFuture<KeyPair> localKeyPair,
      SQLAdmin adminApi,
      CredentialFactory credentialFactory,
      int serverProxyPort,
      ListeningScheduledExecutorService executor) {
    this.adminApi = Optional.of(adminApi);
    this.credentialFactory = Optional.of(credentialFactory);
    this.serverProxyPort = Optional.of(serverProxyPort);
    this.executor = executor;
    this.localKeyPair = localKeyPair;
  }

  /**
   * The real constructor used by getInstance()
   */
  private CoreSocketFactory(ListenableFuture<KeyPair> localKeyPair,
      ListeningScheduledExecutorService executor) {
    this.adminApi = Optional.empty();
    this.credentialFactory = Optional.empty();
    this.serverProxyPort = Optional.empty();
    this.executor = executor;
    this.localKeyPair = localKeyPair;
  }


      /**
       * Returns the {@link CoreSocketFactory} singleton.
       */
  public static synchronized CoreSocketFactory getInstance() {
    if (coreSocketFactory == null) {
      logger.info("First Cloud SQL connection, generating RSA key pair.");
      ListeningScheduledExecutorService executor = getDefaultExecutor();
      coreSocketFactory =
          new CoreSocketFactory(
              executor.submit(CoreSocketFactory::generateRsaKeyPair),
              executor);
    }
    return coreSocketFactory;
  }

  private CloudSqlInstance getCloudSqlInstance(CloudSqlInstanceKey key) {
    return instances.computeIfAbsent(key, this::createInstance);
  }

  private CloudSqlInstance getCloudSqlInstance(String instanceName) {
    return getCloudSqlInstance(CloudSqlInstanceKey.MatchInstance(instanceName));
  }

  private CloudSqlInstance createInstance(CloudSqlInstanceKey key) {
    return new CloudSqlInstance(key, adminApi, credentialFactory, serverProxyPort, executor, localKeyPair);
  }

  static int getDefaultServerProxyPort() {
    return DEFAULT_SERVER_PROXY_PORT;
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
   * Extracts the Unix socket argument from specified properties object. If unset, returns null.
   */
  private static String getUnixSocketArg(Properties props) {
    String unixSocketPath = props.getProperty(UNIX_SOCKET_PROPERTY);
    if (unixSocketPath != null) {
      // Get the Unix socket file path from the properties object
      return unixSocketPath;
    } else if (System.getenv("CLOUD_SQL_FORCE_UNIX_SOCKET") != null) {
      // If the deprecated env var is set, warn and use `/cloudsql/INSTANCE_CONNECTION_NAME`
      // A socket factory is provided at this path for GAE, GCF, and Cloud Run
      logger.warning(
          String.format(
              "\"CLOUD_SQL_FORCE_UNIX_SOCKET\" env var has been deprecated. Please use"
                  + " '%s=\"/cloudsql/INSTANCE_CONNECTION_NAME\"' property in your JDBC url"
                  + " instead.",
              UNIX_SOCKET_PROPERTY));
      return "/cloudsql/" + props.getProperty(CLOUD_SQL_INSTANCE_PROPERTY);
    }
    return null; // if unset, default to null
  }

  /**
   * Creates a socket representing a connection to a Cloud SQL instance.
   */
  public static Socket connect(Properties props) throws IOException {
    return connect(props, null);
  }

  /**
   * Creates a socket representing a connection to a Cloud SQL instance.
   *
   * <p>Depending on the given properties, it may return either a SSL Socket or a Unix Socket.
   *
   * @param props          Properties used to configure the connection.
   * @param unixPathSuffix suffix to add the the Unix socket path. Unused if null.
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  public static Socket connect(Properties props, String unixPathSuffix) throws IOException {
    // Gather parameters
    final String csqlInstanceName = props.getProperty(CLOUD_SQL_INSTANCE_PROPERTY);
    final boolean enableIamAuth = Boolean.parseBoolean(props.getProperty("enableIamAuth"));

    // Validate parameters
    Preconditions.checkArgument(
        csqlInstanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or the "
            + "connection Properties with value in form \"project:region:instance\"");

    // Connect using the specified Unix socket
    String unixSocket = getUnixSocketArg(props);
    if (unixSocket != null) {
      // Verify it ends with the correct suffix
      if (unixPathSuffix != null && !unixSocket.endsWith(unixPathSuffix)) {
        unixSocket = unixSocket + unixPathSuffix;
      }
      logger.info(
          String.format(
              "Connecting to Cloud SQL instance [%s] via unix socket at %s.",
              csqlInstanceName, unixSocket));
      UnixSocketAddress socketAddress = new UnixSocketAddress(new File(unixSocket));
      return UnixSocketChannel.open(socketAddress).socket();
    }

    final List<String> ipTypes = listIpTypes(props.getProperty("ipTypes", DEFAULT_IP_TYPES));
    logger.info(
        String.format("Connecting to Cloud SQL instance [%s] via SSL socket.", csqlInstanceName));
    return getInstance().createSslSocket(csqlInstanceName, ipTypes, enableIamAuth);
  }

  /**
   * Returns data that can be used to establish Cloud SQL SSL connection.
   */
  @Deprecated
  public static SslData getSslData(String csqlInstanceName, boolean enableIamAuth) {
    return getSslData(CloudSqlInstanceKey.Create(csqlInstanceName, enableIamAuth));
  }

  public static SslData getSslData(CloudSqlInstanceKey key) {
    return getInstance().getCloudSqlInstance(key).getSslData();
  }

  /**
   * Returns preferred ip address that can be used to establish Cloud SQL connection.
   */
  public static String getHostIp(String csqlInstanceName) {
    return getInstance().getHostIp(csqlInstanceName, listIpTypes(DEFAULT_IP_TYPES));
  }

  private String getHostIp(String instanceName, List<String> ipTypes) {
    CloudSqlInstance instance = getCloudSqlInstance(instanceName);
    return instance.getPreferredIp(ipTypes);
  }


  /**
   * Creates a secure socket representing a connection to a Cloud SQL instance.
   *
   * @param instanceName Name of the Cloud SQL instance.
   * @param ipTypes      Preferred type of IP to use ("PRIVATE", "PUBLIC")
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  // TODO(berezv): separate creating socket and performing connection to make it easier to test
  @VisibleForTesting
  Socket createSslSocket(String instanceName, List<String> ipTypes, boolean enableIamAuth)
      throws IOException {
    CloudSqlInstance instance = getCloudSqlInstance(CloudSqlInstanceKey.Create(instanceName, enableIamAuth));
    return instance.createAndConfigureSocket(ipTypes);

  }

  Socket createSslSocket(String instanceName, List<String> ipTypes) throws IOException {
    return createSslSocket(instanceName, ipTypes, false);
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

  private static String getVersion() {
    try {
      Properties packageInfo = new Properties();
      packageInfo.load(CoreSocketFactory.class.getClassLoader().getResourceAsStream(
          "com.google.cloud.sql/project.properties"));
      return packageInfo.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }

  /**
   * Sets the default string which is appended to the SQLAdmin API client User-Agent header.
   */
  public static void addArtifactId(String artifactId) {
    String userAgent = artifactId + "/" + version;
    if (!userAgents.contains(userAgent)) {
      userAgents.add(userAgent);
    }
  }

}
