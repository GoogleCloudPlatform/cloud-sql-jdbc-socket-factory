/*
 * Copyright 2016 Google Inc.
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

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import com.google.cloud.sql.IpType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocket;

/**
 * This class manages information on and creates connections to a Cloud SQL instance using the Cloud
 * SQL Admin API. The operations to retrieve information with the API are largely done
 * asynchronously, and this class should be considered threadsafe.
 */
class DefaultConnectionInfoCache {
  private final AccessTokenSupplier accessTokenSupplier;
  private final CloudSqlInstanceName instanceName;
  private final Refresher refresher;
  private final ConnectionConfig config;

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param config instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param connectionInfoRepository Service class for interacting with the Cloud SQL Admin API
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
  DefaultConnectionInfoCache(
      ConnectionConfig config,
      ConnectionInfoRepository connectionInfoRepository,
      CredentialFactory tokenSourceFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair,
      long minRefreshDelayMs) {
    this.instanceName = new CloudSqlInstanceName(config.getCloudSqlInstance());
    this.config = config;

    if (config.getAuthType() == AuthType.IAM) {
      this.accessTokenSupplier = new DefaultAccessTokenSupplier(tokenSourceFactory);
    } else {
      this.accessTokenSupplier = Optional::empty;
    }

    // Initialize the data refresher to retrieve instance data.
    refresher =
        new Refresher(
            config.getCloudSqlInstance(),
            executor,
            () ->
                connectionInfoRepository.getConnectionInfo(
                    this.instanceName,
                    this.accessTokenSupplier,
                    config.getAuthType(),
                    executor,
                    keyPair),
            new AsyncRateLimiter(minRefreshDelayMs));
  }

  /**
   * Returns the current data related to the instance. May block if no valid data is currently
   * available. This method is called by an application thread when it is trying to create a new
   * connection to the database. (It is not called by a ListeningScheduledExecutorService task.) So
   * it is OK to block waiting for a future to complete.
   *
   * <p>When no refresh attempt is in progress, this returns immediately. Otherwise, it waits up to
   * timeoutMs milliseconds. If a refresh attempt succeeds, returns immediately at the end of that
   * successful attempt. If no attempts succeed within the timeout, throws a RuntimeException with
   * the exception from the last failed refresh attempt as the cause.
   */
  private ConnectionInfo getConnectionInfo(long timeoutMs) {
    return refresher.getConnectionInfo(timeoutMs);
  }

  /**
   * Returns an unconnected {@link SSLSocket} using the SSLContext associated with the instance. May
   * block until required instance data is available.
   */
  SSLSocket createSslSocket(long timeoutMs) throws IOException {
    return (SSLSocket)
        getConnectionInfo(timeoutMs).getSslContext().getSocketFactory().createSocket();
  }

  /**
   * Returns metadata needed to create a connection to the instance.
   *
   * @return returns ConnectionMetadata containing the preferred IP and SSL connection data.
   * @throws IllegalArgumentException If the instance has no IP addresses matching the provided
   *     preferences.
   */
  ConnectionMetadata getConnectionMetadata(long timeoutMs) {
    ConnectionInfo info = getConnectionInfo(timeoutMs);
    String preferredIp = null;

    for (IpType ipType : config.getIpTypes()) {
      preferredIp = info.getIpAddrs().get(ipType);
      if (preferredIp != null) {
        break;
      }
    }
    if (preferredIp == null) {
      throw new IllegalArgumentException(
          String.format(
              "[%s] Cloud SQL instance  does not have any IP addresses matching preferences (%s)",
              instanceName.getConnectionName(),
              config.getIpTypes().stream().map(IpType::toString).collect(Collectors.joining(","))));
    }

    return new ConnectionMetadata(
        preferredIp,
        info.getSslData().getKeyManagerFactory(),
        info.getSslData().getTrustManagerFactory());
  }

  void forceRefresh() {
    this.refresher.forceRefresh();
  }

  ListenableFuture<ConnectionInfo> getNext() {
    return refresher.getNext();
  }

  ListenableFuture<ConnectionInfo> getCurrent() {
    return refresher.getCurrent();
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }

  void close() {
    refresher.close();
  }
}
