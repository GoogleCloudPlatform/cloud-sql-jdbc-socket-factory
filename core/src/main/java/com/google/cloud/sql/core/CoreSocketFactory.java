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

package com.google.cloud.sql.core;

import com.google.cloud.sql.ConnectionConfig;

/**
 * Implementation of informally used Java API to preserve compatibility with older code that uses
 * CoreSocketFactory.
 *
 * @deprecated Use the official java API instead.
 * @see com.google.cloud.sql.ConnectorRegistry
 */
@Deprecated
public final class CoreSocketFactory {

  /**
   * Connection property name.
   *
   * @deprecated Use the public API instead.
   * @see com.google.cloud.sql.ConnectionConfig#CLOUD_SQL_INSTANCE_PROPERTY
   */
  @Deprecated
  public static final String CLOUD_SQL_INSTANCE_PROPERTY =
      ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY;

  /**
   * Delegates property name.
   *
   * @deprecated Use the public API instead.
   * @see com.google.cloud.sql.ConnectionConfig#CLOUD_SQL_DELEGATES_PROPERTY
   */
  @Deprecated
  public static final String CLOUD_SQL_DELEGATES_PROPERTY =
      ConnectionConfig.CLOUD_SQL_DELEGATES_PROPERTY;

  /**
   * TargetPrincipal property name.
   *
   * @deprecated Use the public API instead.
   * @see com.google.cloud.sql.ConnectionConfig#CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY
   */
  @Deprecated
  public static final String CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY =
      ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY;

  /**
   * IpTypes default property value.
   *
   * @deprecated Use the public API instead.
   * @see com.google.cloud.sql.ConnectionConfig#DEFAULT_IP_TYPES
   */
  @Deprecated public static final String DEFAULT_IP_TYPES = ConnectionConfig.DEFAULT_IP_TYPES;

  /**
   * Property used to set the application name for the underlying SQLAdmin client.
   *
   * @deprecated Use {@link #setApplicationName(String)} to set the application name
   *     programmatically.
   */
  @Deprecated
  public static final String USER_TOKEN_PROPERTY_NAME =
      InternalConnectorRegistry.USER_TOKEN_PROPERTY_NAME;

  /**
   * Sets the application name for the user agent.
   *
   * @deprecated Use the official java API instead.
   * @see com.google.cloud.sql.ConnectorRegistry
   */
  static void setApplicationName(String artifactId) {
    InternalConnectorRegistry.setApplicationName(artifactId);
  }
}
