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

import static com.google.common.truth.Truth.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.junit.Test;

public class ConnectionConfigTest {

  @Test
  public void testConfigFromProps() {
    final String wantCsqlInstance = "proj:region:inst";
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String delegates = wantDelegates.stream().collect(Collectors.joining(","));
    final String iamAuthN = "true";
    final String wantUnixSocket = "/path/to/socket";
    final List<IpType> wantIpTypes =
        Arrays.asList(IpType.PSC, IpType.PRIVATE, IpType.PUBLIC); // PUBLIC is replaced with PRIMARY
    final String ipTypes = "psc,Private,PUBLIC";

    Properties props = new Properties();
    props.setProperty(ConnectionConfig.CLOUD_SQL_INSTANCE_PROPERTY, wantCsqlInstance);
    props.setProperty(ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY, wantTargetPrincipal);
    props.setProperty(ConnectionConfig.CLOUD_SQL_DELEGATES_PROPERTY, delegates);
    props.setProperty(ConnectionConfig.ENABLE_IAM_AUTH_PROPERTY, iamAuthN);
    props.setProperty(ConnectionConfig.UNIX_SOCKET_PROPERTY, wantUnixSocket);
    props.setProperty(ConnectionConfig.IP_TYPES_PROPERTY, ipTypes);

    ConnectionConfig c = ConnectionConfig.fromConnectionProperties(props);

    assertThat(c.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c.getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(c.getDelegates()).isEqualTo(wantDelegates);
    assertThat(c.getAuthType()).isEqualTo(AuthType.IAM);
    assertThat(c.getUnixSocketPath()).isEqualTo(wantUnixSocket);
    assertThat(c.getIpTypes()).isEqualTo(wantIpTypes);
  }

  @Test
  public void testConfigFromBuilder() {
    final String wantCsqlInstance = "proj:region:inst";
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String wantUnixSocket = "/path/to/socket";
    final List<IpType> wantIpTypes = Arrays.asList(IpType.PSC, IpType.PRIVATE, IpType.PUBLIC);
    final AuthType wantAuthType = AuthType.PASSWORD;

    ConnectionConfig c =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(wantCsqlInstance)
            .withTargetPrincipal(wantTargetPrincipal)
            .withDelegates(wantDelegates)
            .withIpTypes(wantIpTypes)
            .withUnixSocketPath(wantUnixSocket)
            .withAuthType(wantAuthType)
            .build();

    assertThat(c.getCloudSqlInstance()).isEqualTo(wantCsqlInstance);
    assertThat(c.getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(c.getDelegates()).isEqualTo(wantDelegates);
    assertThat(c.getAuthType()).isEqualTo(wantAuthType);
    assertThat(c.getUnixSocketPath()).isEqualTo(wantUnixSocket);
    assertThat(c.getIpTypes()).isEqualTo(wantIpTypes);
  }
}
