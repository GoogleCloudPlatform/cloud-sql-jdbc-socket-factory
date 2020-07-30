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
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import java.util.Properties;
import java.util.function.Function;

/** {@link ConnectionFactoryProvider} for proxied access to GCP Postgres and MySQL instances. */
public abstract class GcpConnectionFactoryProvider implements ConnectionFactoryProvider {

  abstract ConnectionFactory tcpConnectonFactory(
      Builder optionBuilder, Function<SslContextBuilder, SslContextBuilder> customizer);

  abstract ConnectionFactory socketConnectionFactory(Builder optionBuilder, String socket);

  abstract Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions);

  abstract boolean supportedProtocol(String protocol);

  @Override
  public ConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {
    String connectionName = connectionFactoryOptions.getRequiredValue(HOST);
    String protocol = connectionFactoryOptions.getRequiredValue(PROTOCOL);

    Properties properties = new Properties();
    properties.put(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY, connectionName);

    if (!supportedProtocol(protocol)) {
        throw new UnsupportedOperationException(
        "Cannot create ConnectionFactory: unsupported protocol" + protocol);
    }
    
    return createFactory(connectionFactoryOptions, properties);
  }

  private ConnectionFactory createFactory(
      ConnectionFactoryOptions connectionFactoryOptions, Properties properties) {
    Builder optionBuilder = createBuilder(connectionFactoryOptions);

    // precompute SSL Data to avoid blocking calls during the connect phase.
    CoreSocketFactory.getSslData(properties);
    String socket = CoreSocketFactory.getUnixSocketArg(properties);

    if (socket != null) {
      return socketConnectionFactory(optionBuilder, socket);
    }
    return tcpConnectonFactory(optionBuilder, createSslCustomizer(optionBuilder, properties));
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
      Builder optionBuilder, Properties properties) {
    String hostIp = CoreSocketFactory.getHostIp(properties);
    optionBuilder.option(HOST, hostIp).option(PORT, CoreSocketFactory.getDefaultServerProxyPort());

    Function<SslContextBuilder, SslContextBuilder> customizer =
        sslContextBuilder -> {
          SslData sslData = CoreSocketFactory.getSslData(properties);

          sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
          sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
          sslContextBuilder.protocols("TLSv1.2");

          return sslContextBuilder;
        };

    return customizer;
  }
}
