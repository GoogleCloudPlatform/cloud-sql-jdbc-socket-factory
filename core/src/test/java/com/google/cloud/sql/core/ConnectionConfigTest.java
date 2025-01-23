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

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.RefreshStrategy;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Test;

public class ConnectionConfigTest {

  @Test
  public void testConfigFromProps() {
    final String wantCsqlInstance = "proj:region:inst";
    final String wantNamedConnector = "my-connection";
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String delegates = wantDelegates.stream().collect(Collectors.joining(","));
    final String iamAuthN = "true";
    final String wantUnixSocket = "/path/to/socket";
    final List<IpType> wantIpTypes =
        Arrays.asList(IpType.PSC, IpType.PRIVATE, IpType.PUBLIC); // PUBLIC is replaced with PRIMARY
    final String ipTypes = "psc,Private,PUBLIC";
    final String wantAdminRootUrl = "https://googleapis.example.com/";
    final String wantAdminServicePath = "sqladmin/";
    final String wantUnixSuffix = ".psql.5432";
    final String wantPath = "my-path";
    final String wantAdminQuotaProject = "myNewProject";
    final String propRefreshStrategy = "Lazy";
    final RefreshStrategy wantRefreshStrategy = RefreshStrategy.LAZY;
    final String wantDomainName = "db.example.com";

    Properties props = new Properties();
    props.setProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY, wantCsqlInstance);
    props.setProperty(ConnectionConfig.CLOUD_SQL_NAMED_CONNECTOR_PROPERTY, wantNamedConnector);
    props.setProperty(ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY, wantTargetPrincipal);
    props.setProperty(ConnectionConfig.CLOUD_SQL_DELEGATES_PROPERTY, delegates);
    props.setProperty(ConnectionConfig.ENABLE_IAM_AUTH_PROPERTY, iamAuthN);
    props.setProperty(ConnectionConfig.UNIX_SOCKET_PROPERTY, wantUnixSocket);
    props.setProperty(ConnectionConfig.IP_TYPES_PROPERTY, ipTypes);
    props.setProperty(ConnectionConfig.CLOUD_SQL_ADMIN_ROOT_URL_PROPERTY, wantAdminRootUrl);
    props.setProperty(ConnectionConfig.CLOUD_SQL_ADMIN_SERVICE_PATH_PROPERTY, wantAdminServicePath);
    props.setProperty(ConnectionConfig.UNIX_SOCKET_PATH_SUFFIX_PROPERTY, wantUnixSuffix);
    props.setProperty(ConnectionConfig.CLOUD_SQL_GOOGLE_CREDENTIALS_PATH, wantPath);
    props.setProperty(
        ConnectionConfig.CLOUD_SQL_ADMIN_QUOTA_PROJECT_PROPERTY, wantAdminQuotaProject);
    props.setProperty(ConnectionConfig.CLOUD_SQL_REFRESH_STRATEGY_PROPERTY, propRefreshStrategy);

    ConnectionConfig c = ConnectionConfig.fromConnectionProperties(props, wantDomainName);

    assertThat(c.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c.getNamedConnector()).isEqualTo(wantNamedConnector);
    assertThat(c.getConnectorConfig().getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(c.getConnectorConfig().getDelegates()).isEqualTo(wantDelegates);
    assertThat(c.getAuthType()).isEqualTo(AuthType.IAM);
    assertThat(c.getUnixSocketPath()).isEqualTo(wantUnixSocket);
    assertThat(c.getIpTypes()).isEqualTo(wantIpTypes);
    assertThat(c.getConnectorConfig().getAdminRootUrl()).isEqualTo(wantAdminRootUrl);
    assertThat(c.getConnectorConfig().getAdminServicePath()).isEqualTo(wantAdminServicePath);
    assertThat(c.getConnectorConfig().getGoogleCredentialsPath()).isEqualTo(wantPath);
    assertThat(c.getConnectorConfig().getAdminQuotaProject()).isEqualTo(wantAdminQuotaProject);
    assertThat(c.getUnixSocketPathSuffix()).isEqualTo(wantUnixSuffix);
    assertThat(c.getConnectorConfig().getRefreshStrategy()).isEqualTo(wantRefreshStrategy);
    assertThat(c.getDomainName()).isEqualTo(wantDomainName);
  }

  @Test
  public void testConfigFromBuilder() {
    final String wantCsqlInstance = "proj:region:inst";
    final String wantNamedConnector = "my-connection";
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String wantUnixSocket = "/path/to/socket";
    final List<IpType> wantIpTypes = Arrays.asList(IpType.PSC, IpType.PRIVATE, IpType.PUBLIC);
    final AuthType wantAuthType = AuthType.PASSWORD;
    final String wantAdminRootUrl = "https://googleapis.example.com/";
    final String wantAdminServicePath = "sqladmin/";
    final String wantUnixSuffix = ".psql.5432";
    final String wantAdminQuotaProject = "myNewProject";
    final String wantDomainName = "db.example.com";

    ConnectorConfig cc =
        new ConnectorConfig.Builder()
            .withTargetPrincipal(wantTargetPrincipal)
            .withDelegates(wantDelegates)
            .withAdminRootUrl(wantAdminRootUrl)
            .withAdminServicePath(wantAdminServicePath)
            .withAdminQuotaProject(wantAdminQuotaProject)
            .build();

    ConnectionConfig c =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(wantCsqlInstance)
            .withNamedConnector(wantNamedConnector)
            .withIpTypes(wantIpTypes)
            .withAuthType(wantAuthType)
            .withUnixSocketPath(wantUnixSocket)
            .withUnixSocketPathSuffix(wantUnixSuffix)
            .withConnectorConfig(cc)
            .withDomainName(wantDomainName)
            .build();

    assertThat(c.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c.getNamedConnector()).isEqualTo(wantNamedConnector);
    assertThat(c.getConnectorConfig().getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(c.getConnectorConfig().getDelegates()).isEqualTo(wantDelegates);
    assertThat(c.getAuthType()).isEqualTo(wantAuthType);
    assertThat(c.getUnixSocketPath()).isEqualTo(wantUnixSocket);
    assertThat(c.getIpTypes()).isEqualTo(wantIpTypes);
    assertThat(c.getConnectorConfig().getAdminRootUrl()).isEqualTo(wantAdminRootUrl);
    assertThat(c.getConnectorConfig().getAdminServicePath()).isEqualTo(wantAdminServicePath);
    assertThat(c.getConnectorConfig().getAdminQuotaProject()).isEqualTo(wantAdminQuotaProject);
    assertThat(c.getUnixSocketPathSuffix()).isEqualTo(wantUnixSuffix);
    assertThat(c.getDomainName()).isEqualTo(wantDomainName);
  }

  @Test
  public void testWithConnectorConfig() {
    final String wantCsqlInstance = "proj:region:inst";
    final String wantNamedConnector = "my-connection";

    ConnectorConfig cc = new ConnectorConfig.Builder().build();

    ConnectionConfig c =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(wantCsqlInstance)
            .withNamedConnector(wantNamedConnector)
            .build();

    assertThat(c.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c.getNamedConnector()).isEqualTo(wantNamedConnector);
    assertThat(c.getConnectorConfig()).isNotSameInstanceAs(cc);

    ConnectionConfig c1 = c.withConnectorConfig(cc);

    assertThat(c1).isNotSameInstanceAs(c);
    assertThat(c1.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c1.getNamedConnector()).isEqualTo(wantNamedConnector);
    assertThat(c1.getConnectorConfig()).isSameInstanceAs(cc);
  }
}
