package com.google.cloud.sql.core;

import com.google.cloud.sql.ApiFetcherFactory;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.IOException;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

class CloudSqlConnector {
  private static final Logger logger = Logger.getLogger(CloudSqlConnector.class.getName());

  private final ListeningScheduledExecutorService executor;
  private final KeyPair localKeyPair;
  private final ApiFetcherFactory adminApiFactory;
  private final ConcurrentHashMap<ConnectionConfig, CloudSqlInstance> instances =
      new ConcurrentHashMap<>();
  private final CredentialFactory credentialFactory;

  CloudSqlConnector(
      ListeningScheduledExecutorService executor,
      KeyPair localKeyPair,
      ApiFetcherFactory adminApiFactory,
      CredentialFactory credentialFactory) {
    this.executor = executor;
    this.localKeyPair = localKeyPair;
    this.adminApiFactory = adminApiFactory;
    this.credentialFactory = credentialFactory;
  }

  /**
   * Creates a socket representing a connection to a Cloud SQL instance.
   *
   * <p>Depending on the given properties, it may return either a SSL Socket or a Unix Socket.
   *
   * @param props Properties used to configure the connection.
   * @param unixPathSuffix suffix to add the the Unix socket path. Unused if null.
   * @return the newly created Socket.
   * @throws IOException if error occurs during socket creation.
   */
  Socket connect(ConnectionConfig config) throws IOException {
    return getCloudSqlInstance(config).connect();
  }

  SslData getSslData(ConnectionConfig config) throws IOException {
    return getCloudSqlInstance(config).getSslData();
  }

  String getPreferredIp(ConnectionConfig config) {
    return getCloudSqlInstance(config).getPreferredIp();
  }

  CloudSqlInstance getCloudSqlInstance(ConnectionConfig config) {
    return instances.computeIfAbsent(
        config,
        k -> {
          try {
            return new CloudSqlInstance(
                k,
                this.adminApiFactory.create(credentialFactory.create()),
                credentialFactory,
                executor,
                Futures.immediateFuture(localKeyPair));
          } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
