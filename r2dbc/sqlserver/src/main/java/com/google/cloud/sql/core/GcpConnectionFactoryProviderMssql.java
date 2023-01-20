/*
 * Copyright 2020 Google Inc. All Rights Reserved.
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

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.mssql.MssqlConnectionFactoryProvider;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.function.Function;

/**
 * {@link ConnectionFactoryProvider} for proxied access to GCP MsSQL instances.
 */
public class GcpConnectionFactoryProviderMssql extends GcpConnectionFactoryProvider {

  static {
    CoreSocketFactory.addArtifactId("cloud-sql-connector-r2dbc-mssql");
  }

  /**
   * MsSQL driver option value.
   */
  private static final String MSSQL_DRIVER = "mssql";

  @Override
  boolean supportedProtocol(String protocol) {
    return protocol.equals(MSSQL_DRIVER);
  }

  @Override
  ConnectionFactory tcpConnectionFactory(
      Builder optionBuilder,
      boolean enableIamAuth,
      String ipTypes,
      Function<SslContextBuilder, SslContextBuilder> customizer,
      String csqlHostName) {
    optionBuilder
        .option(MssqlConnectionFactoryProvider.SSL_TUNNEL, customizer)
        .option(MssqlConnectionFactoryProvider.TCP_NODELAY, true)
        .option(MssqlConnectionFactoryProvider.TCP_KEEPALIVE, true);

    return new CloudSqlConnectionFactory(
        (ConnectionFactoryOptions options) -> new MssqlConnectionFactoryProvider().create(options),
        ipTypes,
        optionBuilder,
        csqlHostName);
  }

  @Override
  ConnectionFactory socketConnectionFactory(Builder optionBuilder, String socket) {
    throw new RuntimeException("UNIX socket connections are not supported");
  }

  @Override
  Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions) {
    return connectionFactoryOptions.mutate().option(DRIVER, MSSQL_DRIVER);
  }
}
