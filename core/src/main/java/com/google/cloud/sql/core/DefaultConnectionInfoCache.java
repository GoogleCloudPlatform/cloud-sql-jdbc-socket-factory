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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.net.ssl.SSLSocket;

/**
 * This class manages information on and creates connections to a Cloud SQL instance using the Cloud
 * SQL Admin API. The operations to retrieve information with the API are largely done
 * asynchronously, and this class should be considered threadsafe.
 */
class DefaultConnectionInfoCache {
  private final AuthType authType;
  private final AccessTokenSupplier accessTokenSupplier;
  private final CloudSqlInstanceName instanceName;

  private final Refresher refresher;

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param connectionInfoRepository Service class for interacting with the Cloud SQL Admin API
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
  DefaultConnectionInfoCache(
      String connectionName,
      ConnectionInfoRepository connectionInfoRepository,
      AuthType authType,
      CredentialFactory tokenSourceFactory,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair,
      long minRefreshDelayMs) {
    this.instanceName = new CloudSqlInstanceName(connectionName);
    this.authType = authType;

    if (authType == AuthType.IAM) {
      this.accessTokenSupplier = new DefaultAccessTokenSupplier(tokenSourceFactory);
    } else {
      this.accessTokenSupplier = Optional::empty;
    }

    // Initialize the data refresher to retrieve instance data.
    refresher =
        new Refresher(
            connectionName,
            executor,
            () ->
                connectionInfoRepository.getInstanceData(
                    this.instanceName, this.accessTokenSupplier, this.authType, executor, keyPair),
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
  private InstanceData getInstanceData(long timeoutMs) {
    return refresher.getData(timeoutMs);
  }

  /** Returns SslData to establish mTLS connections. */
  SslData getSslData(long timeoutMs) {
    return getInstanceData(timeoutMs).getSslData();
  }

  /**
   * Returns an unconnected {@link SSLSocket} using the SSLContext associated with the instance. May
   * block until required instance data is available.
   */
  SSLSocket createSslSocket(long timeoutMs) throws IOException {
    return (SSLSocket) getInstanceData(timeoutMs).getSslContext().getSocketFactory().createSocket();
  }

  /**
   * Returns the first IP address for the instance, in order of the preference supplied by
   * preferredTypes.
   *
   * @param preferredTypes Preferred instance IP types to use. Valid IP types include "Public" and
   *     "Private".
   * @return returns a string representing the IP address for the instance
   * @throws IllegalArgumentException If the instance has no IP addresses matching the provided
   *     preferences.
   */
  String getPreferredIp(List<IpType> preferredTypes, long timeoutMs) {
    Map<IpType, String> ipAddrs = getInstanceData(timeoutMs).getIpAddrs();
    for (IpType ipType : preferredTypes) {
      String preferredIp = ipAddrs.get(ipType);
      if (preferredIp != null) {
        return preferredIp;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "[%s] Cloud SQL instance  does not have any IP addresses matching preferences (%s)",
            instanceName.getConnectionName(),
            preferredTypes.stream().map(IpType::toString).collect(Collectors.joining(","))));
  }

  void forceRefresh() {
    this.refresher.forceRefresh();
  }

  ListenableFuture<InstanceData> getNext() {
    return refresher.getNext();
  }

  ListenableFuture<InstanceData> getCurrent() {
    return refresher.getCurrent();
  }

  public CloudSqlInstanceName getInstanceName() {
    return instanceName;
  }
}
