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
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** {@link ConnectionFactoryProvider} for proxied access to GCP Postgres and MySQL instances. */
public abstract class GcpConnectionFactoryProvider implements ConnectionFactoryProvider {

  public static final Option<String> UNIX_SOCKET = Option.valueOf("UNIX_SOCKET");
  public static final Option<String> IP_TYPES = Option.valueOf("IP_TYPES");
  public static final Option<Boolean> ENABLE_IAM_AUTH = Option.valueOf("ENABLE_IAM_AUTH");

  private static Function<SslContextBuilder, SslContextBuilder> createSslCustomizer(
      String connectionName, boolean enableIamAuth) {

    return sslContextBuilder -> {
      // Execute in a default scheduler to prevent it from blocking event loop
      SslData sslData =
          Mono.fromSupplier(
                  () -> {
                    try {
                      return CoreSocketFactory.getSslData(connectionName, enableIamAuth);
                    } catch (IOException | ExecutionException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .subscribeOn(Schedulers.boundedElastic())
              .share()
              .block();
      sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
      sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
      sslContextBuilder.protocols("TLSv1.2");

      return sslContextBuilder;
    };
  }

  /**
   * Creates a ConnectionFactory that creates an SSL connection over TCP, using driver-specific
   * options.
   */
  abstract ConnectionFactory tcpConnectionFactory(
      Builder optionBuilder,
      String ipTypes,
      Function<SslContextBuilder, SslContextBuilder> customizer,
      String csqlHostName);

  /**
   * Creates a ConnectionFactory that creates an SSL connection over a unix socket, using
   * driver-specific options.
   */
  abstract ConnectionFactory socketConnectionFactory(Builder optionBuilder, String socket);

  /** Creates a driver-specific {@link ConnectionFactoryOptions.Builder}. */
  abstract Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions);

  /** Allows a particular driver to indicate if it supports a protocol. */
  abstract boolean supportedProtocol(String protocol);

  @Override
  public ConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {
    String protocol = (String) connectionFactoryOptions.getRequiredValue(PROTOCOL);

    if (!supportedProtocol(protocol)) {
      throw new UnsupportedOperationException(
          "Cannot create ConnectionFactory: unsupported protocol (" + protocol + ")");
    }

    try {
      return createFactory(connectionFactoryOptions);
    } catch (IOException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  private ConnectionFactory createFactory(ConnectionFactoryOptions connectionFactoryOptions)
      throws IOException, ExecutionException {
    String connectionName = (String) connectionFactoryOptions.getRequiredValue(HOST);

    String ipTypes = CoreSocketFactory.DEFAULT_IP_TYPES;
    Object ipTypesObj = connectionFactoryOptions.getValue(IP_TYPES);
    if (ipTypesObj != null) {
      ipTypes = (String) ipTypesObj;
    }

    Object iamAuthObj = connectionFactoryOptions.getValue(ENABLE_IAM_AUTH);
    boolean enableIamAuth = false;
    if (iamAuthObj instanceof Boolean) {
      enableIamAuth = (Boolean) iamAuthObj;
    } else if (iamAuthObj instanceof String) {
      enableIamAuth = Boolean.parseBoolean((String) iamAuthObj);
    }

    Builder optionBuilder = createBuilder(connectionFactoryOptions);

    // Precompute SSL Data to trigger the initial refresh to happen immediately,
    // and ensure enableIAMAuth is set correctly.
    CoreSocketFactory.getSslData(connectionName, enableIamAuth);

    String socket = (String) connectionFactoryOptions.getValue(UNIX_SOCKET);
    if (socket != null) {
      return socketConnectionFactory(optionBuilder, socket);
    }

    return tcpConnectionFactory(
        optionBuilder, ipTypes, createSslCustomizer(connectionName, enableIamAuth), connectionName);
  }

  @Override
  public boolean supports(ConnectionFactoryOptions connectionFactoryOptions) {
    String driver = (String) connectionFactoryOptions.getValue(DRIVER);
    String protocol = (String) connectionFactoryOptions.getValue(PROTOCOL);

    return driver != null
        && protocol != null
        && driver.equals(getDriver())
        && supportedProtocol(protocol);
  }

  @Override
  public String getDriver() {
    return "gcp";
  }
}
