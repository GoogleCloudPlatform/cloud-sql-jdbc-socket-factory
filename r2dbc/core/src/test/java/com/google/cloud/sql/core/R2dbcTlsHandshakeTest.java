/*
 * Copyright 2026 Google LLC
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

import com.google.cloud.sql.TlsProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.ssl.SslProvider;
import io.netty.util.concurrent.Future;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.conscrypt.Conscrypt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Handshake tests for the Netty {@link SslContextBuilder} configuration that the R2DBC connector
 * applies, for each kind of SSLContext the connector's {@code cloudSqlTlsProvider} setting can
 * produce: the JRE's default provider ({@code JDK}, and {@code AUTO} when Conscrypt cannot load),
 * and a connector-scoped Conscrypt provider ({@code CONSCRYPT}, and {@code AUTO} when Conscrypt
 * loads).
 *
 * <p>The server is a Conscrypt TLS server. When it is restricted to the hybrid post-quantum group
 * {@code X25519MLKEM768}, a successful handshake proves the R2DBC client offered and negotiated
 * that group: there is no JSSE API that reports the negotiated group.
 */
public class R2dbcTlsHandshakeTest {

  private static final String HYBRID_GROUP = "X25519MLKEM768";

  private EventLoopGroup group;
  private TlsServer server;

  @Before
  public void setUp() {
    group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
  }

  @After
  public void tearDown() throws Exception {
    if (server != null) {
      server.stop();
    }
    group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
  }

  @Test
  public void tlsProtocols_tls13Context_enablesTls13WithTls12Fallback() throws Exception {
    assertThat(GcpConnectionFactoryProvider.tlsProtocols(SSLContext.getInstance("TLSv1.3")))
        .asList()
        .containsExactly("TLSv1.3", "TLSv1.2")
        .inOrder();
  }

  @Test
  public void tlsProtocols_tls12Context_enablesOnlyTls12() throws Exception {
    assertThat(GcpConnectionFactoryProvider.tlsProtocols(SSLContext.getInstance("TLSv1.2")))
        .asList()
        .containsExactly("TLSv1.2");
  }

  @Test
  public void isConnectorScopedProvider_jvmProvider_isFalse() throws Exception {
    Provider jvmProvider = SSLContext.getInstance("TLSv1.3").getProvider();
    assertThat(GcpConnectionFactoryProvider.isConnectorScopedProvider(jvmProvider)).isFalse();
    assertThat(GcpConnectionFactoryProvider.isConnectorScopedProvider(null)).isFalse();
  }

  @Test
  public void isConnectorScopedProvider_unregisteredConscrypt_isTrue() {
    assumeConscryptAvailable();
    Provider conscrypt = Conscrypt.newProvider();
    assertThat(Security.getProvider(conscrypt.getName())).isNotSameInstanceAs(conscrypt);
    assertThat(GcpConnectionFactoryProvider.isConnectorScopedProvider(conscrypt)).isTrue();
  }

