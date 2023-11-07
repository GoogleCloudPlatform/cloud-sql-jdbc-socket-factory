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
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.annotation.NonNull;

/** {@link ConnectionFactoryProvider} for proxied access to GCP Postgres and MySQL instances. */
public abstract class GcpConnectionFactoryProvider implements ConnectionFactoryProvider {

  public static final Option<String> UNIX_SOCKET = Option.valueOf("UNIX_SOCKET");
  public static final Option<String> IP_TYPES = Option.valueOf("IP_TYPES");
  public static final Option<Boolean> ENABLE_IAM_AUTH = Option.valueOf("ENABLE_IAM_AUTH");
  public static final Option<String> DELEGATES = Option.valueOf("DELEGATES");
  public static final Option<String> NAMED_CONNECTOR = Option.valueOf("NAMED_CONNECTOR");
  public static final Option<String> TARGET_PRINCIPAL = Option.valueOf("TARGET_PRINCIPAL");
  public static final Option<String> ADMIN_ROOT_URL = Option.valueOf("ADMIN_ROOT_URL");
  public static final Option<String> ADMIN_SERVICE_PATH = Option.valueOf("ADMIN_SERVICE_PATH");

  /**
   * Creates a ConnectionFactory that creates an SSL connection over a TCP socket, using
   * driver-specific options.
   */
  abstract ConnectionFactory tcpSocketConnectionFactory(
      ConnectionConfig config,
      Builder optionBuilder,
      Function<SslContextBuilder, SslContextBuilder> customizer);

  /**
   * Creates a ConnectionFactory that creates an SSL connection over a Unix domain socket, using
   * driver-specific options.
   */
  abstract ConnectionFactory unixSocketConnectionFactory(Builder optionBuilder, String socket);

  /** Creates a driver-specific {@link ConnectionFactoryOptions.Builder}. */
  abstract Builder createBuilder(ConnectionFactoryOptions connectionFactoryOptions);

  /** Allows a particular driver to indicate if it supports a protocol. */
  abstract boolean supportedProtocol(String protocol);

  @Override
  @NonNull
  public ConnectionFactory create(ConnectionFactoryOptions connectionFactoryOptions) {
    String protocol = (String) connectionFactoryOptions.getRequiredValue(PROTOCOL);
    if (!supportedProtocol(protocol)) {
      throw new UnsupportedOperationException(
          "Cannot create ConnectionFactory: unsupported protocol (" + protocol + ")");
    }

    String ipTypes = ConnectionConfig.DEFAULT_IP_TYPES;
    Object ipTypesObj = connectionFactoryOptions.getValue(IP_TYPES);
    if (ipTypesObj != null) {
      ipTypes = (String) ipTypesObj;
    }

    boolean enableIamAuth;
    Object iamAuthObj = connectionFactoryOptions.getValue(ENABLE_IAM_AUTH);
    if (iamAuthObj instanceof Boolean) {
      enableIamAuth = (Boolean) iamAuthObj;
    } else if (iamAuthObj instanceof String) {
      enableIamAuth = Boolean.parseBoolean((String) iamAuthObj);
    } else {
      enableIamAuth = false;
    }

    final List<String> delegates;
    Object delegatesObj = connectionFactoryOptions.getValue(DELEGATES);

    if (delegatesObj instanceof String && !((String) delegatesObj).isEmpty()) {
      delegates = Arrays.asList(((String) delegatesObj).split(","));
    } else {
      delegates = Collections.emptyList();
    }
    final String targetPrincipal = (String) connectionFactoryOptions.getValue(TARGET_PRINCIPAL);
    final String namedConnector = (String) connectionFactoryOptions.getValue(NAMED_CONNECTOR);

    final String adminRootUrl = (String) connectionFactoryOptions.getValue(ADMIN_ROOT_URL);
    final String adminServicePath = (String) connectionFactoryOptions.getValue(ADMIN_SERVICE_PATH);

    Builder optionBuilder = createBuilder(connectionFactoryOptions);
    String cloudSqlInstance = (String) connectionFactoryOptions.getRequiredValue(HOST);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(cloudSqlInstance)
            .withAuthType(enableIamAuth ? AuthType.IAM : AuthType.PASSWORD)
            .withIpTypes(ipTypes)
            .withNamedConnector(namedConnector)
            .withConnectorConfig(
                new ConnectorConfig.Builder()
                    .withTargetPrincipal(targetPrincipal)
                    .withDelegates(delegates)
                    .withAdminRootUrl(adminRootUrl)
                    .withAdminServicePath(adminServicePath)
                    .build())
            .build();
    // Precompute SSL Data to trigger the initial refresh to happen immediately,
    // and ensure enableIAMAuth is set correctly.
    InternalConnectorRegistry.getInstance().getConnectionMetadata(config);

    String socket = (String) connectionFactoryOptions.getValue(UNIX_SOCKET);
    if (socket != null) {
      return unixSocketConnectionFactory(optionBuilder, socket);
    }

    Function<SslContextBuilder, SslContextBuilder> sslFunction =
        sslContextBuilder -> {
          // Execute in a default scheduler to prevent it from blocking event loop
          ConnectionMetadata connectionMetadata =
              Mono.fromSupplier(
                      () -> InternalConnectorRegistry.getInstance().getConnectionMetadata(config))
                  .subscribeOn(Schedulers.boundedElastic())
                  .share()
                  .block();
          sslContextBuilder.keyManager(connectionMetadata.getKeyManagerFactory());
          sslContextBuilder.trustManager(connectionMetadata.getTrustManagerFactory());
          sslContextBuilder.protocols("TLSv1.2");

          return sslContextBuilder;
        };
    return tcpSocketConnectionFactory(config, optionBuilder, sslFunction);
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
  @NonNull
  public String getDriver() {
    return "gcp";
  }
}
