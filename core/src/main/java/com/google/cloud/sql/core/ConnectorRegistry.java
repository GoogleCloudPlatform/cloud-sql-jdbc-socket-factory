package com.google.cloud.sql.core;

import com.google.cloud.sql.SqlAdminApiFetcherFactory;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.Closeable;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * ConnectorRegistry is a singleton that creates a single Executor, KeyPair, and AlloyDB Admin
 * Client for the lifetime of the SocketFactory. When callers are finished with the Connector, they
 * should use the ConnectorRegistry to shut down all the associated resources.
 */
public enum ConnectorRegistry implements Closeable {
  INSTANCE;

  private final ListeningScheduledExecutorService executor;
  private final CloudSqlConnector connector;
  private final String version;

  private final List<String> userAgents = new ArrayList<>();

  ConnectorRegistry() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    this.executor =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));

    this.connector =
        new CloudSqlConnector(
            this.executor,
            generateRsaKeyPair(),
            new SqlAdminApiFetcherFactory(getUserAgents()),
            CredentialFactoryProvider.getCredentialFactory());
    this.version = getVersion();
  }

  CloudSqlConnector getConnector() {
    return this.connector;
  }

  @Override
  public void close() {
    this.executor.shutdown();
  }

  static KeyPair generateRsaKeyPair() {
    KeyPairGenerator generator;
    try {
      generator = KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException err) {
      throw new RuntimeException(
          "Unable to initialize Cloud SQL socket factory because no RSA implementation is "
              + "available.");
    }
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static String getVersion() {
    try {
      Properties packageInfo = new Properties();
      packageInfo.load(
          CoreSocketFactory.class
              .getClassLoader()
              .getResourceAsStream("com.google.cloud.sql/project.properties"));
      return packageInfo.getProperty("version", "unknown");
    } catch (IOException e) {
      return "unknown";
    }
  }

  /** Sets the default string which is appended to the SQLAdmin API client User-Agent header. */
  void addArtifactId(String artifactId) {
    String userAgent = artifactId + "/" + version;
    if (!userAgents.contains(userAgent)) {
      userAgents.add(userAgent);
    }
  }

  /** Returns the default string which is appended to the SQLAdmin API client User-Agent header. */
  String getUserAgents() {
    return String.join(" ", userAgents) + " " + getApplicationName();
  }

  /** Returns the current User-Agent header set for the underlying SQLAdmin API client. */
  private static String getApplicationName() {
    return System.getProperty(ConnectionConfig.USER_TOKEN_PROPERTY_NAME, "");
  }
}
