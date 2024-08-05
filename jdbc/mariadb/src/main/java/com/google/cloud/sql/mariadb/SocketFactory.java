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

package com.google.cloud.sql.mariadb;

import com.google.cloud.sql.core.ConnectionConfig;
import com.google.cloud.sql.core.InternalConnectorRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.util.ConfigurableSocketFactory;

/**
 * A MariaDB {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance
 * using ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link InternalConnectorRegistry} class.
 */
public class SocketFactory extends ConfigurableSocketFactory {

  static {
    InternalConnectorRegistry.addArtifactId("mariadb-socket-factory");
  }

  private Configuration conf;
  private String host;

  public SocketFactory() {}

  @Override
  public void setConfiguration(Configuration conf, String host) {
    // Ignore the hostname
    this.conf = conf;
    this.host = host;
  }

  @Override
  public Socket createSocket() throws IOException {
    try {
      return InternalConnectorRegistry.getInstance()
          .connect(ConnectionConfig.fromConnectionProperties(conf.nonMappedOptions(), host));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Socket createSocket(String s, int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) {
    throw new UnsupportedOperationException();
  }
}
