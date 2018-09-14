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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.sql.core.SslSocketFactory;
import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;

/**
 * A MySQL {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance using
 * ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link SslSocketFactory} class.
 */
public class SocketFactory implements com.mysql.jdbc.SocketFactory {
  private static final Logger logger = Logger.getLogger(SocketFactory.class.getName());
  private static final String CloudSqlPrefix = "/cloudsql/";

  private Socket socket;

  @Override
  public Socket connect(String hostname, int portNumber, Properties props) throws IOException {
    String instanceName = props.getProperty("cloudSqlInstance");
    checkArgument(
        instanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or "
            + "the connection Properties with value in form \"project:region:instance\"");

    // gaeEnv="standard" indicates standard instances
    // runEnv="Production" indicates production instances
    String gaeEnv = System.getenv("GAE_ENV");
    String runEnv = System.getProperty("com.google.appengine.runtime.environment");
    // Custom env variable for forcing unix socket
    Boolean forceUnixSocket = System.getenv("CLOUD_SQL_FORCE_UNIX_SOCKET") != null;

    // If running on GAE Standard, connect with unix socket
    if (forceUnixSocket || "standard".equals(gaeEnv) && "Production".equals(runEnv)) {
      logger.info(String.format(
          "Connecting to Cloud SQL instance [%s] via unix socket.", instanceName));
      UnixSocketAddress socketAddress = new UnixSocketAddress(
          new File(CloudSqlPrefix + instanceName));
      this.socket = UnixSocketChannel.open(socketAddress).socket();
    } else {
      // Default to SSL Socket
      logger.info(String.format(
          "Connecting to Cloud SQL instance [%s] via ssl socket.", instanceName));
      List<String> ipTypes =
          SslSocketFactory.listIpTypes(
              props.getProperty("ipTypes", SslSocketFactory.DEFAULT_IP_TYPES));
      this.socket = SslSocketFactory.getInstance().create(instanceName, ipTypes);
    }
    return this.socket;
  }

  // Cloud SQL sockets always use TLS and the socket returned by connect above is already TLS-ready. It is fine
  // to implement these as no-ops.

  @Override
  public Socket beforeHandshake() {
    return socket;
  }

  @Override
  public Socket afterHandshake() {
    return socket;
  }
}
