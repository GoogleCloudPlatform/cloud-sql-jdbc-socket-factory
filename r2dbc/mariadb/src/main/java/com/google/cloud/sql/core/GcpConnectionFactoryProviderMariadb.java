/*
 * Copyright 2023 Google LLC. All Rights Reserved.
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
import static org.mariadb.r2dbc.MariadbConnectionFactoryProvider.MARIADB_DRIVER;

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.mariadb.r2dbc.MariadbConnectionFactoryProvider;

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
  ConnectionFactory tcpSocketConnectionFactory(
      ConnectionFactoryOptions.Builder optionBuilder,
      String ipTypes,
      Function<SslContextBuilder, SslContextBuilder> customizer,
      String csqlHostName) {

    // The MariaDB driver accepts the UnaryOperator interface so we need to adapt the customizer
    // function passed in
    UnaryOperator<SslContextBuilder> unaryCustomizer =
        (sslContextBuilder) -> customizer.apply(sslContextBuilder);

    optionBuilder
        .option(MariadbConnectionFactoryProvider.SSL_CONTEXT_BUILDER_CUSTOMIZER, unaryCustomizer)
        .option(MariadbConnectionFactoryProvider.TCP_KEEP_ALIVE, true)
        .option(MariadbConnectionFactoryProvider.SSL_MODE, "tunnel");

    return new CloudSqlConnectionFactory(
        MariadbConnectionFactoryProvider::new,
        ipTypes,
        optionBuilder,
        csqlHostName);
  }

  @Override
  ConnectionFactory unixSocketConnectionFactory(Builder optionBuilder, String socket) {
    optionBuilder.option(MariadbConnectionFactoryProvider.SOCKET, socket);
    return new MariadbConnectionFactoryProvider().create(optionBuilder.build());
  }

  @Override
  Builder createBuilder(
      ConnectionFactoryOptions connectionFactoryOptions) {
    return connectionFactoryOptions.mutate().option(DRIVER, MARIADB_DRIVER);
  }
}
