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

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * InternalConnectorRegistry keeps track of connectors. This class should not be used directly, but
 * only through the JDBC driver specific {@code SocketFactory} implementations.
 *
 * <p>WARNING: This is an internal class. The API is subject to change without notice.
 */
public final class InternalConnectorRegistry {

  static final long DEFAULT_CONNECT_TIMEOUT_MS = 45000; // connect attempt times out after 45 sec
  private static final Logger logger = LoggerFactory.getLogger(InternalConnectorRegistry.class);

  static final int DEFAULT_SERVER_PROXY_PORT = 3307;
  private static final int RSA_KEY_SIZE = 2048;
  private static final List<String> userAgents = new ArrayList<>();
  private static final String version = getVersion();
  private static final long MIN_REFRESH_DELAY_MS = 30000; // Minimum 30 seconds between refresh.
  private static InternalConnectorRegistry internalConnectorRegistry;
  private static boolean shutdown = false;
  private final ListenableFuture<KeyPair> localKeyPair;
  private final ConcurrentHashMap<ConnectorConfig, Connector> unnamedConnectors =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Connector> namedConnectors = new ConcurrentHashMap<>();
  private final ListeningScheduledExecutorService executor;
  private final CredentialFactoryProvider credentialFactoryProvider;
  private final int serverProxyPort;
  private final long connectTimeoutMs;
  private final ConnectionInfoRepositoryFactory connectionInfoRepositoryFactory;

  /**
   * Property used to set the application name for the underlying SQLAdmin client.
   *
   * @deprecated Use {@link #setApplicationName(String)} to set the application name
   *     programmatically.
   */
  static final String USER_TOKEN_PROPERTY_NAME = "_CLOUD_SQL_USER_TOKEN";

  @VisibleForTesting
  InternalConnectorRegistry(
      ListenableFuture<KeyPair> localKeyPair,
      ConnectionInfoRepositoryFactory connectionInfoRepositoryFactory,
      CredentialFactoryProvider credentialFactoryProvider,
      int serverProxyPort,
      long connectTimeoutMs,
      ListeningScheduledExecutorService executor) {
    this.connectionInfoRepositoryFactory = connectionInfoRepositoryFactory;
    this.credentialFactoryProvider = credentialFactoryProvider;
    this.serverProxyPort = serverProxyPort;
    this.executor = executor;
    this.localKeyPair = localKeyPair;
    this.connectTimeoutMs = connectTimeoutMs;
  }

  /** Returns the {@link InternalConnectorRegistry} singleton. */
  public static synchronized InternalConnectorRegistry getInstance() {
    if (shutdown) {
      throw new IllegalStateException("ConnectorRegistry was shut down.");
    }

    if (internalConnectorRegistry == null) {
      logger.debug("First Cloud SQL connection, generating RSA key pair.");

      CredentialFactoryProvider credentialFactoryProvider = new CredentialFactoryProvider();

      ListeningScheduledExecutorService executor = getDefaultExecutor();

      internalConnectorRegistry =
          new InternalConnectorRegistry(
              executor.submit(InternalConnectorRegistry::generateRsaKeyPair),
              new DefaultConnectionInfoRepositoryFactory(getUserAgents()),
              credentialFactoryProvider,
              DEFAULT_SERVER_PROXY_PORT,
              DEFAULT_CONNECT_TIMEOUT_MS,
              executor);
    }
    return internalConnectorRegistry;
  }

  /**
   * Calls shutdown on the singleton and removes the singleton. After calling shutdownInstance(),
   * the next call to getInstance() will start a new singleton instance.
   */
  public static synchronized void resetInstance() {
    if (internalConnectorRegistry != null) {
      InternalConnectorRegistry old = internalConnectorRegistry;
      internalConnectorRegistry = null;
      old.shutdown();
      resetUserAgent();
    }
  }

  /**
   * Calls shutdown on the singleton and removes the singleton. After calling shutdownInstance(),
   * the next call to getInstance() will start a new singleton instance.
   */
  public static synchronized void shutdownInstance() {
    shutdown = true;
    resetInstance();
  }

