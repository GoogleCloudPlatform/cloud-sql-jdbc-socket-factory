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

package com.google.cloud.sql.core;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

/**
 * CoreSocketFactory is the legacy public entrypoint to the Cloud SQL JDBC Socket Factory.
 *
 * <p>This class entirely delegates to ConnectorRegistry.
 *
 * <p>The API of this class is subject to change without notice.
 */
public enum CoreSocketFactory {
  INSTANCE;

  CoreSocketFactory() {}

  /**
   * Connect to the database, returning a socket based on the JDBC connection properties.
   *
   * @param props the properties
   * @return the open socket
   * @throws IOException when there is a problem connecting
   * @throws InterruptedException when interrupted while attemting to connect
   */
  public static Socket connect(Properties props) throws IOException, InterruptedException {
    return connect(new ConnectionConfig(props));
  }

  /**
   * Connect to the database, returning a socket based on the JDBC connection properties.
   *
   * @param props the properties
   * @param unixPathSuffix the suffix to ensure is on the end of the unix socket path, for postgres.
   * @return the open socket
   * @throws IOException when there is a problem connecting
   * @throws InterruptedException when interrupted while attemting to connect
   */
  public static Socket connect(Properties props, String unixPathSuffix)
      throws IOException, InterruptedException {
    props.setProperty(ConnectionConfig.UNIX_SOCKET_PATH_SUFFIX, unixPathSuffix);
    return connect(new ConnectionConfig(props));
  }

  /**
   * Connect to the database, returning a socket based on the JDBC connection properties.
   *
   * @param config the connection configuration
   * @return the open socket
   * @throws IOException when there is a problem connecting
   * @throws InterruptedException when interrupted while attemting to connect
   */
  public static Socket connect(ConnectionConfig config) throws IOException, InterruptedException {
    return ConnectorRegistry.INSTANCE.getConnector().connect(config);
  }

  /**
   * Prepare SSL configuration data for the connection to the database.
   *
   * @param config the configuration
   * @return SSL context and other data needed to create a new socket
   * @throws IOException when there is a problem connecting to the Google Cloud API
   */
  public static SslData getSslData(ConnectionConfig config) throws IOException {
    return ConnectorRegistry.INSTANCE.getConnector().getSslData(config);
  }

  /**
   * Get the IP address to connect to the database.
   *
   * @param config the configuration
   * @return SSL context and other data needed to create a new socket
   * @throws IOException when there is a problem connecting to the Google Cloud API
   */
  public static String getHostIp(ConnectionConfig config) throws IOException {
    return ConnectorRegistry.INSTANCE.getConnector().getPreferredIp(config);
  }

  /**
   * Used by driver-specific libraries to add to the user agent.
   *
   * @param artifactId the artifact ID.
   */
  public static void addArtifactId(String artifactId) {
    ConnectorRegistry.INSTANCE.addArtifactId(artifactId);
  }
}