  @Test
  public void optedInJreProvider_keepsNettyDefaultProviderAndNegotiatesTls13() throws Exception {
    assumeConscryptAvailable();
    server = new TlsServer(null);
    server.start();

    SSLContext clientContext = SSLContext.getInstance("TLSv1.3");
    SslContext sslContext = buildClientContext(clientContext);

    // AUTO resolved to the JRE provider (JDK 27+, or Conscrypt unavailable): the provider is left
    // to Netty's default selection.
    if (sslContext instanceof JdkSslContext) {
      assertThat(((JdkSslContext) sslContext).context().getProvider())
          .isNotInstanceOf(Conscrypt.newProvider().getClass());
    }

    SSLSession session = handshake(sslContext);
    assertThat(session.getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void optedInJreProvider_cannotNegotiatePostQuantumGroupBeforeJdk27() throws Exception {
    assumeConscryptAvailable();
    assumeTrue("JDK 27+ negotiates X25519MLKEM768 natively", javaFeatureVersion() < 27);
    server = new TlsServer(HYBRID_GROUP);
    server.start();

    SslContext sslContext = buildClientContext(SSLContext.getInstance("TLSv1.3"));

    assertThrows(SSLException.class, () -> handshake(sslContext));
  }

  @Test
  public void conscryptProvider_isUsedByNettyAndNegotiatesTls13() throws Exception {
    assumeConscryptAvailable();
    server = new TlsServer(null);
    server.start();

    Provider conscrypt = Conscrypt.newProvider();
    SslContext sslContext = buildClientContext(SSLContext.getInstance("TLSv1.3", conscrypt));

    assertThat(sslContext).isInstanceOf(JdkSslContext.class);
    assertThat(((JdkSslContext) sslContext).context().getProvider()).isSameInstanceAs(conscrypt);

    SSLSession session = handshake(sslContext);
    assertThat(session.getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void conscryptProvider_negotiatesPostQuantumGroup() throws Exception {
    assumeConscryptAvailable();
    // The server accepts only X25519MLKEM768, so the handshake succeeds only if the R2DBC
    // client offered it.
    server = new TlsServer(HYBRID_GROUP);
    server.start();

    SslContext sslContext =
        buildClientContext(SSLContext.getInstance("TLSv1.3", Conscrypt.newProvider()));

    SSLSession session = handshake(sslContext);
    assertThat(session.getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void conscryptProvider_overridesDriverSelectedOpenSslProvider() throws Exception {
    assumeConscryptAvailable();
    Provider conscrypt = Conscrypt.newProvider();
    // Drivers such as r2dbc-mysql select OPENSSL when netty-tcnative is available, before the
    // connector's customizer runs. The connector must switch back to the JDK SslProvider so that
    // the connector-scoped provider is honored.
    SslContextBuilder builder = SslContextBuilder.forClient().sslProvider(SslProvider.OPENSSL);
    SslContext sslContext =
        GcpConnectionFactoryProvider.configureSslContextBuilder(
                builder,
                metadata(SSLContext.getInstance("TLSv1.3", conscrypt)),
                TlsProvider.CONSCRYPT)
            .build();

    assertThat(((JdkSslContext) sslContext).context().getProvider()).isSameInstanceAs(conscrypt);
  }

  @Test
  public void jdkMode_keepsLegacyTls12OnlyConfiguration() throws Exception {
    assumeConscryptAvailable();
    assertThat(TlsProviders.resolve(TlsProvider.JDK)).isNull();
    server = new TlsServer(null);
    server.start();

    // Not opted in: R2DBC keeps offering only TLS 1.2 even though the connector's SSLContext and
    // the server support TLS 1.3.
    SslContext sslContext = buildClientContext(connectorContext(TlsProvider.JDK), TlsProvider.JDK);

    assertThat(sslContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
        .asList()
        .containsExactly("TLSv1.2");
    assertThat(handshake(sslContext).getProtocol()).isEqualTo("TLSv1.2");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void unsetMode_keepsLegacyTls12OnlyConfiguration() throws Exception {
    SslContext sslContext = buildClientContext(connectorContext((Provider) null), null);

    assertThat(sslContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
        .asList()
        .containsExactly("TLSv1.2");
  }

  @Test
  public void optedInModes_offerTls13WithTls12Fallback() throws Exception {
    for (TlsProvider mode : new TlsProvider[] {TlsProvider.AUTO, TlsProvider.CONSCRYPT}) {
      SslContext sslContext = buildClientContext(connectorContext((Provider) null), mode);

      assertThat(sslContext.newEngine(ByteBufAllocator.DEFAULT).getEnabledProtocols())
          .asList()
          .containsExactly("TLSv1.3", "TLSv1.2");
    }
  }

  @Test
  public void autoMode_negotiatesPostQuantumGroup() throws Exception {
    assumeConscryptAvailable();
    // Before JDK 27 AUTO resolves to Conscrypt, on JDK 27+ to the JRE, which negotiates
    // X25519MLKEM768 natively. Either way the hybrid group must be negotiated.
    server = new TlsServer(HYBRID_GROUP);
    server.start();

    SslContext sslContext =
        buildClientContext(connectorContext(TlsProvider.AUTO), TlsProvider.AUTO);

    assertThat(handshake(sslContext).getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void autoMode_fallsBackToJreProviderWhenConscryptIsUnavailable() throws Exception {
    assumeConscryptAvailable();
    // Simulates musl, an unsupported architecture or a GraalVM native image.
    Provider resolved = TlsProviders.resolve(TlsProvider.AUTO, () -> null, 21);
    assertThat(resolved).isNull();
    server = new TlsServer(null);
    server.start();

    SslContext sslContext = buildClientContext(connectorContext(resolved), TlsProvider.AUTO);

    if (sslContext instanceof JdkSslContext) {
      assertThat(TlsProviders.isConscrypt(((JdkSslContext) sslContext).context().getProvider()))
          .isFalse();
    }
    assertThat(handshake(sslContext).getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void conscryptMode_usesConnectorScopedConscryptAndNegotiatesPostQuantumGroup()
      throws Exception {
    assumeConscryptAvailable();
    Provider resolved = TlsProviders.resolve(TlsProvider.CONSCRYPT);
    assertThat(TlsProviders.isConscrypt(resolved)).isTrue();
    // Connector-scoped: never installed in the JVM-wide provider list.
    assertThat(Security.getProvider(resolved.getName())).isNotSameInstanceAs(resolved);
    server = new TlsServer(HYBRID_GROUP);
    server.start();

    SslContext sslContext = buildClientContext(connectorContext(resolved), TlsProvider.CONSCRYPT);

    assertThat(((JdkSslContext) sslContext).context().getProvider()).isSameInstanceAs(resolved);
    assertThat(handshake(sslContext).getProtocol()).isEqualTo("TLSv1.3");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  @Test
  public void optedInTls12Context_negotiatesTls12() throws Exception {
    assumeConscryptAvailable();
    server = new TlsServer(null);
    server.start();

    SslContext sslContext = buildClientContext(SSLContext.getInstance("TLSv1.2"));

    SSLSession session = handshake(sslContext);
    assertThat(session.getProtocol()).isEqualTo("TLSv1.2");
    assertThat(server.awaitMessage()).isEqualTo("ok");
  }

  private static void assumeConscryptAvailable() {
    assumeTrue("Conscrypt native library is unavailable", Conscrypt.isAvailable());
  }

  private static int javaFeatureVersion() {
    String version = System.getProperty("java.specification.version");
    if (version.startsWith("1.")) {
      return Integer.parseInt(version.substring(2));
    }
    return Integer.parseInt(version);
  }

  /**
   * Builds the client context for an opted-in setting (AUTO, the mode whose SSLContext may come
   * from either the JRE or a connector-scoped provider).
   */
  private SslContext buildClientContext(SSLContext connectorContext) throws Exception {
    return buildClientContext(metadata(connectorContext), TlsProvider.AUTO);
  }

  private SslContext buildClientContext(ConnectionMetadata metadata, TlsProvider tlsProvider) {
    try {
      return GcpConnectionFactoryProvider.configureSslContextBuilder(
              SslContextBuilder.forClient(), metadata, tlsProvider)
          .build();
    } catch (SSLException e) {
      throw new RuntimeException(e);
    }
  }

  /** Builds ConnectionMetadata the way the connector does for the given cloudSqlTlsProvider. */
  private static ConnectionMetadata connectorContext(TlsProvider tlsProvider) throws Exception {
    return connectorContext(TlsProviders.resolve(tlsProvider));
  }

  /**
   * Builds ConnectionMetadata for a resolved provider ({@code null} is the JRE default), mirroring
   * DefaultConnectionInfoRepository: a connector-scoped Conscrypt provider supplies both the
   * SSLContext and the KeyManagerFactory.
   */
  private static ConnectionMetadata connectorContext(Provider provider) throws Exception {
    if (provider == null) {
      return metadata(SSLContext.getInstance("TLSv1.3"));
    }
    return metadata(
        SSLContext.getInstance("TLSv1.3", provider),
        KeyManagerFactory.getInstance("PKIX", provider));
  }

  /** Builds the ConnectionMetadata the connector would hand to the R2DBC customizer. */
  private static ConnectionMetadata metadata(SSLContext connectorContext) throws Exception {
    return metadata(
        connectorContext, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()));
  }

  private static ConnectionMetadata metadata(SSLContext connectorContext, KeyManagerFactory kmf)
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

    // Trust the test server certificate directly.
    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(null, null);
    trustStore.setCertificateEntry("server", TestKeys.getServerCert());
    TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
    tmf.init(trustStore);

    connectorContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
    return new ConnectionMetadata(
        Collections.emptyList(),
        Collections.emptyMap(),
        kmf,
        tmf,
        connectorContext,
        Collections.emptyList());
  }

  private SSLSession handshake(SslContext sslContext) throws Exception {
    SslHandler sslHandler =
        sslContext.newHandler(ByteBufAllocator.DEFAULT, "127.0.0.1", server.port);
    Channel channel =
        new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .handler((ChannelHandler) sslHandler)
            .connect("127.0.0.1", server.port)
            .sync()
            .channel();
    try {
      Future<Channel> handshake = sslHandler.handshakeFuture();
      assertThat(handshake.await(10, TimeUnit.SECONDS)).isTrue();
      if (!handshake.isSuccess()) {
        Throwable cause = handshake.cause();
        if (cause instanceof SSLException) {
          throw (SSLException) cause;
        }
        throw new SSLException(cause);
      }
      return sslHandler.engine().getSession();
    } finally {
      channel.close().sync();
    }
  }

  /**
   * A one-connection Conscrypt TLS 1.3/1.2 server requiring client authentication, optionally
   * restricted to a single named group.
   */
  private static class TlsServer {
    private final String namedGroup;
    private final AtomicReference<String> result = new AtomicReference<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private SSLServerSocket serverSocket;
    private int port;

    TlsServer(String namedGroup) {
      this.namedGroup = namedGroup;
    }

    void start() throws Exception {
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setEntry(
          "server",
          new PrivateKeyEntry(
              TestKeys.getServerKeyPair().getPrivate(),
              new X509Certificate[] {TestKeys.getServerCert()}),
          new PasswordProtection(new char[0]));
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, new char[0]);

      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);
      trustStore.setCertificateEntry("signing-ca", TestKeys.getSigningCaCert());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("PKIX");
      tmf.init(trustStore);

      SSLContext context = SSLContext.getInstance("TLS", Conscrypt.newProvider());
      context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
      serverSocket =
          (SSLServerSocket)
              context
                  .getServerSocketFactory()
                  .createServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
      serverSocket.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
      serverSocket.setNeedClientAuth(true);
      port = serverSocket.getLocalPort();

      Thread thread =
          new Thread(
              () -> {
                try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                  if (namedGroup != null) {
                    Conscrypt.setNamedGroups(socket, new String[] {namedGroup});
                  }
                  socket.startHandshake();
                  result.set("ok");
                } catch (SocketException e) {
                  result.set("closed: " + e);
                } catch (IOException | RuntimeException e) {
                  result.set("failed: " + e);
                } finally {
                  done.countDown();
                }
              });
      thread.setDaemon(true);
      thread.start();
    }

    String awaitMessage() throws InterruptedException {
      assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
      return result.get();
    }

    void stop() throws IOException {
      serverSocket.close();
    }
  }
}
