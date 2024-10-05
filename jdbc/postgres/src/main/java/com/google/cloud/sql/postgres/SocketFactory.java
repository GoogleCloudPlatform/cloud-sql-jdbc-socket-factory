/*
 * Copyright 2016 Google Inc.
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

import com.google.cloud.sql.core.ConnectionConfig;
import com.google.cloud.sql.core.InternalConnectorRegistry;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Postgres {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance
 * using ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link InternalConnectorRegistry} class.
 */
public class SocketFactory extends javax.net.SocketFactory {

  private static final Logger logger = LoggerFactory.getLogger(SocketFactory.class);

  private static final String DEPRECATED_SOCKET_ARG = "SocketFactoryArg";
  private static final String POSTGRES_SUFFIX = "/.s.PGSQL.5432";

  /** The connection property containing the hostname from the JDBC url. */
  private static final String POSTGRES_HOST_PROP = "PGHOST";

  private final Properties props;

  static {
    InternalConnectorRegistry.addArtifactId("postgres-socket-factory");
  }

  /**
   * Implements the {@link SocketFactory} constructor, which can be used to create authenticated
   * connections to a Cloud SQL instance.
   */
  public SocketFactory(Properties info) {
    String oldInstanceKey = info.getProperty(DEPRECATED_SOCKET_ARG);
    if (oldInstanceKey != null) {
      logger.debug(
          String.format(
              "The '%s' property has been deprecated. Please update your postgres driver and use"
                  + "the  '%s' property in your JDBC url instead.",
              DEPRECATED_SOCKET_ARG, ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY));
      info.setProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY, oldInstanceKey);
    }
    this.props = info;
  }

  @Deprecated
  public SocketFactory(String instanceName) {
    // Deprecated constructor for converting 'SocketFactoryArg' to 'CloudSqlInstance'
    this(createDefaultProperties(instanceName));
  }

  private static Properties createDefaultProperties(String instanceName) {
    Properties info = new Properties();
    info.setProperty(DEPRECATED_SOCKET_ARG, instanceName);
    info.setProperty(ConnectionConfig.UNIX_SOCKET_PATH_SUFFIX_PROPERTY, POSTGRES_SUFFIX);
    return info;
  }

  @Override
  public Socket createSocket() throws IOException {
    try {
      return InternalConnectorRegistry.getInstance()
          .connect(
              ConnectionConfig.fromConnectionProperties(
                  props, props.getProperty(POSTGRES_HOST_PROP)));
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
