/*
 * Copyright 2020 Google Inc. All Rights Reserved.
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

import com.google.cloud.sql.core.CoreSocketFactory;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

public class SocketFactory extends javax.net.SocketFactory {

  private static final Logger logger = Logger.getLogger(SocketFactory.class.getName());
  // props are protected, not private, so that they can be accessed from unit tests
  @VisibleForTesting
  protected Properties props = new Properties();

  static {
    CoreSocketFactory.addArtifactId("cloud-sql-connector-jdbc-sqlserver");
  }

  /**
   * Implements the {@link SocketFactory} constructor, which can be used to create authenticated
   * connections to a Cloud SQL instance.
   */
  public SocketFactory(String socketFactoryConstructorArg) throws UnsupportedEncodingException {
    URI uri = URI.create(socketFactoryConstructorArg);
    this.props.setProperty(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY, uri.getPath());
    if (uri.getQuery() != null) {
      String[] queryParams = uri.getQuery().split("&");
      for (String param : queryParams) {
        String[] splitParam = param.split("=");
        this.props.setProperty(URLDecoder.decode(splitParam[0], StandardCharsets.UTF_8.name()),
            URLDecoder.decode(splitParam[1], StandardCharsets.UTF_8.name()));
      }
    }
  }

  @Override
  public Socket createSocket() throws IOException {
    return CoreSocketFactory.connect(props);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    throw new UnsupportedOperationException();
  }
}


