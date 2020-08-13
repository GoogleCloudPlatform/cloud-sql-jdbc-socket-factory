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
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;
import java.util.function.Function;

/** {@link ConnectionFactoryProvider} for proxied access to GCP Postgres and MySQL instances. */
public abstract class GcpConnectionFactoryProvider implements ConnectionFactoryProvider {

  /**
   * Creates a ConnectionFactory that creates an SSL connection over TCP,
   * using driver-specific options.
   */
  abstract ConnectionFactory tcpConnectonFactory(
      Builder optionBuilder,
      Function<SslContextBuilder, SslContextBuilder> customizer,
      String csqlHostName);

  /**
   * Creates a ConnectionFactory that creates an SSL connection over a unix socket,
   * using driver-specific options.
   */
  abstract ConnectionFactory socketConnectionFactory(Builder optionBuilder, String socket);

  /**
   * Creates a driver-specific {@link ConnectionFactoryOptions.Builder}.
   */
  abstract Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions);

  /**
   * Allows a particular driver to indicate if it supports a protocol.
   */
  abstract boolean supportedProtocol(String protocol);

  private static final Option<String> UNIX_SOCKET = Option.valueOf("UNIX_SOCKET");

  @Override
  public ConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {
    String protocol = connectionFactoryOptions.getRequiredValue(PROTOCOL);

    if (!supportedProtocol(protocol)) {
      throw new UnsupportedOperationException(
          "Cannot create ConnectionFactory: unsupported protocol (" + protocol + ")");
    }

    return createFactory(connectionFactoryOptions);
  }

  private ConnectionFactory createFactory(
      ConnectionFactoryOptions connectionFactoryOptions) {
    String connectionName = connectionFactoryOptions.getRequiredValue(HOST);
    String socket = connectionFactoryOptions.getValue(UNIX_SOCKET);

    Builder optionBuilder = createBuilder(connectionFactoryOptions);

    // precompute SSL Data to avoid blocking calls during the connect phase.
    SslData sslData = CoreSocketFactory.getSslData(connectionName);

    if (socket != null) {
      return socketConnectionFactory(optionBuilder, socket);
    }
    return tcpConnectonFactory(optionBuilder, createSslCustomizer(sslData), connectionName);
  }

  @Override
  public boolean supports(ConnectionFactoryOptions connectionFactoryOptions) {
    String driver = connectionFactoryOptions.getValue(DRIVER);
    String protocol = connectionFactoryOptions.getValue(PROTOCOL);

    return driver != null
        && protocol != null
        && driver.equals(getDriver())
        && supportedProtocol(protocol);
  }

  @Override
  public String getDriver() {
    return "gcp";
  }

  private static Function<SslContextBuilder, SslContextBuilder> createSslCustomizer(
      SslData sslData) {

    Function<SslContextBuilder, SslContextBuilder> customizer =
        sslContextBuilder -> {
          sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
          sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
          sslContextBuilder.protocols("TLSv1.2");

          return sslContextBuilder;
        };

    return customizer;
  }
}
