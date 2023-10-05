/*
 * Copyright 2023 Google LLC
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

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.IpType;
import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * ConnectionConfig is an immutable configuration value object that holds the entire configuration
 * of a CloudSqlInstance connection.
 *
 * <p>WARNING: This is an internal class. The API is subject to change without notice.
 */
public class ConnectionConfig {

  public static final String CLOUD_SQL_INSTANCE_PROPERTY = "cloudSqlInstance";
  public static final String CLOUD_SQL_NAMED_CONNECTOR_PROPERTY = "cloudSqlNamedConnector";
  public static final String CLOUD_SQL_DELEGATES_PROPERTY = "cloudSqlDelegates";
  public static final String CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY = "cloudSqlTargetPrincipal";
  public static final String CLOUD_SQL_ADMIN_ROOT_URL_PROPERTY = "cloudSqlAdminRootUrl";
  public static final String CLOUD_SQL_ADMIN_SERVICE_PATH_PROPERTY = "cloudSqlAdminServicePath";
  public static final String UNIX_SOCKET_PROPERTY = "unixSocketPath";
  public static final String UNIX_SOCKET_PATH_SUFFIX_PROPERTY = "cloudSqlUnixSocketPathSuffix";
  public static final String ENABLE_IAM_AUTH_PROPERTY = "enableIamAuth";
  public static final String IP_TYPES_PROPERTY = "ipTypes";
  public static final AuthType DEFAULT_AUTH_TYPE = AuthType.PASSWORD;
  public static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";
  public static final List<IpType> DEFAULT_IP_TYPE_LIST =
      Arrays.asList(IpType.PUBLIC, IpType.PRIVATE);

  private final ConnectorConfig connectorConfig;
  private final String cloudSqlInstance;
  private final String namedConnector;
  private final String unixSocketPath;
  private final List<IpType> ipTypes;

  private final AuthType authType;
  private final String unixSocketPathSuffix;

  /** Create a new ConnectionConfig from the well known JDBC Connection properties. */
  public static ConnectionConfig fromConnectionProperties(Properties props) {
    final String csqlInstanceName = props.getProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY);
    final String namedConnection =
        props.getProperty(ConnectionConfig.CLOUD_SQL_NAMED_CONNECTOR_PROPERTY);

