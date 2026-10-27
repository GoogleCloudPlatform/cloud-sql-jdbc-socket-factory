/*
 * Copyright 2023 Google LLC
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

public class FakeSslServer {

  private final PrivateKey privateKey;
  private final X509Certificate[] cert;
  private SSLServerSocket sslServerSocket;

  // The TLS protocol and JSSE provider of the server's SSLContext. A null provider means the JRE
  // default.
  private String protocol = "TLSv1.2";
  private Provider provider;
  // Restricts the key exchange groups the server accepts. Only supported with a Conscrypt provider.
  private String[] namedGroups;
  // When true, the server only enables `protocol`. The JRE enables every supported protocol on a
  // server socket regardless of the SSLContext protocol, e.g. TLSv1.3 for a "TLSv1.2" context.
  private boolean restrictProtocol;

  FakeSslServer() {
    privateKey = TestKeys.getServerKeyPair().getPrivate();
    cert = new X509Certificate[] {TestKeys.getServerCert()};
  }

  /**
   * Creates a server using the given protocol and provider. When namedGroups is non-null, the
   * server only accepts those key exchange groups, which requires a Conscrypt provider.
   */
  FakeSslServer(String protocol, Provider provider, String[] namedGroups) {
    this();
    this.protocol = protocol;
    this.provider = provider;
    this.namedGroups = namedGroups;
    this.restrictProtocol = true;
  }

  public FakeSslServer(PrivateKey privateKey, X509Certificate[] cert) {
    this.privateKey = privateKey;
    this.cert = cert;
  }

  public FakeSslServer(PrivateKey privateKey, X509Certificate cert) {
    this.privateKey = privateKey;
    this.cert = new X509Certificate[] {cert};
  }

  void stop() throws IOException {
    sslServerSocket.close();
  }

  int start(final String ip) throws InterruptedException {
    return this.start(ip, 0, this.cert); // when port == 0, socket will open on a random port
  }

  int start(final String ip, int port, X509Certificate[] certChain) throws InterruptedException {
    final CountDownLatch countDownLatch = new CountDownLatch(1);
    final AtomicInteger pickedPort = new AtomicInteger();

    new Thread(
            () -> {
              try {
                KeyStore authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                authKeyStore.load(null, null);
                PrivateKeyEntry serverCert = new PrivateKeyEntry(privateKey, certChain);
                authKeyStore.setEntry(
                    "serverCert", serverCert, new PasswordProtection(new char[0]));
                KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(authKeyStore, new char[0]);

                final X509Certificate signingCaCert = TestKeys.getSigningCaCert();

                KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                trustKeyStore.load(null, null);
                trustKeyStore.setCertificateEntry("instance", signingCaCert);
                TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
                tmf.init(trustKeyStore);

                SSLContext sslContext =
                    provider == null
                        ? SSLContext.getInstance(protocol)
                        : SSLContext.getInstance(protocol, provider);
                sslContext.init(
                    keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
                SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
                this.sslServerSocket =
                    (SSLServerSocket)
                        sslServerSocketFactory.createServerSocket(
                            port, 5, InetAddress.getByName(ip));
                sslServerSocket.setNeedClientAuth(true);
                if (restrictProtocol) {
                  sslServerSocket.setEnabledProtocols(new String[] {protocol});
                }

                pickedPort.set(sslServerSocket.getLocalPort());
                countDownLatch.countDown();

                while (!sslServerSocket.isClosed()) {
                  SSLSocket socket;
                  try {
                    socket = (SSLSocket) sslServerSocket.accept();
                  } catch (SocketException e) {
                    break; // the server socket was closed, exit accept loop.
                  }
                  try {
                    if (namedGroups != null) {
                      org.conscrypt.Conscrypt.setNamedGroups(socket, namedGroups);
                    }
                    socket.startHandshake();
                    socket
                        .getOutputStream()
                        .write(CloudSqlCoreTestingBase.SERVER_MESSAGE.getBytes(UTF_8));
                  } catch (IOException e) {
                    // A failed handshake (e.g. a client that rejects the server certificate)
                    // must not stop the server from accepting further connections.
                  } finally {
                    socket.close();
                  }
                }
              } catch (Exception e) {
                countDownLatch.countDown();
                throw new RuntimeException(e);
              }
            })
        .start();

    countDownLatch.await();

    return pickedPort.get();
  }
}
