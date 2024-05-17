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

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;

class StubConnectionInfoRepository implements ConnectionInfoRepository {
  private final AtomicInteger refreshCount = new AtomicInteger();

  int getRefreshCount() {
    return refreshCount.get();
  }

  private ConnectionInfo newConnectionInfo() {
    Map<IpType, String> ips = Collections.singletonMap(IpType.PUBLIC, "10.1.1.1");
    try {
      return new ConnectionInfo(
          new InstanceMetadata(ips, null),
          new SslData(
              null, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), null),
          Instant.now().plus(1, ChronoUnit.HOURS));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public ListenableFuture<ConnectionInfo> getConnectionInfo(
      CloudSqlInstanceName instanceName,
      AccessTokenSupplier accessTokenSupplier,
      AuthType authType,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair) {
    refreshCount.incrementAndGet();
    ConnectionInfo connectionInfo = newConnectionInfo();
    return Futures.immediateFuture(connectionInfo);
  }

  @Override
  public ConnectionInfo getConnectionInfoSync(
      CloudSqlInstanceName instanceName,
      AccessTokenSupplier accessTokenSupplier,
      AuthType authType,
      KeyPair keyPair) {
    refreshCount.incrementAndGet();
    return newConnectionInfo();
  }
}
