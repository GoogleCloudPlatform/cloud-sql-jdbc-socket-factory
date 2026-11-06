/*
 * Copyright 2024 Google LLC
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

import com.google.cloud.sql.CredentialFactory;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the refresh ahead cache strategy, which will load the new ConnectionInfo using a
 * background thread before its certificate expires.
 */
class RefreshAheadConnectionInfoCache implements ConnectionInfoCache {

  private final ConnectionConfig config;
  private final CloudSqlInstanceName instanceName;
  private final RefreshAheadStrategy refreshStrategy;
  private final Set<PostgresSocket.IamPrincipal> iamPrincipals = ConcurrentHashMap.newKeySet();

  /**
   * Initializes a new Cloud SQL instance based on the given connection name using the background
   * refresh strategy.
   *
   * @param config instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param connectionInfoRepository Service class for interacting with the Cloud SQL Admin API
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
  public RefreshAheadConnectionInfoCache(
      ConnectionConfig config,
      ConnectionInfoRepository connectionInfoRepository,
      CredentialFactory tokenSourceFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair,
      long minRefreshDelayMs) {
    this(
        config,
        connectionInfoRepository,
        tokenSourceFactory,
        executor,
        keyPair,
        minRefreshDelayMs,
        null);
  }

  RefreshAheadConnectionInfoCache(
      ConnectionConfig config,
      ConnectionInfoRepository connectionInfoRepository,
      CredentialFactory tokenSourceFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair,
      long minRefreshDelayMs,
      PostgresSocket.ProbeDialer probeDialer) {

    CloudSqlInstanceName instanceName =
        new CloudSqlInstanceName(config.getCloudSqlInstance(), config.getDomainName());

    this.config = config;
    this.instanceName = instanceName;

    AccessTokenSupplier accessTokenSupplier =
        DefaultAccessTokenSupplier.newInstance(config.getAuthType(), tokenSourceFactory);

    this.refreshStrategy =
        new RefreshAheadStrategy(
            config.getCloudSqlInstance(),
            executor,
            () ->
                Futures.transform(
                    connectionInfoRepository.getConnectionInfo(
                        instanceName, accessTokenSupplier, config.getAuthType(), executor, keyPair),
                    info -> {
                      PostgresSocket.probeConnection(
                          config, instanceName, info, probeDialer, iamPrincipals);
                      return info;
                    },
                    MoreExecutors.directExecutor()),
            new AsyncRateLimiter(minRefreshDelayMs));
  }

  @Override
  public ConnectionMetadata getConnectionMetadata(long timeoutMs) {
    return refreshStrategy.getConnectionInfo(timeoutMs).toConnectionMetadata(config, instanceName);
  }

  @Override
  public void forceRefresh() {
    refreshStrategy.forceRefresh();
  }

  @Override
  public void refreshIfExpired() {
    refreshStrategy.refreshIfExpired();
  }

  @Override
  public void close() {
    refreshStrategy.close();
  }

  @Override
  public boolean isClosed() {
    return refreshStrategy.isClosed();
  }

  public RefreshAheadStrategy getRefreshStrategy() {
    return refreshStrategy;
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }

  @Override
  public ConnectionConfig getConfig() {
    return config;
  }

  @Override
  public void recordIamPrincipal(String user, String database) {
    if (user == null || user.isEmpty()) {
      return;
    }
    iamPrincipals.add(new PostgresSocket.IamPrincipal(user, database));
  }
}
