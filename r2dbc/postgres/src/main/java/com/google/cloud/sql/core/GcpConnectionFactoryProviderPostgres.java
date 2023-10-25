/*
 * Copyright 2020 Google Inc.
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

import static io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;

import com.google.cloud.sql.ConnectionConfig;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.function.Function;

/** {@link ConnectionFactoryProvider} for proxied access to GCP Postgres instances. */
public class GcpConnectionFactoryProviderPostgres extends GcpConnectionFactoryProvider {

  /** Postgres driver option value. */
  private static final String POSTGRESQL_DRIVER = "postgresql";
  /** Legacy postgres driver option value. */
  private static final String LEGACY_POSTGRESQL_DRIVER = "postgres";

  static {
    InternalConnectorRegistry.addArtifactId("cloud-sql-connector-r2dbc-postgres");
  }

  @Override
  boolean supportedProtocol(String protocol) {
    return protocol.equals(POSTGRESQL_DRIVER) || protocol.equals(LEGACY_POSTGRESQL_DRIVER);
  }

  @Override
  ConnectionFactory tcpSocketConnectionFactory(
      ConnectionConfig config,
      Builder builder,
      Function<SslContextBuilder, SslContextBuilder> customizer) {
    builder
        .option(PostgresqlConnectionFactoryProvider.SSL_CONTEXT_BUILDER_CUSTOMIZER, customizer)
        .option(PostgresqlConnectionFactoryProvider.SSL_MODE, SSLMode.TUNNEL)
        .option(PostgresqlConnectionFactoryProvider.TCP_NODELAY, true)
        .option(PostgresqlConnectionFactoryProvider.TCP_KEEPALIVE, true);

    return new CloudSqlConnectionFactory(config, PostgresqlConnectionFactoryProvider::new, builder);
  }

  @Override
  ConnectionFactory unixSocketConnectionFactory(Builder optionBuilder, String socket) {
    optionBuilder.option(PostgresqlConnectionFactoryProvider.SOCKET, socket);
    return new PostgresqlConnectionFactoryProvider().create(optionBuilder.build());
  }

  @Override
  Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions) {
    return connectionFactoryOptions.mutate().option(DRIVER, POSTGRESQL_DRIVER);
  }
}
