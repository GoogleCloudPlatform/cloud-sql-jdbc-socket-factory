/*
 * Copyright 2022 Google Inc. All Rights Reserved.
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

import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static org.mariadb.r2dbc.MariadbConnectionFactoryProvider.MARIADB_DRIVER;

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.function.Function;
import org.mariadb.r2dbc.MariadbConnectionFactoryProvider;
import org.mariadb.r2dbc.SslMode;

/** {@link ConnectionFactoryProvider} for proxied access to GCP MySQL instances. */
public class GcpConnectionFactoryProviderMariadb extends GcpConnectionFactoryProvider {
  static {
    CoreSocketFactory.addArtifactId("cloud-sql-connector-r2dbc-mariadb");
  }

  @Override
  boolean supportedProtocol(String protocol) {
    return protocol.equals(MARIADB_DRIVER);
  }

  @Override
  ConnectionFactory tcpConnectonFactory(
      ConnectionFactoryOptions.Builder optionBuilder,
      Function<SslContextBuilder, SslContextBuilder> customizer,
      String csqlHostName) {
    return new CloudSqlConnectionFactory(
        (ConnectionFactoryOptions options) ->
            new MariadbConnectionFactoryProvider().create(options),
        optionBuilder
            .option(MariadbConnectionFactoryProvider.TCP_KEEP_ALIVE, true),
        csqlHostName);
  }

  @Override
  ConnectionFactory socketConnectionFactory(
      ConnectionFactoryOptions.Builder optionBuilder, String socket) {
    return new MariadbConnectionFactoryProvider()
        .create(optionBuilder.option(MariadbConnectionFactoryProvider.SOCKET, socket).build());
  }

  @Override
  ConnectionFactoryOptions.Builder createBuilder(
      ConnectionFactoryOptions connectionFactoryOptions) {
    return connectionFactoryOptions.mutate().option(DRIVER, MARIADB_DRIVER);
  }
}
