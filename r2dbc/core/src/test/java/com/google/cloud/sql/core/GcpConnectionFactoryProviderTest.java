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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.RefreshStrategy;
import com.google.cloud.sql.SecurityProviderLibrary;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.OpenSsl;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryMetadata;
import io.r2dbc.spi.ConnectionFactoryOptions;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.Collections;
import java.util.function.Function;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.conscrypt.Conscrypt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.reactivestreams.Publisher;

public class GcpConnectionFactoryProviderTest extends CloudSqlCoreTestingBase {
  private static final String TEST_INSTANCE = "myProject:myRegion:myInstance";
  private final ConnectionFactory unixFactory = new StubConnectionFactory(null, null);
  private ListeningScheduledExecutorService defaultExecutor;

  @Before
  public void setUpExecutor() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
  }

  @After
  public void tearDownExecutor() {
    if (defaultExecutor != null) {
      defaultExecutor.shutdownNow();
    }
  }

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
    options.option(GcpConnectionFactoryProvider.TLS_PROVIDER, "conscrypt");

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
    assertThat(config.getConnectorConfig().getSecurityProviderLibrary())
        .isEqualTo(SecurityProviderLibrary.CONSCRYPT);
  }

  @Test
  public void testTlsProviderDefaultsToJdk() {
    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");

    StubConnectionFactory factory = configureConnection(options.build());

    assertThat(factory.config.getConnectorConfig().getSecurityProviderLibrary())
        .isEqualTo(SecurityProviderLibrary.JDK);
  }

  @Test
  public void testTlsProviderAcceptsEnumAndStrings() {
    for (Object value : new Object[] {SecurityProviderLibrary.AUTO, "AUTO", "auto", " Auto "}) {
      ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
      options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
      options.option(ConnectionFactoryOptions.HOST, "project:region:instance");
      options.option(GcpConnectionFactoryProvider.TLS_PROVIDER, value);

      StubConnectionFactory factory = configureConnection(options.build());

      assertThat(factory.config.getConnectorConfig().getSecurityProviderLibrary())
          .isEqualTo(SecurityProviderLibrary.AUTO);
    }
    assertThat(GcpConnectionFactoryProvider.parseSecurityProviderLibrary("jdk"))
        .isEqualTo(SecurityProviderLibrary.JDK);
    assertThat(GcpConnectionFactoryProvider.parseSecurityProviderLibrary("")).isNull();
    assertThat(GcpConnectionFactoryProvider.parseSecurityProviderLibrary(null)).isNull();
  }

  @Test
  public void testTlsProviderRejectsInvalidValues() {
    ConnectionFactoryOptions.Builder options = ConnectionFactoryOptions.builder();
    options.option(ConnectionFactoryOptions.PROTOCOL, "cloudsql");
    options.option(ConnectionFactoryOptions.HOST, "project:region:instance");
    options.option(GcpConnectionFactoryProvider.TLS_PROVIDER, "bogus");

    assertThrows(IllegalArgumentException.class, () -> configureConnection(options.build()));
    assertThrows(
        IllegalArgumentException.class,
        () -> GcpConnectionFactoryProvider.parseSecurityProviderLibrary(42));
  }

  @Test
  public void testConfigureSslContextBuilder_connectorScopedConscryptOverridesOpenSsl()
      throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    Provider conscrypt = Conscrypt.newProvider();
    assertThat(GcpConnectionFactoryProvider.isConnectorScopedProvider(conscrypt)).isTrue();
    ConnectionMetadata metadata =
        testConnectionMetadata(
            SSLContext.getInstance("TLSv1.3", conscrypt),
            KeyManagerFactory.getInstance("PKIX", conscrypt));

    // Pre-select SslProvider.OPENSSL (as r2dbc-mysql does when netty-tcnative is present).
    SslContext nettyContext =
        GcpConnectionFactoryProvider.configureSslContextBuilder(
                SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL),
                metadata,
                SecurityProviderLibrary.CONSCRYPT)
            .build();

    assertThat(nettyContext).isInstanceOf(JdkSslContext.class);
    assertThat(((JdkSslContext) nettyContext).context().getProvider()).isSameInstanceAs(conscrypt);
    assertThat(nettyContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
        .asList()
        .containsExactly("TLSv1.3", "TLSv1.2");
  }

  @Test
  public void testConfigureSslContextBuilder_pqcDefaultContextOverridesOpenSsl() throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    // Install a PQC-capable provider globally at priority 1 to simulate a JVM whose default
    // TLSv1.3 SSLContext already advertises X25519MLKEM768 (e.g. JDK 27+, JDK 25.0.5+, or a
    // user-registered BCJSSE/Conscrypt provider, where isConnectorScopedProvider is false).
    Provider globalPqc = Conscrypt.newProvider();
    assertThat(Security.getProvider(globalPqc.getName())).isNull();
    Security.insertProviderAt(globalPqc, 1);
    try {
      SSLContext defaultContext = SSLContext.getInstance("TLSv1.3");
      assertThat(defaultContext.getProvider()).isSameInstanceAs(globalPqc);
      assertThat(GcpConnectionFactoryProvider.isConnectorScopedProvider(globalPqc)).isFalse();
      assertThat(SecurityProviderFactory.supportsX25519MlKem768(defaultContext)).isTrue();

      ConnectionMetadata metadata =
          testConnectionMetadata(
              defaultContext,
              KeyManagerFactory.getInstance("PKIX", globalPqc),
              SecurityProviderLibrary.AUTO);
      SslContext nettyContext =
          GcpConnectionFactoryProvider.configureSslContextBuilder(
                  SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL),
                  metadata,
                  SecurityProviderLibrary.AUTO)
              .build();

      assertThat(nettyContext).isInstanceOf(JdkSslContext.class);
      assertThat(((JdkSslContext) nettyContext).context().getProvider())
          .isSameInstanceAs(globalPqc);
      assertThat(nettyContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
          .asList()
          .containsExactly("TLSv1.3", "TLSv1.2");
    } finally {
      Security.removeProvider(globalPqc.getName());
    }
  }

  @Test
  public void testNamedConnector_autoAndConscryptWithoutUrlTlsProviderEnableTls13AndJdkProvider()
      throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    InternalConnectorRegistry registry = newTestRegistry();
    try {
      registry.register(
          "named-auto",
          new ConnectorConfig.Builder()
              .withSecurityProviderLibrary(SecurityProviderLibrary.AUTO)
              .build());
      registry.register(
          "named-conscrypt",
          new ConnectorConfig.Builder()
              .withSecurityProviderLibrary(SecurityProviderLibrary.CONSCRYPT)
              .build());

      // Named connector with AUTO and NO TLS_PROVIDER option on R2DBC ConnectionFactoryOptions.
      StubConnectionFactory autoFactory =
          configureConnection(
              ConnectionFactoryOptions.builder()
                  .option(ConnectionFactoryOptions.PROTOCOL, "cloudsql")
                  .option(ConnectionFactoryOptions.HOST, TEST_INSTANCE)
                  .option(GcpConnectionFactoryProvider.IP_TYPES, "PUBLIC")
                  .option(GcpConnectionFactoryProvider.NAMED_CONNECTOR, "named-auto")
                  .build(),
              registry);
      assertThat(autoFactory.config.getConnectorConfig().getSecurityProviderLibrary())
          .isEqualTo(SecurityProviderLibrary.JDK);

      SslContext autoNettyContext =
          autoFactory
              .customizer
              .apply(SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL))
              .build();
      assertThat(autoNettyContext).isInstanceOf(JdkSslContext.class);
      assertThat(autoNettyContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
          .asList()
          .containsExactly("TLSv1.3", "TLSv1.2");

      // Named connector with CONSCRYPT and NO TLS_PROVIDER option on R2DBC
      // ConnectionFactoryOptions.
      StubConnectionFactory conscryptFactory =
          configureConnection(
              ConnectionFactoryOptions.builder()
                  .option(ConnectionFactoryOptions.PROTOCOL, "cloudsql")
                  .option(ConnectionFactoryOptions.HOST, TEST_INSTANCE)
                  .option(GcpConnectionFactoryProvider.IP_TYPES, "PUBLIC")
                  .option(GcpConnectionFactoryProvider.NAMED_CONNECTOR, "named-conscrypt")
                  .build(),
              registry);
      SslContext conscryptNettyContext =
          conscryptFactory
              .customizer
              .apply(SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL))
              .build();
      assertThat(conscryptNettyContext).isInstanceOf(JdkSslContext.class);
      assertThat(Conscrypt.isConscrypt(((JdkSslContext) conscryptNettyContext).context())).isTrue();
      assertThat(conscryptNettyContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
          .asList()
          .containsExactly("TLSv1.3", "TLSv1.2");
    } finally {
      registry.shutdown();
    }
  }

  @Test
  public void testNamedConnector_defaultJdkKeepsTls12AndLeavesOpenSslUntouched() throws Exception {
    InternalConnectorRegistry registry = newTestRegistry();
    try {
      registry.register("named-jdk", new ConnectorConfig.Builder().build());

      // Even if TLS_PROVIDER is unset OR set to AUTO on the URL, the registered named connector's
      // JDK configuration owns the instance's ConnectionMetadata.
      for (String urlTlsProvider : new String[] {null, "AUTO"}) {
        ConnectionFactoryOptions.Builder options =
            ConnectionFactoryOptions.builder()
                .option(ConnectionFactoryOptions.PROTOCOL, "cloudsql")
                .option(ConnectionFactoryOptions.HOST, TEST_INSTANCE)
                .option(GcpConnectionFactoryProvider.IP_TYPES, "PUBLIC")
                .option(GcpConnectionFactoryProvider.NAMED_CONNECTOR, "named-jdk");
        if (urlTlsProvider != null) {
          options.option(GcpConnectionFactoryProvider.TLS_PROVIDER, urlTlsProvider);
        }

        StubConnectionFactory factory = configureConnection(options.build(), registry);

        SslContext defaultProviderContext =
            factory.customizer.apply(SslContextBuilder.forClient()).build();
        assertThat(defaultProviderContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
            .asList()
            .containsExactly("TLSv1.2");

        SslContextBuilder openSslBuilder =
            factory.customizer.apply(
                SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL));
        assertOpenSslProviderLeftUntouched(openSslBuilder);
      }
    } finally {
      registry.shutdown();
    }
  }

  @Test
  public void testUnnamedDefaultJdk_onPqcCapableSslContextKeepsTls12AndLeavesOpenSslUntouched()
      throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    // Regression guard against deciding protocol/provider purely from isSslContextSupportsPqc():
    // on JDK 27+, JDK 25.0.5+, or global BCJSSE/Conscrypt, ConnectionMetadata's TLSv1.3 SSLContext
    // advertises X25519MLKEM768 even when TLS_PROVIDER is unset / JDK. R2DBC must still stay on
    // TLSv1.2 and leave a driver-selected SslProvider.OPENSSL untouched.
    Provider pqcProvider = Conscrypt.newProvider();
    SSLContext pqcContext = SSLContext.getInstance("TLSv1.3", pqcProvider);
    ConnectionMetadata pqcMetadataInJdkMode =
        testConnectionMetadata(
            pqcContext,
            KeyManagerFactory.getInstance("PKIX", pqcProvider),
            SecurityProviderLibrary.JDK);
    assertThat(pqcMetadataInJdkMode.getSecurityProviderLibrary())
        .isEqualTo(SecurityProviderLibrary.JDK);
    assertThat(pqcMetadataInJdkMode.isSslContextSupportsPqc()).isTrue();

    SslContext defaultNettyContext =
        GcpConnectionFactoryProvider.configureSslContextBuilder(
                SslContextBuilder.forClient(),
                pqcMetadataInJdkMode,
                pqcMetadataInJdkMode.getSecurityProviderLibrary())
            .build();
    assertThat(defaultNettyContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
        .asList()
        .containsExactly("TLSv1.2");

    SslContextBuilder openSslBuilder =
        GcpConnectionFactoryProvider.configureSslContextBuilder(
            SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL),
            pqcMetadataInJdkMode,
            pqcMetadataInJdkMode.getSecurityProviderLibrary());
    assertOpenSslProviderLeftUntouched(openSslBuilder);
  }

  @Test
  public void testConfigureSslContextBuilder_usesCachedPqcFlagWithoutProbingSslContextPerCall()
      throws Exception {
    Provider nonScopedProvider = SSLContext.getInstance("TLSv1.3").getProvider();
    SSLContext failIfProbedContext =
        new SSLContext(
            new SSLContextSpi() {
              @Override
              protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) {}

              @Override
              protected SSLSocketFactory engineGetSocketFactory() {
                return null;
              }

              @Override
              protected SSLServerSocketFactory engineGetServerSocketFactory() {
                return null;
              }

              @Override
              protected SSLEngine engineCreateSSLEngine() {
                throw new AssertionError(
                    "configureSslContextBuilder must use ConnectionMetadata.isSslContextSupportsPqc"
                        + " instead of probing SSLContext.createSSLEngine() per connection");
              }

              @Override
              protected SSLEngine engineCreateSSLEngine(String host, int port) {
                return engineCreateSSLEngine();
              }

              @Override
              protected SSLSessionContext engineGetServerSessionContext() {
                return null;
              }

              @Override
              protected SSLSessionContext engineGetClientSessionContext() {
                return null;
              }
            },
            nonScopedProvider,
            "TLSv1.3") {};

    ConnectionMetadata base =
        testConnectionMetadata(
            SSLContext.getInstance("TLSv1.3"),
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()),
            SecurityProviderLibrary.AUTO);
    ConnectionMetadata cachedPqcMetadata =
        new ConnectionMetadata(
            Collections.emptyList(),
            Collections.emptyMap(),
            base.getKeyManagerFactory(),
            base.getTrustManagerFactory(),
            failIfProbedContext,
            Collections.emptyList(),
            SecurityProviderLibrary.AUTO,
            true);

    SslContext nettyContext =
        GcpConnectionFactoryProvider.configureSslContextBuilder(
                SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL),
                cachedPqcMetadata,
                SecurityProviderLibrary.AUTO)
            .build();
    assertThat(nettyContext).isInstanceOf(JdkSslContext.class);
    assertThat(((JdkSslContext) nettyContext).context().getProvider())
        .isSameInstanceAs(nonScopedProvider);
  }

  private static void assertOpenSslProviderLeftUntouched(SslContextBuilder openSslBuilder)
      throws Exception {
    if (OpenSsl.isAvailable()) {
      SslContext ctx = openSslBuilder.build();
      assertThat(ctx).isNotInstanceOf(JdkSslContext.class);
      assertThat(ctx.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
          .asList()
          .containsExactly("TLSv1.2");
    } else {
      // netty-tcnative is absent in r2dbc-core unit tests, so building with SslProvider.OPENSSL
      // throws UnsatisfiedLinkError if and only if SslProvider.OPENSSL was left untouched.
      assertThrows(UnsatisfiedLinkError.class, openSslBuilder::build);
    }
  }

  private InternalConnectorRegistry newTestRegistry() {
    ConnectionInfoRepositoryFactory repoFactory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    return new InternalConnectorRegistry(
        clientKeyPair,
        repoFactory,
        stubCredentialFactoryProvider,
        InternalConnectorRegistry.DEFAULT_SERVER_PROXY_PORT,
        5000L,
        defaultExecutor);
  }

  private static ConnectionMetadata testConnectionMetadata(
      SSLContext sslContext, KeyManagerFactory kmf) throws Exception {
    return testConnectionMetadata(sslContext, kmf, SecurityProviderLibrary.CONSCRYPT);
  }

  private static ConnectionMetadata testConnectionMetadata(
      SSLContext sslContext, KeyManagerFactory kmf, SecurityProviderLibrary securityProviderLibrary)
      throws Exception {
    KeyStore clientKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    clientKeyStore.load(null, null);
    Certificate clientCert =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(
                new ByteArrayInputStream(
                    TestKeys.createEphemeralCert(Duration.ZERO).getBytes(UTF_8)));
    clientKeyStore.setEntry(
        "ephemeral",
        new PrivateKeyEntry(
            TestKeys.getClientKeyPair().getPrivate(), new Certificate[] {clientCert}),
        new PasswordProtection(new char[0]));
    kmf.init(clientKeyStore, new char[0]);

    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    trustStore.setCertificateEntry("server", TestKeys.getServerCert());
    TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
    tmf.init(trustStore);

    sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
    return new ConnectionMetadata(
        Collections.emptyList(),
        Collections.emptyMap(),
        kmf,
        tmf,
        sslContext,
        Collections.emptyList(),
        securityProviderLibrary,
        SecurityProviderFactory.supportsX25519MlKem768(sslContext));
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
    final Function<SslContextBuilder, SslContextBuilder> customizer;

    private StubConnectionFactory(
        ConnectionConfig config, Function<SslContextBuilder, SslContextBuilder> customizer) {
      this.config = config;
      this.customizer = customizer;
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
    return configureConnection(options, null);
  }

  private StubConnectionFactory configureConnection(
      ConnectionFactoryOptions options, InternalConnectorRegistry registryOverride) {
    GcpConnectionFactoryProvider p =
        new GcpConnectionFactoryProvider() {
          @Override
          InternalConnectorRegistry getConnectorRegistry() {
            return registryOverride != null ? registryOverride : super.getConnectorRegistry();
          }

          @Override
          ConnectionFactory tcpSocketConnectionFactory(
              ConnectionConfig config,
              ConnectionFactoryOptions.Builder optionBuilder,
              Function<SslContextBuilder, SslContextBuilder> customizer) {
            return new StubConnectionFactory(config, customizer);
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
