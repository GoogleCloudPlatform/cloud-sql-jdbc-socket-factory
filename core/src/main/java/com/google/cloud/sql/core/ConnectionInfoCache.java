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

import java.io.IOException;
import javax.net.ssl.SSLSocket;

/** ConnectionInfoCache is the contract for a caching strategy for ConnectionInfo. */
public interface ConnectionInfoCache {

  /**
   * Returns an unconnected {@link SSLSocket} using the SSLContext associated with the instance. May
   * block until required instance data is available.
   */
  SSLSocket createSslSocket(long timeoutMs) throws IOException;

  /**
   * Returns metadata needed to create a connection to the instance.
   *
   * @return returns ConnectionMetadata containing the preferred IP and SSL connection data.
   * @throws IllegalArgumentException If the instance has no IP addresses matching the provided
   *     preferences.
   */
  ConnectionMetadata getConnectionMetadata(long timeoutMs);

  void forceRefresh();

  void refreshIfExpired();

  RefreshStrategy getRefresher();

  CloudSqlInstanceName getInstanceName();

  void close();
}
