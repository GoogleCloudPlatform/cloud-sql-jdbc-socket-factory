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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Logger;

/**
* A Microsoft SQL Server {@link SocketFactory} that establishes a secure connection to a 
* Cloud SQL instance using ephemeral certificates.
*
* <p>The heavy lifting is done by the singleton {@link CoreSocketFactory} class.
*/
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
  public SocketFactory(String socketFactoryConstructorArg)
      throws UnsupportedEncodingException {
    String[] s = socketFactoryConstructorArg.split("\\?");
    this.props.setProperty(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY, s[0]);
    if (s.length == 2 && s[1].length() > 0) {
      String[] queryParams = s[1].split("&");
      for (String param : queryParams) {
        String[] splitParam = param.split("=");
        if (splitParam.length != 2 || splitParam[0].length() == 0 || splitParam[1].length() == 0) {
          throw new IllegalArgumentException(String.format(
              "Malformed query param in socketFactoryConstructorArg : %s", param));
        }
        this.props.setProperty(URLDecoder.decode(splitParam[0], StandardCharsets.UTF_8.name()),
            URLDecoder.decode(splitParam[1], StandardCharsets.UTF_8.name()));
      }
    } else if (s.length > 2) {
      throw new IllegalArgumentException(
          "Only one query string allowed in socketFactoryConstructorArg");
    }
  }

  @Override
  public Socket createSocket() throws IOException {
    try {
      return CoreSocketFactory.connect(props);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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


