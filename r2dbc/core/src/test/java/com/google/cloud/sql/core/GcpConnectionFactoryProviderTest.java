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
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.RefreshStrategy;
import com.google.cloud.sql.SecurityProviderLibrary;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.JdkSslContext;
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
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.reactivestreams.Publisher;

public class GcpConnectionFactoryProviderTest {
  private final ConnectionFactory unixFactory = new StubConnectionFactory(null);

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
          testConnectionMetadata(defaultContext, KeyManagerFactory.getInstance("PKIX", globalPqc));
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

  private static ConnectionMetadata testConnectionMetadata(
      SSLContext sslContext, KeyManagerFactory kmf) throws Exception {
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
        Collections.emptyList());
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

    private StubConnectionFactory(ConnectionConfig config) {
      this.config = config;
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
    GcpConnectionFactoryProvider p =
        new GcpConnectionFactoryProvider() {
          @Override
          ConnectionFactory tcpSocketConnectionFactory(
              ConnectionConfig config,
              ConnectionFactoryOptions.Builder optionBuilder,
              Function<SslContextBuilder, SslContextBuilder> customizer) {
            return new StubConnectionFactory(config);
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
