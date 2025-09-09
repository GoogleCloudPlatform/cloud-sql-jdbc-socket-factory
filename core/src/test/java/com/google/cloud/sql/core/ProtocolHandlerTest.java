/*
 * Copyright 2025 Google LLC
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

import com.google.cloud.sql.core.mdx.MetadataExchange;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Test;

public class ProtocolHandlerTest {

  @Test
  public void testSendMdx() throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new ProtocolHandler("ua")
        .sendMdx(out, MetadataExchange.MetadataExchangeRequest.ClientProtocolType.TLS);
    // Check that the MDX request was sent to the output stream
    assertThat(out.toByteArray()).isEqualTo(wantRequestBytes());
  }

  @Test
  public void testReadMdx_WithMdxResponse() throws IOException {
    byte[] wantInBytes = "hello client".getBytes(StandardCharsets.UTF_8);
    byte[] resBytes = wantResponseBytes(wantInBytes);

    ByteArrayInputStream in = new ByteArrayInputStream(resBytes);
    MetadataExchange.MetadataExchangeResponse res = new ProtocolHandler("ua").readMdxResponse(in);
    // Check that there was no MDX response
    assertThat(res).isNotNull();

    // Check that the input is set to the database bytes position.
    byte[] gotInBytes = new byte[wantInBytes.length];
    int l = in.read(gotInBytes);
    assertThat(l).isEqualTo(wantInBytes.length);
    assertThat(gotInBytes).isEqualTo(wantInBytes);
  }

  @Test
  public void testMdxSocket_clientWritesFirst_noMdxResponse() throws Exception {
    // 1. The client connects, writes to the server, sending an MDX request,
    //    reads from the server but receives no MDX response

    // Setup SSL server that expects an MDX request, but does not send an MDX response.
    SslServer server =
        new SslServer(
            (in, out) -> {
              // Server reads the client's MDX request.
              byte[] req = new byte[wantRequestBytes().length];
              new DataInputStream(in).readFully(req);
              assertThat(req).isEqualTo(wantRequestBytes());

              // Server writes a non-MDX response.
              out.write("hello".getBytes(StandardCharsets.UTF_8));
            });
    SslServer.SslServerParams p = server.start();

    // Setup client socket
    Socket socket = new ProtocolHandler("ua").connect(p.getSocket(), "tls");

    // Client writes, which should trigger the MDX exchange.
    socket.getOutputStream().write("from client".getBytes(StandardCharsets.UTF_8));

    // The server should not have sent an MDX response, so the client should
    // read the raw "hello" from the server.
    byte[] fromServer = new byte[5];
    new DataInputStream(socket.getInputStream()).readFully(fromServer);
    assertThat(fromServer).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

    socket.close();
    server.stop();
  }

  @Test
  public void testMdxSocket_clientWritesFirst_receivesMdxResponse() throws Exception {
    // 2. The client connects, writes to the server, sending an MDX request,
    //    reads from the server and receives an MDX response
    AtomicReference<byte[]> requestBytes = new AtomicReference<>();

    // Setup SSL server that expects an MDX request and sends an MDX response.
    SslServer server =
        new SslServer(
            (in, out) -> {
              // Server reads the client's MDX request.
              byte[] req = new byte[wantRequestBytes().length];
              new DataInputStream(in).readFully(req);
              requestBytes.set(req);
              // Server writes an MDX response.
              out.write(wantResponseBytes("hello".getBytes(StandardCharsets.UTF_8)));
            });

    SslServer.SslServerParams p = server.start();

    // Setup client socket
    Socket socket = new ProtocolHandler("ua").connect(p.getSocket(), "tls");

    // Client writes, which should trigger the MDX exchange.
    socket.getOutputStream().write("from client".getBytes(StandardCharsets.UTF_8));

    // The server should have sent an MDX response, so the client should
    // read the "hello" from the server.
    byte[] fromServer = new byte[5];
    new DataInputStream(socket.getInputStream()).readFully(fromServer);
    assertThat(fromServer).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

    assertThat(requestBytes.get()).isEqualTo(wantRequestBytes());

    socket.close();
    server.stop();
  }

  @Test
  public void testMdxSocket_clientReadsFirst_noMdxResponse() throws Exception {
    // 3. The client connects, reads from the server but receives no MDX response,
    //    then writes to the server, sending an MDX request
    AtomicReference<byte[]> requestBytes = new AtomicReference<>();

    // Setup SSL server that expects an MDX request and sends an MDX response.
    SslServer server =
        new SslServer(
            (in, out) -> {
              // Server writes a non-MDX response.
              out.write("hello".getBytes(StandardCharsets.UTF_8));

              // Server reads the client's MDX request.
              byte[] req = new byte[wantRequestBytes().length];
              new DataInputStream(in).readFully(req);
              requestBytes.set(req);
            });
    SslServer.SslServerParams p = server.start();

    // Setup client socket
    Socket socket = new ProtocolHandler("ua").connect(p.getSocket(), "tls");

    // The server should not have sent an MDX response, so the client should
    // read the raw "hello" from the server.
    byte[] fromServer = new byte[5];
    new DataInputStream(socket.getInputStream()).readFully(fromServer);
    assertThat(fromServer).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    assertThat(requestBytes.get()).isNull();

    // Client writes, which should trigger the MDX exchange.
    socket.getOutputStream().write("from client".getBytes(StandardCharsets.UTF_8));

    socket.close();
    server.stop();
  }

  @Test
  public void testMdxSocket_clientReadsFirst_receivesMdxResponse() throws Exception {
    // 4. The client connects, reads from the server and receives an MDX response,
    //    then writes to the server, sending an MDX request

    AtomicReference<byte[]> requestBytes = new AtomicReference<>();

    // Setup SSL server that expects an MDX request and sends an MDX response.
    SslServer server =
        new SslServer(
            (in, out) -> {
              // Server writes a non-MDX response.
              out.write("hello".getBytes(StandardCharsets.UTF_8));

              // Server reads the client's MDX request.
              byte[] req = new byte[wantRequestBytes().length];
              new DataInputStream(in).readFully(req);
              requestBytes.set(req);
            });
    SslServer.SslServerParams p = server.start();

    // Setup client socket
    Socket socket = new ProtocolHandler("ua").connect(p.getSocket(), "tls");

    // The server should have sent an MDX response, so the client should
    // read the "hello" from the server.
    byte[] fromServer = new byte[5];
    new DataInputStream(socket.getInputStream()).readFully(fromServer);
    assertThat(fromServer).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));

    // Client writes, which should trigger the MDX exchange.
    socket.getOutputStream().write("from client".getBytes(StandardCharsets.UTF_8));
    assertThat(requestBytes.get()).isNull();

    socket.close();
    server.stop();
  }

  private static byte[] wantRequestBytes() throws IOException {
    ByteArrayOutputStream wantOut = new ByteArrayOutputStream();
    MetadataExchange.MetadataExchangeRequest req =
        MetadataExchange.MetadataExchangeRequest.newBuilder()
            .setClientProtocolType(MetadataExchange.MetadataExchangeRequest.ClientProtocolType.TLS)
            .setUserAgent("ua")
            .build();
    int size = req.getSerializedSize();

    // Write the protocoal header
    wantOut.write("CSQLMDEX".getBytes(StandardCharsets.UTF_8));
    // Write the uint32 size
    wantOut.write((size >>> 24) & 0xFF);
    wantOut.write((size >>> 16) & 0xFF);
    wantOut.write((size >>> 8) & 0xFF);
    wantOut.write(size & 0xFF);
    // Write the protobuf
    req.writeTo(wantOut);
    wantOut.flush();
    return wantOut.toByteArray();
  }

  private static byte[] wantResponseBytes(byte[] data) throws IOException {
    ByteArrayOutputStream wantOut = new ByteArrayOutputStream();
    MetadataExchange.MetadataExchangeResponse res =
        MetadataExchange.MetadataExchangeResponse.newBuilder()
            .setResponseStatusCode(MetadataExchange.MetadataExchangeResponse.ResponseStatusCode.OK)
            .build();

    int size = res.getSerializedSize();

    // Write the protocoal header
    wantOut.write("CSQLMDEX".getBytes(StandardCharsets.UTF_8));
    // Write the uint32 size
    wantOut.write((size >>> 24) & 0xFF);
    wantOut.write((size >>> 16) & 0xFF);
    wantOut.write((size >>> 8) & 0xFF);
    wantOut.write(size & 0xFF);
    // Write the protobuf
    res.writeTo(wantOut);
    wantOut.write(data);
    wantOut.flush();
    return wantOut.toByteArray();
  }

  private interface ThrowingBiConsumer<T, U> {
    void accept(T t, U u) throws Exception;
  }

  private static class SslServer {
    private final ThrowingBiConsumer<InputStream, OutputStream> handler;
    private volatile ServerSocket serverSocket;
    private volatile boolean keepRunning = true;

    SslServer(ThrowingBiConsumer<InputStream, OutputStream> handler) {
      this.handler = handler;
    }

    private SslServerParams start() throws Exception {

      final KeyStore keyStore = createKeyStore();
      final KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, "password".toCharArray());
      final TrustManagerFactory tmf =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      tmf.init(keyStore);
      final SSLContext sc = SSLContext.getInstance("TLSv1.2");
      sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

      serverSocket =
          sc.getServerSocketFactory().createServerSocket(0, 5, InetAddress.getLoopbackAddress());
      Thread t =
          new Thread(
              () -> {
                try {
                  while (keepRunning) {
                    Socket s = serverSocket.accept();
                    if (s == null) {
                      return;
                    }
                    handler.accept(s.getInputStream(), s.getOutputStream());
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              });
      t.start();

      return new SslServerParams(sc, (SSLServerSocket) serverSocket);
    }

    void stop() throws IOException {
      this.keepRunning = false;
      if (serverSocket != null) {
        serverSocket.close();
      }
    }

    static class SslServerParams {
      private final SSLContext sslContext;
      private final SSLServerSocket serverSocket;

      SslServerParams(SSLContext sslContext, SSLServerSocket serverSocket) {
        this.sslContext = sslContext;
        this.serverSocket = serverSocket;
      }

      SSLSocket getSocket() throws IOException {
        SSLSocket socket =
            (SSLSocket)
                sslContext
                    .getSocketFactory()
                    .createSocket(serverSocket.getInetAddress(), serverSocket.getLocalPort());
        socket.startHandshake();
        return socket;
      }
    }
  }

  private static KeyStore createKeyStore() throws Exception {
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
    keyPairGenerator.initialize(2048);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    PrivateKey privateKey = keyPair.getPrivate();

    X500Name owner = new X500Name("CN=localhost");
    JcaX509v3CertificateBuilder builder =
        new JcaX509v3CertificateBuilder(
            owner,
            new BigInteger(64, new SecureRandom()),
            Date.from(Instant.now().minus(24, ChronoUnit.HOURS)),
            Date.from(Instant.now().plus(24, ChronoUnit.HOURS)),
            owner,
            keyPair.getPublic());

    X509CertificateHolder certHolder =
        builder.build(new JcaContentSignerBuilder("SHA256WithRSA").build(privateKey));
    X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certHolder);

    KeyStore keyStore = KeyStore.getInstance("JKS");
    keyStore.load(null, null);
    keyStore.setKeyEntry("alias", privateKey, "password".toCharArray(), new Certificate[] {cert});
    return keyStore;
  }
}
