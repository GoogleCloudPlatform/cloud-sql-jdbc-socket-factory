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

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.TlsProvider;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import java.security.Provider;
import java.security.Security;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import org.conscrypt.Conscrypt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * End-to-end R2DBC TLS tests on the production path: the connector builds the instance's
 * ConnectionMetadata (InstanceCheckingTrustManagerFactory, the Conscrypt workaround and the
 * cloudSqlTlsProvider-selected SSLContext and KeyManagerFactory), the R2DBC customizer configures a
 * Netty SslContext from it, and a Netty client completes mTLS with a {@link FakeSslServer} whose
 * certificate passes instance name checking.
 */
public class R2dbcConnectorTlsHandshakeTest extends CloudSqlCoreTestingBase {

  private static final long TEST_MAX_REFRESH_MS = 5000L;
  private static final String X25519_MLKEM768 = "X25519MLKEM768";
  private static final String INSTANCE = "myProject:myRegion:myInstance";

  private ListeningScheduledExecutorService defaultExecutor;
  private List<String> securityProvidersBefore;
  private FakeSslServer sslServer;
  private EventLoopGroup group;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
    securityProvidersBefore = securityProviderNames();
    group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
  }

  @After
  public void tearDown() throws Exception {
    if (sslServer != null) {
      sslServer.stop();
    }
    group.shutdownGracefully(0, 1, TimeUnit.SECONDS).sync();
    defaultExecutor.shutdownNow();
    // Neither the connector nor the R2DBC customizer may touch the JVM-global provider list.
    assertThat(securityProviderNames()).isEqualTo(securityProvidersBefore);
    assertThat(Security.getProvider("Conscrypt")).isNull();
  }

  @Test
  public void jdk_keepsLegacyTls12WithJreProvider() throws Exception {
    // The server supports TLS 1.3 and 1.2.
    sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(TlsProvider.JDK, port);

    if (sslContext instanceof JdkSslContext) {
      assertThat(Conscrypt.isConscrypt(((JdkSslContext) sslContext).context())).isFalse();
    }
    // Not opted in: same TLS configuration as before cloudSqlTlsProvider existed.
    assertThat(handshake(sslContext, port).getProtocol()).isEqualTo("TLSv1.2");
  }

  @Test
  public void unset_keepsLegacyTls12WithJreProvider() throws Exception {
    sslServer = new FakeSslServer();
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(null, port);

    assertThat(handshake(sslContext, port).getProtocol()).isEqualTo("TLSv1.2");
  }

  @Test
  public void conscrypt_negotiatesPostQuantumGroupThroughNetty() throws Exception {
    assumeConscryptAvailable();
    // The server accepts only X25519MLKEM768: the handshake succeeds only if the R2DBC client
    // offered it.
    sslServer = mlkemOnlyServer();
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(TlsProvider.CONSCRYPT, port);

    assertThat(Conscrypt.isConscrypt(((JdkSslContext) sslContext).context())).isTrue();
    assertThat(handshake(sslContext, port).getProtocol()).isEqualTo("TLSv1.3");
  }

  @Test
  public void auto_negotiatesPostQuantumGroupThroughNetty() throws Exception {
    assumeConscryptAvailable();
    sslServer = mlkemOnlyServer();
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(TlsProvider.AUTO, port);

    assertThat(handshake(sslContext, port).getProtocol()).isEqualTo("TLSv1.3");
  }

  @Test
  public void jdk_cannotNegotiatePostQuantumGroupBeforeJdk27() throws Exception {
    assumeConscryptAvailable();
    assumeTrue("JDK 27+ negotiates X25519MLKEM768 natively", javaFeatureVersion() < 27);
    sslServer = mlkemOnlyServer();
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(TlsProvider.JDK, port);

    assertThrows(SSLException.class, () -> handshake(sslContext, port));
  }

  @Test
  public void conscrypt_fallsBackToTls12WhenServerOnlySupportsTls12() throws Exception {
    assumeConscryptAvailable();
    sslServer = new FakeSslServer("TLSv1.2", null, null);
    int port = sslServer.start(PUBLIC_IP);

    SslContext sslContext = r2dbcSslContext(TlsProvider.CONSCRYPT, port);

    assertThat(handshake(sslContext, port).getProtocol()).isEqualTo("TLSv1.2");
  }

  /** Builds the Netty SslContext an R2DBC driver gets from the connector's customizer. */
  private SslContext r2dbcSslContext(TlsProvider tlsProvider, int port) throws Exception {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance(INSTANCE)
            .withIpTypes("PRIMARY")
            .withConnectorConfig(new ConnectorConfig.Builder().withTlsProvider(tlsProvider).build())
            .build();
    ConnectionMetadata metadata =
        newConnector(config.getConnectorConfig(), port)
            .getConnection(config)
            .getConnectionMetadata(TEST_MAX_REFRESH_MS);
    Provider provider = metadata.getSslContextProvider();
    if (tlsProvider == TlsProvider.CONSCRYPT) {
      assertThat(Conscrypt.isConscrypt(provider)).isTrue();
    } else if (tlsProvider == null || tlsProvider == TlsProvider.JDK) {
      assertThat(Conscrypt.isConscrypt(provider)).isFalse();
    }
    return GcpConnectionFactoryProvider.configureSslContextBuilder(
            SslContextBuilder.forClient(), metadata, config.getConnectorConfig().getTlsProvider())
        .build();
  }

  /** Completes a TLS handshake with Netty and checks the server's message arrives. */
  private SSLSession handshake(SslContext sslContext, int port) throws Exception {
    SslHandler sslHandler = sslContext.newHandler(ByteBufAllocator.DEFAULT, PUBLIC_IP, port);
    CompletableFuture<String> received = new CompletableFuture<>();
    Channel channel =
        new Bootstrap()
            .group(group)
            .channel(NioSocketChannel.class)
            .handler(
                new ChannelInitializer<SocketChannel>() {
                  @Override
                  protected void initChannel(SocketChannel ch) {
                    ch.pipeline()
                        .addLast(sslHandler)
                        .addLast(
                            new SimpleChannelInboundHandler<ByteBuf>() {
                              @Override
                              protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
                                received.complete(msg.toString(UTF_8));
                              }
                            });
                  }
                })
            .connect(PUBLIC_IP, port)
            .sync()
            .channel();
    try {
      Future<Channel> handshake = sslHandler.handshakeFuture();
      assertThat(handshake.await(10, TimeUnit.SECONDS)).isTrue();
      if (!handshake.isSuccess()) {
        Throwable cause = handshake.cause();
        throw cause instanceof SSLException ? (SSLException) cause : new SSLException(cause);
      }
      assertThat(received.get(10, TimeUnit.SECONDS)).startsWith(SERVER_MESSAGE);
      return sslHandler.engine().getSession();
    } finally {
      channel.close().sync();
    }
  }

  private static FakeSslServer mlkemOnlyServer() {
    return new FakeSslServer("TLSv1.3", Conscrypt.newProvider(), new String[] {X25519_MLKEM768});
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

  private static void assumeConscryptAvailable() {
    assumeTrue("Conscrypt is not available on this platform", Conscrypt.isAvailable());
  }

  private static int javaFeatureVersion() {
    String version = System.getProperty("java.specification.version");
    return Integer.parseInt(version.startsWith("1.") ? version.substring(2) : version);
  }

  private static List<String> securityProviderNames() {
    return Arrays.stream(Security.getProviders())
        .map(Provider::getName)
        .collect(Collectors.toList());
  }
}