    final String unixSocketPath = props.getProperty(ConnectionConfig.UNIX_SOCKET_PROPERTY);
    final AuthType authType =
        Boolean.parseBoolean(props.getProperty(ConnectionConfig.ENABLE_IAM_AUTH_PROPERTY))
            ? AuthType.IAM
            : AuthType.PASSWORD;
    final String targetPrincipal =
        props.getProperty(ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY);
    final String delegatesStr = props.getProperty(ConnectionConfig.CLOUD_SQL_DELEGATES_PROPERTY);
    final List<String> delegates;
    if (delegatesStr != null && !delegatesStr.isEmpty()) {
      delegates = Arrays.asList(delegatesStr.split(","));
    } else {
      delegates = Collections.emptyList();
    }
    final List<IpType> ipTypes =
        listIpTypes(
            props.getProperty(
                ConnectionConfig.IP_TYPES_PROPERTY, ConnectionConfig.DEFAULT_IP_TYPES));
    final String adminRootUrl =
        props.getProperty(ConnectionConfig.CLOUD_SQL_ADMIN_ROOT_URL_PROPERTY);
    final String adminServicePath =
        props.getProperty(ConnectionConfig.CLOUD_SQL_ADMIN_SERVICE_PATH_PROPERTY);
    final String unixSocketPathSuffix =
        props.getProperty(ConnectionConfig.UNIX_SOCKET_PATH_SUFFIX_PROPERTY);
    return new ConnectionConfig(
        csqlInstanceName,
        namedConnection,
        unixSocketPath,
        ipTypes,
        authType,
        unixSocketPathSuffix,
        new ConnectorConfig.Builder()
            .withTargetPrincipal(targetPrincipal)
            .withDelegates(delegates)
            .withAdminRootUrl(adminRootUrl)
            .withAdminServicePath(adminServicePath)
            .build());
  }

  /**
   * Converts the string property of IP types to a list by splitting by commas, and upper-casing.
   */
  private static List<IpType> listIpTypes(String cloudSqlIpTypes) {
    List<String> rawTypes = Splitter.on(',').splitToList(cloudSqlIpTypes);
    ArrayList<IpType> result = new ArrayList<>(rawTypes.size());
    for (String type : rawTypes) {
      if (type.trim().equalsIgnoreCase("PUBLIC")) {
        result.add(IpType.PUBLIC);
      } else if (type.trim().equalsIgnoreCase("PRIMARY")) {
        result.add(IpType.PUBLIC);
      } else if (type.trim().equalsIgnoreCase("PRIVATE")) {
        result.add(IpType.PRIVATE);
      } else if (type.trim().equalsIgnoreCase("PSC")) {
        result.add(IpType.PSC);
      } else {
        throw new IllegalArgumentException(
            "Unsupported IP type: " + type + " found in ipTypes parameter");
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConnectionConfig)) {
      return false;
    }
    ConnectionConfig config = (ConnectionConfig) o;
    return Objects.equals(cloudSqlInstance, config.cloudSqlInstance)
        && Objects.equals(namedConnector, config.namedConnector)
        && Objects.equals(unixSocketPath, config.unixSocketPath)
        && Objects.equals(ipTypes, config.ipTypes)
        && Objects.equals(authType, config.authType)
        && Objects.equals(connectorConfig, config.connectorConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        cloudSqlInstance, namedConnector, unixSocketPath, ipTypes, authType, connectorConfig);
  }

  private ConnectionConfig(
      String cloudSqlInstance,
      String namedConnector,
      String unixSocketPath,
      List<IpType> ipTypes,
      AuthType authType,
      String unixSocketPathSuffix,
      ConnectorConfig connectorConfig) {
    this.cloudSqlInstance = cloudSqlInstance;
    this.namedConnector = namedConnector;
    this.unixSocketPath = unixSocketPath;
    this.ipTypes = ipTypes;
    this.unixSocketPathSuffix = unixSocketPathSuffix;
    this.connectorConfig = connectorConfig;
    this.authType = authType;
  }

  public String getNamedConnector() {
    return namedConnector;
  }

  public String getCloudSqlInstance() {
    return cloudSqlInstance;
  }

  public String getUnixSocketPath() {
    return unixSocketPath;
  }

  public List<IpType> getIpTypes() {
    return ipTypes;
  }

  public String getUnixSocketPathSuffix() {
    return unixSocketPathSuffix;
  }

  public ConnectorConfig getConnectorConfig() {
    return connectorConfig;
  }

  public AuthType getAuthType() {
    return authType;
  }

  /** The builder for the ConnectionConfig. */
  public static class Builder {

    private String cloudSqlInstance;
    private String namedConnector;
    private String unixSocketPath;
    private List<IpType> ipTypes = DEFAULT_IP_TYPE_LIST;
    private String unixSocketPathSuffix;
    private ConnectorConfig connectorConfig = new ConnectorConfig.Builder().build();
    private AuthType authType = DEFAULT_AUTH_TYPE;

    public Builder withCloudSqlInstance(String cloudSqlInstance) {
      this.cloudSqlInstance = cloudSqlInstance;
      return this;
    }

    public Builder withNamedConnector(String namedConnector) {
      this.namedConnector = namedConnector;
      return this;
    }

    public Builder withConnectorConfig(ConnectorConfig connectorConfig) {
      this.connectorConfig = connectorConfig;
      return this;
    }

    public Builder withUnixSocketPath(String unixSocketPath) {
      this.unixSocketPath = unixSocketPath;
      return this;
    }

    public Builder withAuthType(AuthType authType) {
      this.authType = authType;
      return this;
    }

    /** Set ipTypes with a comma-delimited string. */
    public Builder withIpTypes(String ipTypes) {
      this.ipTypes = listIpTypes(ipTypes);
      return this;
    }

    /** Set ipTypes as a list of IpType. */
    public Builder withIpTypes(List<IpType> ipTypes) {
      this.ipTypes = ipTypes;
      return this;
    }

    public Builder withUnixSocketPathSuffix(String unixSocketPathSuffix) {
      this.unixSocketPathSuffix = unixSocketPathSuffix;
      return this;
    }

    /** Builds a new instance of {@code ConnectionConfig}. */
    public ConnectionConfig build() {
      return new ConnectionConfig(
          cloudSqlInstance,
          namedConnector,
          unixSocketPath,
          ipTypes,
          authType,
          unixSocketPathSuffix,
          connectorConfig);
    }
  }
}
