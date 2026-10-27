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
import com.google.cloud.sql.RefreshStrategy;
import com.google.cloud.sql.TlsProvider;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionFactoryProvider;
import io.r2dbc.spi.Option;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
  public static final Option<String> ADMIN_QUOTA_PROJECT = Option.valueOf("ADMIN_QUOTA_PROJECT");
  public static final Option<String> UNIVERSE_DOMAIN = Option.valueOf("UNIVERSE_DOMAIN");
  public static final Option<String> GOOGLE_CREDENTIALS_PATH =
      Option.valueOf("GOOGLE_CREDENTIALS_PATH");

  public static final Option<String> REFRESH_STRATEGY = Option.valueOf("REFRESH_STRATEGY");

  /**
   * The JSSE provider for the mTLS connection: {@code JDK} (default), {@code AUTO} or {@code
   * CONSCRYPT}. The R2DBC equivalent of the {@code cloudSqlTlsProvider} JDBC property. Accepts a
   * {@link TlsProvider} or its name (case-insensitive).
   */
  public static final Option<Object> TLS_PROVIDER = Option.valueOf("TLS_PROVIDER");

  private static final String TLS_1_3 = "TLSv1.3";
  private static final String TLS_1_2 = "TLSv1.2";

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
    final String adminQuotaProject =
        (String) connectionFactoryOptions.getValue(ADMIN_QUOTA_PROJECT);
    final String googleCredentialsPath =
        (String) connectionFactoryOptions.getValue(GOOGLE_CREDENTIALS_PATH);
    final String universeDomain = (String) connectionFactoryOptions.getValue(UNIVERSE_DOMAIN);
    final RefreshStrategy refreshStrategy =
        "lazy".equalsIgnoreCase((String) connectionFactoryOptions.getValue(REFRESH_STRATEGY))
            ? RefreshStrategy.LAZY
            : RefreshStrategy.BACKGROUND;
    final TlsProvider tlsProvider =
        parseTlsProvider(connectionFactoryOptions.getValue(TLS_PROVIDER));

    final String r2dbcHostname = (String) connectionFactoryOptions.getRequiredValue(HOST);
    final String cloudSqlInstance;
    final String domainName;
    if (CloudSqlInstanceName.isValidInstanceName(r2dbcHostname)) {
      cloudSqlInstance = r2dbcHostname;
      domainName = null;
    } else {
      cloudSqlInstance = null;
      domainName = r2dbcHostname;
    }

    Builder optionBuilder = createBuilder(connectionFactoryOptions);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(cloudSqlInstance)
            .withDomainName(domainName)
            .withAuthType(enableIamAuth ? AuthType.IAM : AuthType.PASSWORD)
            .withIpTypes(ipTypes)
            .withNamedConnector(namedConnector)
            .withConnectorConfig(
                new ConnectorConfig.Builder()
                    .withTargetPrincipal(targetPrincipal)
                    .withDelegates(delegates)
                    .withAdminRootUrl(adminRootUrl)
                    .withAdminServicePath(adminServicePath)
                    .withAdminQuotaProject(adminQuotaProject)
                    .withGoogleCredentialsPath(googleCredentialsPath)
                    .withUniverseDomain(universeDomain)
                    .withRefreshStrategy(refreshStrategy)
                    .withTlsProvider(tlsProvider)
                    .build())
            .build();

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
          return configureSslContextBuilder(
              sslContextBuilder, connectionMetadata, config.getConnectorConfig().getTlsProvider());
        };
    return tcpSocketConnectionFactory(config, optionBuilder, sslFunction);
  }

  /**
   * Configures a driver's Netty {@link SslContextBuilder} with the mTLS material, TLS protocol
   * versions and TLS provider for this instance.
   *
   * <p>Post-quantum support is strictly opt-in. With the default {@link TlsProvider#JDK} (or no
   * setting), the configuration is exactly the one used before {@code cloudSqlTlsProvider} existed:
   * TLS 1.2 only and Netty's default provider selection.
   *
   * <p>With {@link TlsProvider#AUTO} or {@link TlsProvider#CONSCRYPT}, the connector creates the
   * instance's {@link SSLContext} in {@code DefaultConnectionInfoRepository} according to that
   * setting, and this method mirrors it for Netty: TLS 1.3 (with TLS 1.2 fallback), and the same
   * connector-scoped JSSE provider (e.g. Conscrypt, which negotiates the hybrid post-quantum group
   * X25519MLKEM768) as JDBC connections. The JVM-wide provider list is never modified.
   */
  static SslContextBuilder configureSslContextBuilder(
      SslContextBuilder sslContextBuilder,
      ConnectionMetadata connectionMetadata,
      TlsProvider tlsProvider) {
    sslContextBuilder.keyManager(connectionMetadata.getKeyManagerFactory());
    sslContextBuilder.trustManager(connectionMetadata.getTrustManagerFactory());

    if (tlsProvider == null || tlsProvider == TlsProvider.JDK) {
      // Not opted in: keep the TLS configuration R2DBC has always used.
      sslContextBuilder.protocols(TLS_1_2);
    } else {
      configureOptedInTls(sslContextBuilder, connectionMetadata);
    }

    // Disable the default SSL hostname verification. Cloud SQL instances
    // require custom hostname checking logic, which is implemented in the
    // InstanceCheckingTrustManagerFactory.
    sslContextBuilder.endpointIdentificationAlgorithm("");

    return sslContextBuilder;
  }

  /** TLS settings for cloudSqlTlsProvider=AUTO|CONSCRYPT. */
  private static void configureOptedInTls(
      SslContextBuilder sslContextBuilder, ConnectionMetadata connectionMetadata) {
    SSLContext sslContext = connectionMetadata.getSslContext();
    sslContextBuilder.protocols(tlsProtocols(sslContext));

    Provider provider = connectionMetadata.getSslContextProvider();
    if (isConnectorScopedProvider(provider)) {
      // The connector chose a provider instance that is not installed in the JVM (e.g. a
      // Conscrypt provider selected by cloudSqlTlsProvider=AUTO|CONSCRYPT). Netty can only use a
      // specific java.security.Provider through its JDK SslProvider, so select it explicitly;
      // some drivers (e.g. r2dbc-mysql) otherwise default to netty-tcnative when available.
      sslContextBuilder.sslProvider(SslProvider.JDK);
      sslContextBuilder.sslContextProvider(provider);
    }
  }

  /**
   * Parses the {@link #TLS_PROVIDER} option. Returns null (the connector default, {@link
   * TlsProvider#JDK}) when unset.
   *
   * @throws IllegalArgumentException if the value does not name a TlsProvider
   */
  static TlsProvider parseTlsProvider(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof TlsProvider) {
      return (TlsProvider) value;
    }
    if (value instanceof CharSequence) {
      String s = value.toString();
      return s.trim().isEmpty() ? null : TlsProvider.fromString(s);
    }
    throw new IllegalArgumentException(
        String.format(
            "Invalid %s option type %s. Use a TlsProvider or one of JDK, AUTO, CONSCRYPT.",
            TLS_PROVIDER.name(), value.getClass().getName()));
  }

  /**
   * Returns the TLS protocol versions to enable, consistent with the connector's SSLContext: TLS
   * 1.3 (with TLS 1.2 as a fallback, as for JDBC sockets) when the connector created a TLSv1.3
   * context, otherwise TLS 1.2 only (Java runtimes without TLS 1.3 support).
   */
  static String[] tlsProtocols(SSLContext sslContext) {
    if (sslContext != null && TLS_1_3.equals(sslContext.getProtocol())) {
      return new String[] {TLS_1_3, TLS_1_2};
    }
    return new String[] {TLS_1_2};
  }

  /**
   * Returns true when {@code provider} is a provider instance chosen by the connector that is not
   * the provider registered in the JVM under the same name, i.e. the connection must be served by
   * that exact instance rather than by Netty's default.
   */
  static boolean isConnectorScopedProvider(Provider provider) {
    return provider != null && Security.getProvider(provider.getName()) != provider;
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
