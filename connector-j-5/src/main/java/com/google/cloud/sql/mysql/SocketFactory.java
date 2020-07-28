/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

/**
 * A MySQL {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance using
 * ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link CoreSocketFactory} class.
 */
public class SocketFactory implements com.mysql.jdbc.SocketFactory {

  private Socket socket;

  static {
    CoreSocketFactory.addArtifactId("mysql-socket-factory-connector-j-5");
  }


  @Override
  public Socket connect(String hostname, int portNumber, Properties props) throws IOException {
    socket = CoreSocketFactory.connect(props);
    return socket;
  }

  // Cloud SQL sockets always use TLS and the socket returned by connect above is already TLS-ready.
  // It is fine to implement these as no-ops.
  @Override
  public Socket beforeHandshake() {
    return socket;
  }

  @Override
  public Socket afterHandshake() {
    return socket;
  }
}
