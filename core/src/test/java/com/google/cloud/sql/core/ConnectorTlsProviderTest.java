/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import static com.google.cloud.sql.core.InternalConnectorRegistry.DEFAULT_SERVER_PROXY_PORT;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.TlsProvider;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.security.Provider;
import java.security.Security;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import org.conscrypt.Conscrypt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * End-to-end tests of the {@code cloudSqlTlsProvider} setting: the connector dials a {@link
 * FakeSslServer} and the test inspects the negotiated TLS session.
 */
@RunWith(JUnit4.class)
public class ConnectorTlsProviderTest extends CloudSqlCoreTestingBase {

  private static final long TEST_MAX_REFRESH_MS = 5000L;
  private static final String X25519_MLKEM768 = "X25519MLKEM768";
  private static final String INSTANCE = "myProject:myRegion:myInstance";

  private ListeningScheduledExecutorService defaultExecutor;
  private List<String> securityProvidersBefore;
  private FakeSslServer sslServer;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
    securityProvidersBefore = securityProviderNames();
  }

  @After
  public void tearDown() throws Exception {
    if (sslServer != null) {
      sslServer.stop();
    }
    defaultExecutor.shutdownNow();
    // The connector must never install Conscrypt (or anything else) into the JVM-global provider
    // list.
    assertThat(securityProviderNames()).isEqualTo(securityProvidersBefore);
    assertThat(Security.getProvider("Conscrypt")).isNull();
  }

  @Test
  public void jdkDefault_usesJreProvider() throws Exception {
    sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    ConnectionConfig config = connectionConfig(null);
    assertThat(config.getConnectorConfig().getTlsProvider()).isEqualTo(TlsProvider.JDK);

    SSLSocket socket = connect(config, port);

    assertThat(Conscrypt.isConscrypt(socket)).isFalse();
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void conscrypt_usesConnectorScopedConscryptProvider() throws Exception {
    assumeConscryptAvailable();
    sslServer = new FakeSslServer("TLSv1.3", null, null);
    int port = sslServer.start(PUBLIC_IP);

    SSLSocket socket = connect(connectionConfig(TlsProvider.CONSCRYPT), port);

    assertThat(Conscrypt.isConscrypt(socket)).isTrue();
    assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void conscrypt_fallsBackToTls12WhenServerOnlySupportsTls12() throws Exception {
    assumeConscryptAvailable();
    sslServer = new FakeSslServer("TLSv1.2", null, null);
    int port = sslServer.start(PUBLIC_IP);

    SSLSocket socket = connect(connectionConfig(TlsProvider.CONSCRYPT), port);

    assertThat(Conscrypt.isConscrypt(socket)).isTrue();
    assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.2");
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void auto_usesConscryptBeforeJdk27() throws Exception {
    assumeConscryptAvailable();
    assumeTrue(
        "JDK 27+ negotiates ML-KEM natively, so AUTO uses the JRE provider",
        TlsProviders.parseJreVersion(System.getProperty("java.specification.version")) < 27);
    sslServer = new FakeSslServer("TLSv1.3", null, null);
    int port = sslServer.start(PUBLIC_IP);

    SSLSocket socket = connect(connectionConfig(TlsProvider.AUTO), port);

    assertThat(Conscrypt.isConscrypt(socket)).isTrue();
    assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  /**
   * The server only accepts the X25519MLKEM768 key exchange group, so a successful TLS 1.3
   * handshake proves that the connector negotiated X25519MLKEM768. JSSE has no API to report the
   * negotiated group directly.
   */
  @Test
  public void conscrypt_negotiatesX25519Mlkem768() throws Exception {
    assumeConscryptAvailable();
    sslServer = mlkemOnlyServer();
    int port = sslServer.start(PUBLIC_IP);

    SSLSocket socket = connect(connectionConfig(TlsProvider.CONSCRYPT), port);

    assertThat(Conscrypt.isConscrypt(socket)).isTrue();
    assertThat(socket.getSession().getProtocol()).isEqualTo("TLSv1.3");
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  /**
   * Control for {@link #conscrypt_negotiatesX25519Mlkem768()}: a JRE without ML-KEM support cannot
   * complete a handshake with the X25519MLKEM768-only server, which shows that the server's group
   * restriction is effective.
   */
  @Test
  public void jdk_cannotNegotiateWithMlkemOnlyServerBeforeJdk27() throws Exception {
    assumeConscryptAvailable();
    assumeTrue(TlsProviders.parseJreVersion(System.getProperty("java.specification.version")) < 27);
    sslServer = mlkemOnlyServer();
    int port = sslServer.start(PUBLIC_IP);

    ConnectionConfig config = connectionConfig(TlsProvider.JDK);
    Connector connector = newConnector(config.getConnectorConfig(), port);
    assertThrows(SSLException.class, () -> connector.connect(config, TEST_MAX_REFRESH_MS));
  }

  /** Instance name verification still applies when Conscrypt serves the connection. */
  @Test
  public void conscrypt_rejectsServerCertificateForWrongInstance() throws Exception {
    assumeConscryptAvailable();
    sslServer = new FakeSslServer("TLSv1.3", null, null);
    int port = sslServer.start(PUBLIC_IP);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:wrongwrongwrong")
            .withIpTypes("PRIMARY")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withTlsProvider(TlsProvider.CONSCRYPT).build())
            .build();
    Connector connector = newConnector(config.getConnectorConfig(), port);

    SSLException ex =
        assertThrows(SSLException.class, () -> connector.connect(config, TEST_MAX_REFRESH_MS));
    assertThat(ex).hasMessageThat().contains("Server certificate CN does not match instance name");
  }

  @Test
  public void conscrypt_connectionMetadataExposesSslContextProvider() throws Exception {
    assumeConscryptAvailable();
    ConnectionConfig config = connectionConfig(TlsProvider.CONSCRYPT);
    Connector connector = newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT);

    ConnectionMetadata metadata = connector.getConnection(config).getConnectionMetadata(10_000L);

    Provider provider = metadata.getSslContextProvider();
    assertThat(Conscrypt.isConscrypt(provider)).isTrue();
    assertThat(metadata.getSslContext().getProtocol()).isEqualTo("TLSv1.3");
  }

  @Test
  public void jdk_connectionMetadataExposesJreSslContextProvider() throws Exception {
    ConnectionConfig config = connectionConfig(TlsProvider.JDK);
    Connector connector = newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT);

    ConnectionMetadata metadata = connector.getConnection(config).getConnectionMetadata(10_000L);

    assertThat(metadata.getSslContextProvider().getName()).isNotEqualTo("Conscrypt");
  }

  private static FakeSslServer mlkemOnlyServer() {
    return new FakeSslServer("TLSv1.3", Conscrypt.newProvider(), new String[] {X25519_MLKEM768});
  }

  private static void assumeConscryptAvailable() {
    assumeTrue("Conscrypt is not available on this platform", Conscrypt.isAvailable());
  }

  private static ConnectionConfig connectionConfig(TlsProvider tlsProvider) {
    ConnectorConfig.Builder connectorConfig = new ConnectorConfig.Builder();
    if (tlsProvider != null) {
      connectorConfig.withTlsProvider(tlsProvider);
    }
    return new ConnectionConfig.Builder()
        .withCloudSqlInstance(INSTANCE)
        .withIpTypes("PRIMARY")
        .withConnectorConfig(connectorConfig.build())
        .build();
  }

  private SSLSocket connect(ConnectionConfig config, int port) throws IOException {
    Connector connector = newConnector(config.getConnectorConfig(), port);
    return (SSLSocket) connector.connect(config, TEST_MAX_REFRESH_MS);
  }

  private Connector newConnector(ConnectorConfig config, int port) {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));
    return new Connector(
        config,
        factory,
        stubCredentialFactoryProvider.getInstanceCredentialFactory(config),
        defaultExecutor,
        clientKeyPair,
        10,
        TEST_MAX_REFRESH_MS,
        port,
        null,
        new ProtocolHandler("test"));
  }

  private static String readLine(Socket socket) throws IOException {
    return new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8)).readLine();
  }

  private static List<String> securityProviderNames() {
    return Arrays.stream(Security.getProviders())
        .map(Provider::getName)
        .collect(Collectors.toList());
  }
}
