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

package com.google.cloud.sql;

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * ConnectionConfig is an immutable configuration value object that holds the entire configuration
 * of a CloudSqlInstance connection.
 */
public class ConnectionConfig {

  public static final String CLOUD_SQL_INSTANCE_PROPERTY = "cloudSqlInstance";
  public static final String CLOUD_SQL_DELEGATES_PROPERTY = "cloudSqlDelegates";
  public static final String CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY = "cloudSqlTargetPrincipal";
  public static final String CLOUD_SQL_ADMIN_ROOT_URL_PROPERTY = "cloudSqlAdminRootUrl";
  public static final String CLOUD_SQL_ADMIN_SERVICE_PATH_PROPERTY = "cloudSqlAdminServicePath";
  public static final String UNIX_SOCKET_PROPERTY = "unixSocketPath";
  public static final String ENABLE_IAM_AUTH_PROPERTY = "enableIamAuth";
  public static final String IP_TYPES_PROPERTY = "ipTypes";
  public static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";
  public static final List<IpType> DEFAULT_IP_TYPE_LIST =
      Arrays.asList(IpType.PUBLIC, IpType.PRIVATE);
  public static final AuthType DEFAULT_AUTH_TYPE = AuthType.PASSWORD;
  private final String cloudSqlInstance;
  private final String targetPrincipal;
  private final List<String> delegates;
  private final String unixSocketPath;
  private final AuthType authType;
  private final List<IpType> ipTypes;
  private final String adminRootUrl;
  private final String adminServicePath;

  /** Create a new ConnectionConfig from the well known JDBC Connection properties. */
  public static ConnectionConfig fromConnectionProperties(Properties props) {
    final String csqlInstanceName = props.getProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY);
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

    return new ConnectionConfig(
        csqlInstanceName,
        targetPrincipal,
        delegates,
        unixSocketPath,
        authType,
        ipTypes,
        adminRootUrl,
        adminServicePath);
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

  private ConnectionConfig(
      String cloudSqlInstance,
      String targetPrincipal,
      List<String> delegates,
      String unixSocketPath,
      AuthType authType,
      List<IpType> ipTypes,
      String adminRootUrl,
      String adminServicePath) {
    this.cloudSqlInstance = cloudSqlInstance;
    this.targetPrincipal = targetPrincipal;
    this.delegates = delegates;
    this.unixSocketPath = unixSocketPath;
    this.authType = authType;
    this.ipTypes = ipTypes;
    this.adminRootUrl = adminRootUrl;
    this.adminServicePath = adminServicePath;
  }

  public String getCloudSqlInstance() {
    return cloudSqlInstance;
  }

  public String getTargetPrincipal() {
    return targetPrincipal;
  }

  public String getUnixSocketPath() {
    return unixSocketPath;
  }

  public AuthType getAuthType() {
    return authType;
  }

  public List<String> getDelegates() {
    return delegates;
  }

  public List<IpType> getIpTypes() {
    return ipTypes;
  }

  public String getAdminRootUrl() {
    return adminRootUrl;
  }

  public String getlAdminServicePath() {
    return adminServicePath;
  }

  /** The builder for the ConnectionConfig. */
  public static class Builder {

    private String cloudSqlInstance;
    private String targetPrincipal;
    private List<String> delegates;
    private String unixSocketPath;
    private AuthType authType = DEFAULT_AUTH_TYPE;
    private List<IpType> ipTypes = DEFAULT_IP_TYPE_LIST;
    private String adminRootUrl;
    private String adminServicePath;

    public Builder withCloudSqlInstance(String cloudSqlInstance) {
      this.cloudSqlInstance = cloudSqlInstance;
      return this;
    }

    public Builder withTargetPrincipal(String targetPrincipal) {
      this.targetPrincipal = targetPrincipal;
      return this;
    }

    public Builder withDelegates(List<String> delegates) {
      this.delegates = delegates;
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

    /** Use ipTypes as a comma-delimited string. */
    public Builder withIpTypes(String ipTypes) {
      this.ipTypes = listIpTypes(ipTypes);
      return this;
    }

    /** Use ipTypes as a comma-delimited string. */
    public Builder withIpTypes(List<IpType> ipTypes) {
      this.ipTypes = ipTypes;
      return this;
    }

    public Builder withAdminRootUrl(String adminRootUrl) {
      this.adminRootUrl = adminRootUrl;
      return this;
    }

    public Builder withAdminServicePath(String adminServicePath) {
      this.adminServicePath = adminServicePath;
      return this;
    }

    public ConnectionConfig build() {
      return new ConnectionConfig(
          cloudSqlInstance,
          targetPrincipal,
          delegates,
          unixSocketPath,
          authType,
          ipTypes,
          adminRootUrl,
          adminServicePath);
    }
  }
}
