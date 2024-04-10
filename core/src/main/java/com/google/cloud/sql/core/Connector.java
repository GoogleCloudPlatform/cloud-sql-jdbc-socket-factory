/*
 * Copyright 2023 Google LLC
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

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLSocket;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class Connector {
  private static final Logger logger = LoggerFactory.getLogger(Connector.class);

  private final ConnectionInfoRepository adminApi;
  private final CredentialFactory instanceCredentialFactory;
  private final ListeningScheduledExecutorService executor;
  private final ListenableFuture<KeyPair> localKeyPair;
  private final long minRefreshDelayMs;

  private final ConcurrentHashMap<ConnectionConfig, DefaultConnectionInfoCache> instances =
      new ConcurrentHashMap<>();
  private final int serverProxyPort;
  private final ConnectorConfig config;

  Connector(
      ConnectorConfig config,
      ConnectionInfoRepositoryFactory connectionInfoRepositoryFactory,
      CredentialFactory instanceCredentialFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> localKeyPair,
      long minRefreshDelayMs,
      long refreshTimeoutMs,
      int serverProxyPort) {
    this.config = config;

    this.adminApi =
        connectionInfoRepositoryFactory.create(instanceCredentialFactory.create(), config);
    this.instanceCredentialFactory = instanceCredentialFactory;
    this.executor = executor;
    this.localKeyPair = localKeyPair;
    this.minRefreshDelayMs = minRefreshDelayMs;
    this.serverProxyPort = serverProxyPort;
  }

  public ConnectorConfig getConfig() {
    return config;
  }

  /** Extracts the Unix socket argument from specified properties object. If unset, returns null. */
  private String getUnixSocketArg(ConnectionConfig config) {
    String unixSocketPath = config.getUnixSocketPath();
    if (unixSocketPath != null) {
      // Get the Unix socket file path from the properties object
      return unixSocketPath;
    } else if (System.getenv("CLOUD_SQL_FORCE_UNIX_SOCKET") != null) {
      // If the deprecated env var is set, warn and use `/cloudsql/INSTANCE_CONNECTION_NAME`
      // A socket factory is provided at this path for GAE, GCF, and Cloud Run
      logger.debug(
          String.format(
              "\"CLOUD_SQL_FORCE_UNIX_SOCKET\" env var has been deprecated. Please use"
                  + " '%s=\"/cloudsql/INSTANCE_CONNECTION_NAME\"' property in your JDBC url"
                  + " instead.",
              ConnectionConfig.UNIX_SOCKET_PROPERTY));
      return "/cloudsql/" + config.getCloudSqlInstance();
    }
    return null; // if unset, default to null
  }

  Socket connect(ConnectionConfig config, long timeoutMs) throws IOException {
    // Connect using the specified Unix socket
    String unixSocket = getUnixSocketArg(config);
    String unixPathSuffix = config.getUnixSocketPathSuffix();
    if (unixSocket != null) {
      // Verify it ends with the correct suffix
      if (unixPathSuffix != null && !unixSocket.endsWith(unixPathSuffix)) {
        unixSocket = unixSocket + unixPathSuffix;
      }
      logger.debug(
          String.format(
              "Connecting to Cloud SQL instance [%s] via unix socket at %s.",
              config.getCloudSqlInstance(), unixSocket));
      UnixSocketAddress socketAddress = new UnixSocketAddress(new File(unixSocket));
      return UnixSocketChannel.open(socketAddress).socket();
    }

    DefaultConnectionInfoCache instance = getConnection(config);
    try {

      String instanceIp = instance.getConnectionMetadata(timeoutMs).getPreferredIpAddress();
      logger.debug(String.format("[%s] Connecting to instance.", instanceIp));

      SSLSocket socket = instance.createSslSocket(timeoutMs);
      socket.setKeepAlive(true);
      socket.setTcpNoDelay(true);
      socket.connect(new InetSocketAddress(instanceIp, serverProxyPort));

      try {
        socket.startHandshake();
      } catch (IOException e) {
        logger.debug("TLS handshake failed!");
        throw e;
      }

      logger.debug(String.format("[%s] Connected to instance successfully.", instanceIp));

      return socket;
    } catch (IOException e) {
      logger.debug(
          String.format(
              "[%s] Socket connection failed! Trigger a refresh.", config.getCloudSqlInstance()));
      instance.forceRefresh();
      throw e;
    }
  }

  DefaultConnectionInfoCache getConnection(ConnectionConfig config) {
    DefaultConnectionInfoCache instance =
        instances.computeIfAbsent(config, k -> createConnectionInfo(config));

    // If the client certificate has expired (as when the computer goes to
    // sleep, and the refresh cycle cannot run), force a refresh immediately.
    // The TLS handshake will not fail on an expired client certificate. It's
    // not until the first read where the client cert error will be surfaced.
    // So check that the certificate is valid before proceeding.
    instance.refreshIfExpired();

    return instance;
  }

  private DefaultConnectionInfoCache createConnectionInfo(ConnectionConfig config) {
    logger.debug(
        String.format("[%s] Connection info added to cache.", config.getCloudSqlInstance()));
    return new DefaultConnectionInfoCache(
        config, adminApi, instanceCredentialFactory, executor, localKeyPair, minRefreshDelayMs);
  }

  public void close() {
    logger.debug("Close all connections and remove them from cache.");
    this.instances.forEach((key, c) -> c.close());
    this.instances.clear();
  }
}
