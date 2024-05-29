/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.cloud.sql.IpType;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.SSLContext;

/** Represents the results of a certificate and metadata refresh operation. */
class ConnectionInfo {

  private final InstanceMetadata instanceMetadata;
  private final SSLContext sslContext;
  private final SslData sslData;
  private final Instant expiration;

  ConnectionInfo(InstanceMetadata instanceMetadata, SslData sslData, Instant expiration) {
    this.instanceMetadata = instanceMetadata;
    this.sslData = sslData;
    this.sslContext = sslData.getSslContext();
    this.expiration = expiration;
  }

  public Instant getExpiration() {
    return expiration;
  }

  SSLContext getSslContext() {
    return sslContext;
  }

  Map<IpType, String> getIpAddrs() {
    return instanceMetadata.getIpAddrs();
  }

  SslData getSslData() {
    return sslData;
  }

  ConnectionMetadata toConnectionMetadata(
      ConnectionConfig config, CloudSqlInstanceName instanceName) {
    String preferredIp = null;

    for (IpType ipType : config.getIpTypes()) {
      preferredIp = getIpAddrs().get(ipType);
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
        sslData.getKeyManagerFactory(),
        sslData.getTrustManagerFactory(),
        sslData.getSslContext());
  }
}
