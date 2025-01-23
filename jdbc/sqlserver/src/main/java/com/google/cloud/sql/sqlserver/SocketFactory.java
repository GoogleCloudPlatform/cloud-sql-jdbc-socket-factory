/*
 * Copyright 2020 Google Inc.
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

package com.google.cloud.sql.sqlserver;

import com.google.cloud.sql.core.CloudSqlInstanceName;
import com.google.cloud.sql.core.ConnectionConfig;
import com.google.cloud.sql.core.InternalConnectorRegistry;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.List;
import java.util.Properties;

/**
 * A Microsoft SQL Server {@link SocketFactory} that establishes a secure connection to a Cloud SQL
 * instance using ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link InternalConnectorRegistry} class.
 */
public class SocketFactory extends javax.net.SocketFactory {

  static {
    InternalConnectorRegistry.addArtifactId("cloud-sql-connector-jdbc-sqlserver");
  }

  // props are protected, not private, so that they can be accessed from unit tests
  @VisibleForTesting protected Properties props = new Properties();
  @VisibleForTesting protected String domainName;

  /**
   * Implements the {@link SocketFactory} constructor, which can be used to create authenticated
   * connections to a Cloud SQL instance.
   */
  public SocketFactory(String socketFactoryConstructorArg) throws UnsupportedEncodingException {
    List<String> s = Splitter.on('?').splitToList(socketFactoryConstructorArg);
    final String instanceOrDomainName = s.get(0);
    if (CloudSqlInstanceName.isValidInstanceName(instanceOrDomainName)) {
      this.props.setProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY, instanceOrDomainName);
    } else {
      domainName = instanceOrDomainName;
    }
    if (s.size() == 2 && s.get(1).length() > 0) {
      Iterable<String> queryParams = Splitter.on('&').split(s.get(1));
      for (String param : queryParams) {
        List<String> splitParam = Splitter.on('=').splitToList(param);
        if (splitParam.size() != 2
            || splitParam.get(0).length() == 0
            || splitParam.get(1).length() == 0) {
          throw new IllegalArgumentException(
              String.format("Malformed query param in socketFactoryConstructorArg : %s", param));
        }
        this.props.setProperty(
            URLDecoder.decode(splitParam.get(0), "utf-8"),
            URLDecoder.decode(splitParam.get(1), "utf-8"));
      }
    } else if (s.size() > 2) {
      throw new IllegalArgumentException(
          "Only one query string allowed in socketFactoryConstructorArg");
    }
  }

  @Override
  public Socket createSocket() throws IOException {
    try {
      return InternalConnectorRegistry.getInstance()
          .connect(ConnectionConfig.fromConnectionProperties(props, domainName));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public Socket createSocket(String host, int port) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress host, int port) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(
      InetAddress address, int port, InetAddress localAddress, int localPort) {
    throw new UnsupportedOperationException();
  }
}
