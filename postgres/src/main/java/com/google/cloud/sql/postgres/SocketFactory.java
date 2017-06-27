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

package com.google.cloud.sql.postgres;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.sql.core.SslSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * A Postgres {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance
 * using ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link SslSocketFactory} class.
 */
public class SocketFactory extends javax.net.SocketFactory {
  private static final Logger logger = Logger.getLogger(SocketFactory.class.getName());

  private final String instanceName;

  public SocketFactory(String instanceName) {
    checkArgument(
        instanceName != null,
        "socketFactoryArg property not set. Please specify this property in the JDBC "
            + "URL or the connection Properties with the instance connection name in "
            + "form \"project:region:instance\"");
    this.instanceName = instanceName;
  }

  @Override
  public Socket createSocket() throws IOException {
    logger.info(String.format("Connecting to Cloud SQL instance [%s].", instanceName));
    return SslSocketFactory.getInstance().create(instanceName);
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
