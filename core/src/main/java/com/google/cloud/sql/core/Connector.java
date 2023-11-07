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
import java.util.logging.Logger;
import javax.net.ssl.SSLSocket;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

class Connector {
  private static final Logger logger = Logger.getLogger(Connector.class.getName());

  private final DefaultConnectionInfoRepository adminApi;
  private final CredentialFactory instanceCredentialFactory;
  private final ListeningScheduledExecutorService executor;
  private final ListenableFuture<KeyPair> localKeyPair;
  private final long minRefreshDelayMs;

  private final ConcurrentHashMap<ConnectionConfig, DefaultConnectionInfoCache> instances =
      new ConcurrentHashMap<>();
  private final long refreshTimeoutMs;
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
    this.refreshTimeoutMs = refreshTimeoutMs;
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
      logger.warning(
          String.format(
              "\"CLOUD_SQL_FORCE_UNIX_SOCKET\" env var has been deprecated. Please use"
                  + " '%s=\"/cloudsql/INSTANCE_CONNECTION_NAME\"' property in your JDBC url"
                  + " instead.",
              ConnectionConfig.UNIX_SOCKET_PROPERTY));
      return "/cloudsql/" + config.getCloudSqlInstance();
    }
    return null; // if unset, default to null
  }

  Socket connect(ConnectionConfig config) throws IOException {
    // Connect using the specified Unix socket
    String unixSocket = getUnixSocketArg(config);
    String unixPathSuffix = config.getUnixSocketPathSuffix();
    if (unixSocket != null) {
      // Verify it ends with the correct suffix
      if (unixPathSuffix != null && !unixSocket.endsWith(unixPathSuffix)) {
        unixSocket = unixSocket + unixPathSuffix;
      }
      logger.info(
          String.format(
              "Connecting to Cloud SQL instance [%s] via unix socket at %s.",
              config.getCloudSqlInstance(), unixSocket));
      UnixSocketAddress socketAddress = new UnixSocketAddress(new File(unixSocket));
      return UnixSocketChannel.open(socketAddress).socket();
    }

    DefaultConnectionInfoCache instance = getConnection(config);
    try {

      SSLSocket socket = instance.createSslSocket(this.refreshTimeoutMs);

      socket.setKeepAlive(true);
      socket.setTcpNoDelay(true);

      String instanceIp = instance.getConnectionMetadata(refreshTimeoutMs).getPreferredIpAddress();

      socket.connect(new InetSocketAddress(instanceIp, serverProxyPort));
      socket.startHandshake();

      return socket;
    } catch (IOException e) {
      instance.forceRefresh();
      throw e;
    }
  }

  DefaultConnectionInfoCache getConnection(ConnectionConfig config) {
    return instances.computeIfAbsent(config, k -> createConnectionInfo(config));
  }

  private DefaultConnectionInfoCache createConnectionInfo(ConnectionConfig config) {
    return new DefaultConnectionInfoCache(
        config, adminApi, instanceCredentialFactory, executor, localKeyPair, minRefreshDelayMs);
  }

  public void close() {
    this.instances.forEach((key, c) -> c.close());
    this.instances.clear();
  }
}
