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
import com.google.cloud.sql.RefreshStrategy;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  private final ConcurrentHashMap<ConnectionConfig, ConnectionInfoCache> instances =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<ConnectionConfig, Collection<Socket>> socketsByConfig =
      new ConcurrentHashMap<>();
  private final int serverProxyPort;
  private final ConnectorConfig config;

  private final InstanceConnectionNameResolver instanceNameResolver;
  private final Timer instanceNameResolverTimer;

  Connector(
      ConnectorConfig config,
      ConnectionInfoRepositoryFactory connectionInfoRepositoryFactory,
      CredentialFactory instanceCredentialFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> localKeyPair,
      long minRefreshDelayMs,
      long refreshTimeoutMs,
      int serverProxyPort,
      InstanceConnectionNameResolver instanceNameResolver) {
    this.config = config;

    this.adminApi =
        connectionInfoRepositoryFactory.create(instanceCredentialFactory.create(), config);
    this.instanceCredentialFactory = instanceCredentialFactory;
    this.executor = executor;
    this.localKeyPair = localKeyPair;
    this.minRefreshDelayMs = minRefreshDelayMs;
    this.serverProxyPort = serverProxyPort;
    this.instanceNameResolver = instanceNameResolver;
    this.instanceNameResolverTimer = new Timer("InstanceNameResolverTimer", true);
    // every 30 seconds, poll DNS records for changes.
    this.instanceNameResolverTimer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            checkDomainNames();
          }
        },
        30000,
        30000);
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

    ConnectionInfoCache instance = getConnection(config);
    try {
      ConnectionMetadata metadata = instance.getConnectionMetadata(timeoutMs);
      String instanceIp = metadata.getPreferredIpAddress();
      logger.debug(String.format("[%s] Connecting to instance.", instanceIp));

      SSLSocket socket = (SSLSocket) metadata.getSslContext().getSocketFactory().createSocket();
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
      if (hasDomain(config)) {
        this.socketsByConfig.compute(
            instance.getConfig(),
            (cfg, sockets) -> {
              if (sockets == null) {
                sockets = new ArrayList();
              }
              sockets.add(socket);
              return sockets;
            });
      }

      return socket;
    } catch (IOException e) {
      logger.debug(
          String.format(
              "[%s] Socket connection failed! Trigger a refresh.", config.getCloudSqlInstance()));
      instance.forceRefresh();
      throw e;
    }
  }

  private boolean hasDomain(ConnectionConfig config) {
    return config.getDomainName() != null && !config.getDomainName().isEmpty();
  }

  ConnectionInfoCache getConnection(final ConnectionConfig config) {
    final ConnectionConfig updatedConfig = resolveConnectionName(config);

    ConnectionInfoCache instance =
        instances.computeIfAbsent(updatedConfig, k -> createConnectionInfo(updatedConfig));

    closeCachesWithSameDomain(updatedConfig);

    // If the client certificate has expired (as when the computer goes to
    // sleep, and the refresh cycle cannot run), force a refresh immediately.
    // The TLS handshake will not fail on an expired client certificate. It's
    // not until the first read where the client cert error will be surfaced.
    // So check that the certificate is valid before proceeding.
    instance.refreshIfExpired();

    return instance;
  }

  /**
   * Updates the ConnectionConfig to ensure that the cloudSqlInstance field is set, resolving the
   * domainName using the InstanceNameResolver.
   *
   * @param config the configuration to resolve.
   * @return a ConnectionConfig guaranteed to have the CloudSqlInstance field set.
   */
  private ConnectionConfig resolveConnectionName(ConnectionConfig config) {
    // If domainName is not set, return the original configuration unmodified.
    if (config.getDomainName() == null || config.getDomainName().isEmpty()) {
      return config;
    }

    // If both domainName and cloudSqlInstance are set, ignore the domain name. Return a new
    // configuration with domainName set to null.
    if (config.getCloudSqlInstance() != null && !config.getCloudSqlInstance().isEmpty()) {
      return config.withDomainName(null);
    }

    // If both domainName and cloudSqlInstance are set, ignore the domain name. Return a new
    // configuration with domainName set to null.
    if (config.getCloudSqlInstance() != null && !config.getCloudSqlInstance().isEmpty()) {
      return config.withDomainName(null);
    }

    // If only domainName is set, resolve the domain name.
    // Resolve the domain name.
    try {
      final String unresolvedName = config.getDomainName();
      final CloudSqlInstanceName name;
      final Function<String, String> resolver =
          config.getConnectorConfig().getInstanceNameResolver();
      if (resolver != null) {
        name = instanceNameResolver.resolve(resolver.apply(unresolvedName));
      } else {
        name = instanceNameResolver.resolve(unresolvedName);
      }
      return config.withCloudSqlInstance(name.getConnectionName());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Cloud SQL connection name is invalid: \"%s\"", config.getDomainName()), e);
    }
  }

  private ConnectionInfoCache createConnectionInfo(ConnectionConfig config) {
    logger.debug(
        String.format("[%s] Connection info added to cache.", config.getCloudSqlInstance()));
    if (config.getConnectorConfig().getRefreshStrategy() == RefreshStrategy.LAZY) {
      // Resolve the key operation immediately.
      KeyPair keyPair = null;
      try {
        keyPair = localKeyPair.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new RuntimeException(e);
      }
      return new LazyRefreshConnectionInfoCache(
          config, adminApi, instanceCredentialFactory, keyPair);

    } else {
      return new RefreshAheadConnectionInfoCache(
          config, adminApi, instanceCredentialFactory, executor, localKeyPair, minRefreshDelayMs);
    }
  }

  public void close() {
    logger.debug("Close all connections and remove them from cache.");
    this.instances.forEach((key, c) -> c.close());
    this.instances.clear();
  }

  private void checkDomainNames() {
    instances.entrySet().stream()
        // filter for all instance caches configured with domain names
        .filter(entry -> hasDomain(entry.getKey()))
        .forEach(
            entry -> {
              // Resolve the connection name again.
              ConnectionConfig updatedConfig = resolveConnectionName(entry.getKey());

              // Close the cache if it has the same domain name.
              closeCachesWithSameDomain(updatedConfig);

              // Remove closed sockets from the Connector's list of domain sockets.
              socketsByConfig.computeIfPresent(
                  entry.getKey(),
                  (cfg, sockets) ->
                      sockets.stream().filter(s -> !s.isClosed()).collect(Collectors.toList()));
            });
  }

  private boolean closeCachesWithSameDomain(ConnectionConfig config) {
    if (!hasDomain(config)) {
      return false;
    }
    long closedCaches =
        instances.entrySet().stream()
            // Filter to instances that have the same domain, but a different config, in other words
            // different instance name or connection properties.
            .filter(
                entry ->
                    hasDomain(entry.getKey())
                        && entry.getKey().getDomainName().equals(config.getDomainName())
                        && !entry.getKey().equals(config))
            .map(
                entry -> {
                  logger.info(
                      "Cloud SQL Instance associated with domain name {} changed from {} to {}.",
                      entry.getKey().getDomainName(),
                      entry.getKey().getCloudSqlInstance(),
                      config.getDomainName());
                  // Safely remove this cache entry, only if it still has the same value
                  // and close the cache.
                  this.instances.remove(entry.getKey(), entry.getValue());
                  Collection<Socket> sockets = socketsByConfig.remove(entry.getKey());
                  entry.getValue().close();

                  if (sockets != null) {
                    sockets.forEach(
                        s -> {
                          if (!s.isClosed()) {
                            try {
                              s.close();
                            } catch (IOException e) {
                              logger.debug(
                                  "Unable to close socket when domain {} changed from "
                                      + "instance {} to {} value changed",
                                  config.getDomainName(),
                                  entry.getKey().getCloudSqlInstance(),
                                  config.getCloudSqlInstance(),
                                  e);
                            }
                          }
                        });
                  }
                  return entry.getValue();
                })
            .count();
    return closedCaches > 0;
  }
}
