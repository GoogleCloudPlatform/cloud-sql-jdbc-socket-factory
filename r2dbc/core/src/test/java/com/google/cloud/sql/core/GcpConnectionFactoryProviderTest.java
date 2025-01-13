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
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.RefreshStrategy;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.util.Collections;
import java.util.function.Function;
import org.junit.Test;
import org.reactivestreams.Publisher;

public class GcpConnectionFactoryProviderTest {
  private final ConnectionFactory unixFactory = new StubConnectionFactory(null);

  @Test
  public void testCreateWithInstanceName() {

    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");

    StubConnectionFactory factory = configureConnection(options.build());
    ConnectionConfig config = factory.config;
    assertThat(config.getCloudSqlInstance()).isEqualTo("project:region:instance");
  }

  @Test
  public void testCreateWithUnixSocket() {

    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");
    options.option(GcpConnectionFactoryProvider.UNIX_SOCKET, "/socket/path");

    StubConnectionFactory factory = configureConnection(options.build());
    assertThat(factory).isSameInstanceAs(unixFactory);
  }

  @Test
  public void testCreateWithAllOptions() {

    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");
    options.option(GcpConnectionFactoryProvider.NAMED_CONNECTOR, "resolver-test");
    options.option(GcpConnectionFactoryProvider.IP_TYPES, "private");
    options.option(GcpConnectionFactoryProvider.ENABLE_IAM_AUTH, true);
    options.option(GcpConnectionFactoryProvider.DELEGATES, "delegate@example.com");
    options.option(GcpConnectionFactoryProvider.TARGET_PRINCIPAL, "target@example.com");
    options.option(GcpConnectionFactoryProvider.ADMIN_QUOTA_PROJECT, "quota-project");
    options.option(GcpConnectionFactoryProvider.GOOGLE_CREDENTIALS_PATH, "/credentials/path");
    options.option(GcpConnectionFactoryProvider.UNIVERSE_DOMAIN, "domain.google.com");
    options.option(GcpConnectionFactoryProvider.REFRESH_STRATEGY, "lazy");

    StubConnectionFactory factory = configureConnection(options.build());
    ConnectionConfig config = factory.config;

    assertThat(config.getCloudSqlInstance()).isEqualTo("project:region:instance");
    assertThat(config.getNamedConnector()).isEqualTo("resolver-test");
    assertThat(config.getIpTypes()).isEqualTo(Collections.singletonList(IpType.PRIVATE));
    assertThat(config.getAuthType()).isEqualTo(AuthType.IAM);
    assertThat(config.getConnectorConfig().getDelegates())
        .isEqualTo(Collections.singletonList("delegate@example.com"));
    assertThat(config.getConnectorConfig().getTargetPrincipal()).isEqualTo("target@example.com");
    assertThat(config.getConnectorConfig().getAdminQuotaProject()).isEqualTo("quota-project");
    assertThat(config.getConnectorConfig().getGoogleCredentialsPath())
        .isEqualTo("/credentials/path");
    assertThat(config.getConnectorConfig().getUniverseDomain()).isEqualTo("domain.google.com");
    assertThat(config.getConnectorConfig().getRefreshStrategy()).isEqualTo(RefreshStrategy.LAZY);
  }

  @Test
  public void testCreateWithAdminApiOptions() {

    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");
    options.option(GcpConnectionFactoryProvider.ADMIN_ROOT_URL, "http://example.com/root");
    options.option(GcpConnectionFactoryProvider.ADMIN_SERVICE_PATH, "/service");

    StubConnectionFactory factory = configureConnection(options.build());
    ConnectionConfig config = factory.config;

    assertThat(config.getCloudSqlInstance()).isEqualTo("project:region:instance");
    assertThat(config.getConnectorConfig().getAdminRootUrl()).isEqualTo("http://example.com/root");
    assertThat(config.getConnectorConfig().getAdminServicePath()).isEqualTo("/service");
  }

  @Test
  public void testCreateWithDomainName() {

    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "db.example.com");

    StubConnectionFactory factory = configureConnection(options.build());
    ConnectionConfig config = factory.config;

    assertThat(config.getDomainName()).isEqualTo("db.example.com");
    assertThat(config.getCloudSqlInstance()).isNull();
  }

  private static class StubConnectionFactory implements ConnectionFactory {

    final ConnectionConfig config;

    private StubConnectionFactory(ConnectionConfig config) {
      this.config = config;
    }

    @Override
    public Publisher<? extends Connection> create() {
      return null;
    }

    @Override
    public ConnectionFactoryMetadata getMetadata() {
      return null;
    }
  }

  private StubConnectionFactory configureConnection(ConnectionFactoryOptions options) {
    GcpConnectionFactoryProvider p =
        new GcpConnectionFactoryProvider() {
          @Override
          ConnectionFactory tcpSocketConnectionFactory(
              ConnectionConfig config,
              ConnectionFactoryOptions.Builder optionBuilder,
              Function<SslContextBuilder, SslContextBuilder> customizer) {
            return new StubConnectionFactory(config);
          }

          @Override
          ConnectionFactory unixSocketConnectionFactory(
              ConnectionFactoryOptions.Builder optionBuilder, String socket) {
            return unixFactory;
          }

          @Override
          ConnectionFactoryOptions.Builder createBuilder(
              ConnectionFactoryOptions connectionFactoryOptions) {
            return ConnectionFactoryOptions.builder();
          }

          @Override
          boolean supportedProtocol(String protocol) {
            return "cloudsql".equals(protocol);
          }
        };

    return (StubConnectionFactory) p.create(options);
  }
}
