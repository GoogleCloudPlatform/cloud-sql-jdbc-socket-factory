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
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
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

  private final ConcurrentHashMap<ConnectionConfig, MonitoredCache> instances =
      new ConcurrentHashMap<>();
  private final int serverProxyPort;
  private final ConnectorConfig config;

  private final InstanceConnectionNameResolver instanceNameResolver;
  private final DnsResolver dnsResolver;
  private final Timer instanceNameResolverTimer;
  private final ProtocolHandler mdxProtocolHandler;

  Connector(
      ConnectorConfig config,
      ConnectionInfoRepositoryFactory connectionInfoRepositoryFactory,
      CredentialFactory instanceCredentialFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> localKeyPair,
      long minRefreshDelayMs,
      long refreshTimeoutMs,
      int serverProxyPort,
      InstanceConnectionNameResolver instanceNameResolver,
      DnsResolver dnsResolver,
      ProtocolHandler mdxProtocolHandler) {
    this.config = config;
    this.adminApi =
        connectionInfoRepositoryFactory.create(instanceCredentialFactory.create(), config);
    this.instanceCredentialFactory = instanceCredentialFactory;
    this.executor = executor;
    this.localKeyPair = localKeyPair;
    this.minRefreshDelayMs = minRefreshDelayMs;
    this.serverProxyPort = serverProxyPort;
    this.instanceNameResolver = instanceNameResolver;
    this.dnsResolver = dnsResolver;
    this.instanceNameResolverTimer = new Timer("InstanceNameResolverTimer", true);
    this.mdxProtocolHandler = mdxProtocolHandler;
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

    MonitoredCache instance = getConnection(config);
    try {
      ConnectionMetadata metadata = instance.getConnectionMetadata(timeoutMs);
      String instanceIp = metadata.getPreferredIpAddress();

      // If a domain name was used to connect, resolve it to an IP address
      if (!Strings.isNullOrEmpty(instance.getConfig().getDomainName())) {
        try {
          List<InetAddress> addrs = dnsResolver.resolveHost(instance.getConfig().getDomainName());
          if (addrs != null && !addrs.isEmpty()) {
            logger.debug(
                String.format(
                    "[%s] custom DNS name %s resolved to %s, using it to connect",
                    instance.getConfig().getCloudSqlInstance(),
                    instance.getConfig().getDomainName(),
                    addrs.get(0).getHostAddress()));
            instanceIp = addrs.get(0).getHostAddress();
          } else {
            logger.debug(
                String.format(
                    "[%s] custom DNS name %s resolved but returned no entries, using %s from"
                        + " instance metadata",
                    instance.getConfig().getCloudSqlInstance(),
                    instance.getConfig().getDomainName(),
                    instanceIp));
          }
        } catch (UnknownHostException e) {
          logger.debug(
              String.format(
                  "[%s] custom DNS name %s did not resolve to an IP address: %s, using %s from"
                      + " instance metadata",
                  instance.getConfig().getCloudSqlInstance(),
                  instance.getConfig().getDomainName(),
                  e.getMessage(),
                  instanceIp));
        }
      }

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

      if (metadata.isMdxClientProtocolTypeSupport()
          && !Strings.isNullOrEmpty(config.getMdxClientProtocolType())) {
        socket = mdxProtocolHandler.connect(socket, config.getMdxClientProtocolType());
      }

      logger.debug(String.format("[%s] Connected to instance successfully.", instanceIp));
      instance.addSocket(socket);

      return socket;
    } catch (IOException e) {
      logger.debug(
          String.format(
              "[%s] Socket connection failed! Trigger a refresh.", config.getCloudSqlInstance()));
      instance.forceRefresh();
      throw e;
    }
  }

  MonitoredCache getConnection(final ConnectionConfig config) {
    final ConnectionConfig updatedConfig = resolveConnectionName(config);

    // If the cache entry doesn't exist, or if the cache entry is closed,
    // replace it.
    MonitoredCache instance =
        instances.compute(
            updatedConfig,
            (k, v) ->
                v != null && !v.isClosed()
                    ? v
                    : new MonitoredCache(
                        createConnectionInfo(updatedConfig),
                        instanceNameResolverTimer,
                        this::resolveDomain));

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
    if (Strings.isNullOrEmpty(config.getDomainName())) {
      return config;
    }

    // If both domainName and cloudSqlInstance are set, ignore the domain name. Return a new
    // configuration with domainName set to null.
    if (!Strings.isNullOrEmpty(config.getCloudSqlInstance())) {
      return config.withDomainName(null);
    }

    // If only domainName is set, resolve the domain name.
    try {
      final CloudSqlInstanceName name = resolveDomain(config);
      return config.withCloudSqlInstance(name.getConnectionName());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format("Cloud SQL connection name is invalid: \"%s\"", config.getDomainName()), e);
    }
  }

  private CloudSqlInstanceName resolveDomain(ConnectionConfig config) {
    final String unresolvedName = config.getDomainName();
    final CloudSqlInstanceName name;
    final Function<String, String> resolver = config.getConnectorConfig().getInstanceNameResolver();
    if (resolver != null) {
      name = instanceNameResolver.resolve(resolver.apply(unresolvedName));
    } else {
      name = instanceNameResolver.resolve(unresolvedName);
    }
    return name;
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
    this.instanceNameResolverTimer.cancel();
    this.instances.forEach((key, c) -> c.close());
    this.instances.clear();
  }
}
