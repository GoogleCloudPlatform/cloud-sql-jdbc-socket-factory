package com.google.cloud.sql.core;

import com.google.cloud.sql.AuthType;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/**
 * A value object containing all configuration values needed to set up a connection to a Cloud SQL
 * Instance.
 */
public class ConnectionConfig {
  public static final String CLOUD_SQL_INSTANCE_PROPERTY = "cloudSqlInstance";

  public static final String DEFAULT_IP_TYPES = "PUBLIC,PRIVATE";
  public static final int DEFAULT_SERVER_PROXY_PORT = 3307;

  public static final String UNIX_SOCKET_PROPERTY = "unixSocketPath";
  public static final String ENABLE_IAM_AUTHN_PROPERTY = "enableIamAuthn";
  public static final String TARGET_PRINCIPAL_PROPERTY = "targetPrincipal";
  public static final String IP_TYPES_PROPERTY = "ipTypes";
  public static final String DELEGATES_PROPERTY = "delegates";
  public static final String UNIX_SOCKET_PATH_SUFFIX = "_unixSocketPathSuffix";
  public static final String USER_TOKEN_PROPERTY_NAME = "_CLOUD_SQL_USER_TOKEN";
  private final AuthType authType;

  private final String unixSocketPath;
  private final String instanceName;
  private final String targetPrincipal;
  private final String unixSocketPathSuffix;

  private final List<String> delegates;
  private final List<String> ipTypes;

  private int serverPort = ConnectionConfig.DEFAULT_SERVER_PROXY_PORT;

  /**
   * Converts the string property of IP types to a list by splitting by commas, and upper-casing.
   */
  private static List<String> listIpTypes(String cloudSqlIpTypes) {
    String[] rawTypes = cloudSqlIpTypes.split(",");
    ArrayList<String> result = new ArrayList<>(rawTypes.length);
    for (int i = 0; i < rawTypes.length; i++) {
      if (rawTypes[i].trim().equalsIgnoreCase("PUBLIC")) {
        result.add(i, "PRIMARY");
      } else {
        result.add(i, rawTypes[i].trim().toUpperCase());
      }
    }
    return result;
  }

  /**
   * Creates a new ConnectionConfig from the JDBC connection properties. Using the property names
   * listed above:
   *
   * <p>enableIamAuthn - whether to use IAM Authentication for the database connection. "true" or
   * "false", default "false" unixSocketPath - The path to the unix socket to use when connecting to
   * the databse. cloudSqlInstance - The name of the cloud sql instance in the format
   * project:region:instanceName ipTypes - A comma-separated list of of IP types. Valid values:
   * "PUBLIC" "PRIVATE" targetPrincipal - The target principal to use for service account
   * impersonation delegates - A comma-separated list of principals to use to generate a token for
   * the target principal
   *
   * @param props the JDBC connection properties
   */
  public ConnectionConfig(Properties props) {
    authType =
        Boolean.parseBoolean(props.getProperty(ENABLE_IAM_AUTHN_PROPERTY, "false"))
            ? AuthType.IAM
            : AuthType.PASSWORD;
    unixSocketPath = props.getProperty(UNIX_SOCKET_PROPERTY);
    instanceName = props.getProperty(CLOUD_SQL_INSTANCE_PROPERTY);
    targetPrincipal = props.getProperty(TARGET_PRINCIPAL_PROPERTY);
    delegates = Arrays.asList(props.getProperty(DELEGATES_PROPERTY, "").split(","));
    ipTypes = listIpTypes(props.getProperty(IP_TYPES_PROPERTY, DEFAULT_IP_TYPES));
    unixSocketPathSuffix = props.getProperty(UNIX_SOCKET_PATH_SUFFIX);
    validatePreconditions();
  }

  /**
   * Creates a new ConnectionConfig.
   *
   * @param instanceName the name of the database instance in the format project:region:instanceName
   * @param authType The type of authentication
   * @param unixSocketPath The path to the unix socket for this database
   * @param targetPrincipal The target principal to use for service account impersonation
   * @param delegates A comma-separated list of principals to use to generate a token for the *
   *     target principal
   * @param ipTypes A comma-separated list of of IP types. Valid values: "PUBLIC" "PRIVATE"
   * @param unixSocketPathSuffix a suffix to add to the database unix socket path if it is not
   *     already included, used for Postgres.
   */
  public ConnectionConfig(
      String instanceName,
      AuthType authType,
      String unixSocketPath,
      String targetPrincipal,
      List<String> delegates,
      String ipTypes,
      String unixSocketPathSuffix) {
    this.authType = authType;
    this.unixSocketPath = unixSocketPath;
    this.instanceName = instanceName;
    this.targetPrincipal = targetPrincipal;
    this.delegates = delegates;
    this.ipTypes = listIpTypes(ipTypes);
    this.unixSocketPathSuffix = unixSocketPathSuffix;
    validatePreconditions();
  }

  @VisibleForTesting
  ConnectionConfig(
      String instanceName,
      AuthType authType,
      String unixSocketPath,
      String targetPrincipal,
      List<String> delegates,
      String ipTypes,
      String unixSocketPathSuffix,
      int serverPort) {
    this.authType = authType;
    this.unixSocketPath = unixSocketPath;
    this.instanceName = instanceName;
    this.targetPrincipal = targetPrincipal;
    this.delegates = delegates;
    this.ipTypes = listIpTypes(ipTypes);
    this.unixSocketPathSuffix = unixSocketPathSuffix;
    this.serverPort = serverPort;
    validatePreconditions();
  }

  private void validatePreconditions() {
    Preconditions.checkArgument(
        this.instanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or the "
            + "connection Properties with value in form \"project:region:instance\"");
  }

  public int getServerPort() {
    return serverPort;
  }

  public List<String> getIpTypes() {
    return ipTypes;
  }

  String getUnixSocketPath() {
    return unixSocketPath;
  }

  public String getUnixSocketPathSuffix() {
    return unixSocketPathSuffix;
  }

  String getInstanceName() {
    return instanceName;
  }

  AuthType getAuthType() {
    return authType;
  }

  String getTargetPrincipal() {
    return targetPrincipal;
  }

  List<String> getDelegates() {
    return delegates;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ConnectionConfig)) {
      return false;
    }
    ConnectionConfig that = (ConnectionConfig) o;
    return serverPort == that.serverPort
        && authType == that.authType
        && Objects.equals(unixSocketPath, that.unixSocketPath)
        && Objects.equals(instanceName, that.instanceName)
        && Objects.equals(targetPrincipal, that.targetPrincipal)
        && Objects.equals(unixSocketPathSuffix, that.unixSocketPathSuffix)
        && Objects.equals(delegates, that.delegates)
        && Objects.equals(ipTypes, that.ipTypes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        authType,
        unixSocketPath,
        instanceName,
        targetPrincipal,
        unixSocketPathSuffix,
        delegates,
        ipTypes,
        serverPort);
  }
}
