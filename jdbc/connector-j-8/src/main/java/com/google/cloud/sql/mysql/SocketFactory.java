/*
 * Copyright 2018 Google LLC
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

package com.google.cloud.sql.mysql;

import com.google.cloud.sql.core.CoreSocketFactory;
import com.mysql.cj.conf.PropertySet;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.SocketConnection;
import java.io.Closeable;
import java.io.IOException;
import java.util.Properties;

/**
 * A MySQL {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance using
 * ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link CoreSocketFactory} class.
 */
public class SocketFactory implements com.mysql.cj.protocol.SocketFactory {

  static {
    CoreSocketFactory.addArtifactId("mysql-socket-factory-connector-j-8");
  }

  @Override
  public <T extends Closeable> T connect(
      String host, int portNumber, PropertySet props, int loginTimeout) throws IOException {
    return connect(host, portNumber, props.exposeAsProperties(), loginTimeout);
  }

  /**
   * Implements the interface for com.mysql.cj.protocol.SocketFactory for mysql-connector-java prior
   * to version 8.0.13. This change is required for backwards compatibility.
   */
  public <T extends Closeable> T connect(
      String host, int portNumber, Properties props, int loginTimeout) throws IOException {
    @SuppressWarnings("unchecked")
    T socket = (T) CoreSocketFactory.connect(props);
    return socket;
  }

  // Cloud SQL sockets always use TLS and the socket returned by connect above is already TLS-ready.
  // It is fine to implement these as no-ops.
  @Override
  public void beforeHandshake() {
  }

  @Override
  public <T extends Closeable> T performTlsHandshake(
      SocketConnection socketConnection, ServerSession serverSession) throws IOException {
    @SuppressWarnings("unchecked")
    T socket = (T) socketConnection.getMysqlSocket();
    return socket;
  }

  @Override
  public void afterHandshake() {
  }
}
