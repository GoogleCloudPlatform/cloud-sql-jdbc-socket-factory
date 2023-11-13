/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql;

import com.google.cloud.sql.core.InternalConnectorRegistry;

/** Configure the CloudSQL JDBC Connector. */
public final class ConnectorRegistry {

  /**
   * Register a named connection so that it can later be referenced by name in a JDBC or R2DBC URL.
   *
   * @param name the named connection name.
   * @param config the full configuration of the connection.
   */
  public static void register(String name, ConnectorConfig config) {
    InternalConnectorRegistry.getInstance().register(name, config);
  }

  /**
   * Close a named connector. This will stop all background credential refresh processes. All future
   * attempts to connect via this named connection will fail.
   *
   * @param name the name of the connector to close.
   */
  public static void close(String name) {
    InternalConnectorRegistry.getInstance().close(name);
  }

  /**
   * Shutdown the entire CloudSQL JDBC Connector. This will stop all background threads. All future
   * attempts to connect to a CloudSQL database will fail.
   */
  public static void shutdown() {
    InternalConnectorRegistry.shutdownInstance();
  }

  /**
   * Resets the entire CloudSQL JDBC Connector. This will stop all background threads. The next
   * attempt to open a connection or register a configuration will start a new ConnectorRegistry.
   */
  public static void reset() {
    InternalConnectorRegistry.resetInstance();
  }

  /**
   * Adds an external application name to the user agent string for tracking. This is known to be
   * used by the spring-cloud-gcp project.
   *
   * @throws IllegalStateException if the SQLAdmin client has already been initialized
   */
  public static void addArtifactId(String artifactId) {
    InternalConnectorRegistry.getInstance().addArtifactId(artifactId);
  }
}
