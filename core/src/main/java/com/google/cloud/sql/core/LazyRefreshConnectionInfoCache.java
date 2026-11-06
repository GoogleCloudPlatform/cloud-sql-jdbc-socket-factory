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

import static com.google.cloud.sql.core.RefreshCalculator.DEFAULT_REFRESH_BUFFER;

import com.google.cloud.sql.CredentialFactory;
import java.security.KeyPair;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements the lazy refresh cache strategy, which loads the new certificate as needed during a
 * request for a new connection.
 */
class LazyRefreshConnectionInfoCache implements ConnectionInfoCache {
  private final ConnectionConfig config;
  private final CloudSqlInstanceName instanceName;
  private final Set<PostgresSocket.IamPrincipal> iamPrincipals = ConcurrentHashMap.newKeySet();

  private final LazyRefreshStrategy refreshStrategy;

  /**
   * Initializes a new Cloud SQL instance based on the given connection name using the lazy refresh
   * strategy.
   *
   * @param config instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param connectionInfoRepository Service class for interacting with the Cloud SQL Admin API
   * @param keyPair public/private key pair used to authenticate connections
   */
  public LazyRefreshConnectionInfoCache(
      ConnectionConfig config,
      ConnectionInfoRepository connectionInfoRepository,
      CredentialFactory tokenSourceFactory,
      KeyPair keyPair) {
    this(config, connectionInfoRepository, tokenSourceFactory, keyPair, null);
  }

  LazyRefreshConnectionInfoCache(
      ConnectionConfig config,
      ConnectionInfoRepository connectionInfoRepository,
      CredentialFactory tokenSourceFactory,
      KeyPair keyPair,
      PostgresSocket.ProbeDialer probeDialer) {

    CloudSqlInstanceName instanceName =
        new CloudSqlInstanceName(config.getCloudSqlInstance(), config.getDomainName());

    this.config = config;
    this.instanceName = instanceName;

    AccessTokenSupplier accessTokenSupplier =
        DefaultAccessTokenSupplier.newInstance(config.getAuthType(), tokenSourceFactory);

    this.refreshStrategy =
        new LazyRefreshStrategy(
            config.getCloudSqlInstance(),
            () -> {
              ConnectionInfo info =
                  connectionInfoRepository.getConnectionInfoSync(
                      instanceName, accessTokenSupplier, config.getAuthType(), keyPair);
              PostgresSocket.probeConnection(
                  config, instanceName, info, probeDialer, iamPrincipals);
              return info;
            },
            DEFAULT_REFRESH_BUFFER);
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
