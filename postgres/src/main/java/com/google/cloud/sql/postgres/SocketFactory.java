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

import com.google.cloud.sql.core.CoreSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * A Postgres {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance
 * using ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link CoreSocketFactory} class.
 */
public class SocketFactory extends javax.net.SocketFactory {

  private static final Logger logger = Logger.getLogger(SocketFactory.class.getName());

  private static final String CLOUD_SQL_PREFIX = "/cloudsql/";
  private static final String POSTGRES_SUFFIX = "/.s.PGSQL.5432";

  private static final String INSTANCE_PROPERTY_KEY = "cloudSqlInstance";
  private static final String IP_TYPES_KEY = "ipTypes";

  private final String instanceName;
  private final List<String> ipTypes;

  // TODO(kurtisvg): add more details to this javadoc.
  /** Implements the SocketFactory constructor for the postgres connector. */
  public SocketFactory(Properties info) {
    this.instanceName = info.getProperty(INSTANCE_PROPERTY_KEY);
    checkArgument(
        this.instanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or "
            + "the connection Properties with value in form \"project:region:instance\"");

    this.ipTypes =
        CoreSocketFactory.listIpTypes(
            info.getProperty(IP_TYPES_KEY, CoreSocketFactory.DEFAULT_IP_TYPES));
  }

  @Deprecated
  public SocketFactory(String instanceName) {
    // Deprecated constructor for converting 'SocketFactoryArg' to 'CloudSqlInstance'
    this(createDefaultProperties(instanceName));
  }

  private static Properties createDefaultProperties(String instanceName) {
    Properties info = new Properties();
    info.setProperty(INSTANCE_PROPERTY_KEY, instanceName);
    return info;
  }

  @Override
  public Socket createSocket() throws IOException {
    // Custom env variable for forcing unix socket
    boolean forceUnixSocket = System.getenv("CLOUD_SQL_FORCE_UNIX_SOCKET") != null;

    // If running on GAE Standard, connect with unix socket
    if (forceUnixSocket || runningOnGaeStandard()) {
      logger.info(
          String.format("Connecting to Cloud SQL instance [%s] via unix socket.", instanceName));
      UnixSocketAddress socketAddress =
          new UnixSocketAddress(new File(CLOUD_SQL_PREFIX + instanceName + POSTGRES_SUFFIX));
      return UnixSocketChannel.open(socketAddress).socket();
    }
    // Default to SSL Socket
    logger.info(
        String.format("Connecting to Cloud SQL instance [%s] via ssl socket.", instanceName));
    return CoreSocketFactory.getInstance().createSslSocket(instanceName, ipTypes);
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

  /** Returns {@code true} if running in a Google App Engine Standard runtime. */
  // TODO(kurtisvg) move this check into a shared class
  private boolean runningOnGaeStandard() {
    // gaeEnv="standard" indicates standard instances
    String gaeEnv = System.getenv("GAE_ENV");
    // runEnv="Production" requires to rule out Java 8 emulated environments
    String runEnv = System.getProperty("com.google.appengine.runtime.environment");
    // gaeRuntime="java11" in Java 11 environments (no emulated environments)
    String gaeRuntime = System.getenv("GAE_RUNTIME");

    return "standard".equals(gaeEnv)
        && ("Production".equals(runEnv) || "java11".equals(gaeRuntime));
  }
}