  // TODO(kvg): Figure out better executor to use for testing
  @VisibleForTesting
  // Returns a listenable, scheduled executor that exits upon shutdown.
  static ListeningScheduledExecutorService getDefaultExecutor() {

    // During refresh, each instance consumes 2 threads from the thread pool. By using 8 threads,
    // there should be enough free threads so that there will not be a deadlock. Most users
    // configure 3 or fewer instances, requiring 6 threads during refresh. By setting
    // this to 8, it's enough threads for most users, plus a safety factor of 2.

    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(8);

    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  /**
   * Internal use only: Creates a socket representing a connection to a Cloud SQL instance.
   *
   * <p>Depending on the given properties, it may return either a SSL Socket or a Unix Socket.
   *
   * @param config used to configure the connection.
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  public Socket connect(ConnectionConfig config) throws IOException, InterruptedException {
    if (config.getNamedConnector() != null) {
      Connector connector = getNamedConnector(config.getNamedConnector());
      return connector.connect(config.withConnectorConfig(connector.getConfig()), connectTimeoutMs);
    }

    // Validate parameters
    Preconditions.checkArgument(
        config.getCloudSqlInstance() != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or the "
            + "connection Properties with value in form \"project:region:instance\"");

    return getConnector(config).connect(config, connectTimeoutMs);
  }

  /** Internal use only: Returns ConnectionMetadata for a connection. */
  public ConnectionMetadata getConnectionMetadata(ConnectionConfig config) {
    if (config.getNamedConnector() != null) {
      Connector connector = getNamedConnector(config.getNamedConnector());
      return connector
          .getConnection(config.withConnectorConfig(connector.getConfig()))
          .getConnectionMetadata(connectTimeoutMs);
    }

    return getConnector(config).getConnection(config).getConnectionMetadata(connectTimeoutMs);
  }

  /** Internal use only: Force refresh the connection info. */
  public void forceRefresh(ConnectionConfig config) {
    if (config.getNamedConnector() != null) {
      Connector connector = getNamedConnector(config.getNamedConnector());
      connector.getConnection(config.withConnectorConfig(connector.getConfig())).forceRefresh();
    } else {
      getConnector(config).getConnection(config).forceRefresh();
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

  private static String getVersion() {
    try {
      Properties packageInfo = new Properties();
      packageInfo.load(
          InternalConnectorRegistry.class
              .getClassLoader()
              .getResourceAsStream("com.google.cloud.sql/project.properties"));
      return packageInfo.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }

  /**
   * Internal use only: Sets the default string which is appended to the SQLAdmin API client
   * User-Agent header.
   *
   * @param artifactId is the Artifact ID.
   * @param addVersion whether the version should be appended to the ID.
   */
  public static void addArtifactId(String artifactId, boolean addVersion) {
    String userAgent = artifactId;

    if (addVersion) {
      userAgent += "/" + version;
    }
    if (!userAgents.contains(userAgent)) {
      userAgents.add(userAgent);
    }
  }

  /**
   * Internal use only: Sets the default string which is appended to the SQLAdmin API client
   * User-Agent header.
   *
   * <p>This is used by the specific database connector socket factory implementations to append
   * their database name to the user agent. The version is appended to the ID.
   *
   * @param artifactId is the Artifact ID.
   */
  public static void addArtifactId(String artifactId) {
    addArtifactId(artifactId, true);
  }

  /** Resets the values of User Agent fields for unit tests. */
  @VisibleForTesting
  static void resetUserAgent() {
    internalConnectorRegistry = null;
    userAgents.clear();
    setApplicationName("");
  }

  /** Returns the default string which is appended to the SQLAdmin API client User-Agent header. */
  static String getUserAgents() {
    String ua = String.join(" ", userAgents);
    String appName = getApplicationName();
    if (!Strings.isNullOrEmpty(appName)) {
      ua = ua + " " + appName;
    }
    return ua;
  }

  /** Returns the current User-Agent header set for the underlying SQLAdmin API client. */
  private static String getApplicationName() {
    return System.getProperty(USER_TOKEN_PROPERTY_NAME, "");
  }

  /**
   * Adds an external application name to the user agent string for tracking. This is known to be
   * used by the spring-cloud-gcp project.
   *
   * @throws IllegalStateException if the SQLAdmin client has already been initialized
   */
  public static void setApplicationName(String applicationName) {
    if (internalConnectorRegistry != null) {
      throw new IllegalStateException(
          "Unable to set ApplicationName - SQLAdmin client already initialized.");
    }
    System.setProperty(USER_TOKEN_PROPERTY_NAME, applicationName);
  }

  private Connector getConnector(ConnectionConfig config) {
    return unnamedConnectors.computeIfAbsent(
        config.getConnectorConfig(), k -> createConnector(config.getConnectorConfig()));
  }

  private Connector createConnector(ConnectorConfig config) {

    CredentialFactory instanceCredentialFactory =
        credentialFactoryProvider.getInstanceCredentialFactory(config);

    String universeDomain = config.getUniverseDomain();
    String credentialsUniverse;
    try {
      credentialsUniverse = instanceCredentialFactory.getCredentials().getUniverseDomain();
    } catch (IOException e) {
      throw new IllegalStateException("Fail to fetch the credential universe domain");
    }

    // Verify that the universe domain provided matches the credential universe domain.
    if (credentialsUniverse != null
        && universeDomain != null
        && !credentialsUniverse.equals(universeDomain)) {
      throw new IllegalStateException(
          String.format(
              "The configured universe domain (%s) does not match "
                  + "the credential universe domain (%s)",
              universeDomain, credentialsUniverse));
    }

    return new Connector(
        config,
        connectionInfoRepositoryFactory,
        instanceCredentialFactory,
        executor,
        localKeyPair,
        MIN_REFRESH_DELAY_MS,
        connectTimeoutMs,
        serverProxyPort,
        new DnsInstanceConnectionNameResolver(new JndiDnsResolver()));
  }

  /** Register the configuration for a named connector. */
  public void register(String name, ConnectorConfig config) {
    if (this.namedConnectors.containsKey(name)) {
      throw new IllegalArgumentException("Named connection " + name + " exists.");
    }
    this.namedConnectors.put(name, createConnector(config));
  }

  /** Close a named connector, stopping the refresh process and removing it from the registry. */
  public void close(String name) {
    Connector connector = namedConnectors.remove(name);
    if (connector == null) {
      throw new IllegalArgumentException("Named connection " + name + " does not exist.");
    }
    connector.close();
  }

  /** Shutdown all connectors and remove the singleton instance. */
  public void shutdown() {
    this.unnamedConnectors.forEach((key, c) -> c.close());
    this.unnamedConnectors.clear();
    this.namedConnectors.forEach((key, c) -> c.close());
    this.namedConnectors.clear();
    this.executor.shutdown();
  }

  private Connector getNamedConnector(String name) {
    Connector connector = namedConnectors.get(name);
    if (connector == null) {
      throw new IllegalArgumentException("Named connection " + name + " does not exist.");
    }
    return connector;
  }
}
