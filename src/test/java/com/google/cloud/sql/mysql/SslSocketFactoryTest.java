/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.cloud.sql.mysql;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.SslCertsCreateEphemeralRequest;
import com.google.cloud.sql.mysql.SslSocketFactory.Clock;
import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import sun.security.x509.AlgorithmId;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateVersion;
import sun.security.x509.CertificateX509Key;
import sun.security.x509.X500Name;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.xml.bind.DatatypeConverter;

// TODO(berezv): add multithreaded test
@RunWith(JUnit4.class)
public class SslSocketFactoryTest {
  private static final String SERVER_MESSAGE = "HELLO";
  private static final String PROJECT_ID = "foo";
  private static final String INSTANCE_NAME = "bar";
  private static final String REGION = "baz";
  private static final String INSTANCE_CONNECTION_STRING = "foo:baz:bar";

  @Rule public MockitoRule mocks = MockitoJUnit.rule();

  @Mock private GoogleCredential credential;
  @Mock private SQLAdmin adminApi;
  @Mock private SQLAdmin.Instances adminApiInstances;
  @Mock private SQLAdmin.Instances.Get adminApiInstancesGet;
  @Mock private SQLAdmin.SslCerts adminApiSslCerts;
  @Mock private SQLAdmin.SslCerts.CreateEphemeral adminApiSslCertsCreateEphemeral;
  @Mock private Clock mockClock;

  private KeyPair clientKeyPair;

  @Before
  public void setup() throws IOException, GeneralSecurityException {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(DatatypeConverter.parseBase64Binary(TestKeys.CLIENT_PRIVATE_KEY));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    X509EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(DatatypeConverter.parseBase64Binary(TestKeys.CLIENT_PUBLIC_KEY));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    clientKeyPair = new KeyPair(publicKey, privateKey);

    when(adminApi.instances()).thenReturn(adminApiInstances);
    when(adminApiInstances.get(anyString(), anyString())).thenReturn(adminApiInstancesGet);

    when(adminApi.sslCerts()).thenReturn(adminApiSslCerts);
    when(adminApiSslCerts.createEphemeral(
        anyString(), anyString(), isA(SslCertsCreateEphemeralRequest.class)))
            .thenReturn(adminApiSslCertsCreateEphemeral);

    when(adminApiInstancesGet.execute())
        .thenReturn(
            new DatabaseInstance()
                .setBackendType("SECOND_GEN")
                .setIpAddresses(ImmutableList.of(new IpMapping().setIpAddress("127.0.0.1")))
                .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT))
                .setRegion(REGION));
    when(adminApiSslCertsCreateEphemeral.execute())
        .thenReturn(new SslCert().setCert(createEphemeralCert(Duration.standardSeconds(0))));
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create("foo");
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Invalid Cloud SQL instance");
    }

    try {
      sslSocketFactory.create("foo:bar");
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Invalid Cloud SQL instance");
    }
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    when(adminApiInstancesGet.execute())
        .thenReturn(
            new DatabaseInstance()
                .setBackendType("SECOND_GEN")
                .setIpAddresses(ImmutableList.of(new IpMapping().setIpAddress("127.0.0.1")))
                .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT))
                .setRegion("beer"));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create("foo:bar:baz");
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e.getMessage()).startsWith("Incorrect region value");
    }
  }

  @Test
  public void create_successfulConnection()
      throws IOException, GeneralSecurityException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, port);
    Socket socket = sslSocketFactory.create(INSTANCE_CONNECTION_STRING);

    verify(adminApiInstances).get(PROJECT_ID, INSTANCE_NAME);
    verify(adminApiSslCerts)
        .createEphemeral(
            eq(PROJECT_ID), eq(INSTANCE_NAME), isA(SslCertsCreateEphemeralRequest.class));

    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    String line = bufferedReader.readLine();
    assertThat(line).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  @Ignore
  // test disabled because the connection to the test server produces a different error than when
  // connecting to the real thing.
  // TODO(berezv): figure out why the test server produces a different error on an expired
  // certificate
  public void create_expiredCertificateOnFirstConnection_certificateRenewed()
      throws IOException, GeneralSecurityException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    // Certificate already expired.
    when(adminApiSslCertsCreateEphemeral.execute())
        .thenReturn(new SslCert().setCert(createEphemeralCert(Duration.standardMinutes(65))));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, port);
    Socket socket = sslSocketFactory.create("foo:baz:bar");

    verify(adminApiInstances, times(2)).get(PROJECT_ID, INSTANCE_NAME);
    verify(adminApiSslCerts, times(2))
        .createEphemeral(
            eq(PROJECT_ID), eq(INSTANCE_NAME), isA(SslCertsCreateEphemeralRequest.class));

    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    String line = bufferedReader.readLine();
    assertThat(line).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_certificateReusedIfNotExpired()
      throws IOException, GeneralSecurityException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, port);
    sslSocketFactory.create("foo:baz:bar");

    verify(adminApiInstances).get(PROJECT_ID, INSTANCE_NAME);
    verify(adminApiSslCerts)
        .createEphemeral(
            eq(PROJECT_ID), eq(INSTANCE_NAME), isA(SslCertsCreateEphemeralRequest.class));

    sslSocketFactory.create(INSTANCE_CONNECTION_STRING);

    verifyNoMoreInteractions(adminApiInstances);
    verifyNoMoreInteractions(adminApiSslCerts);
  }

  @Test
  public void create_certificateRenewedIfCloseToExpiration()
      throws IOException, GeneralSecurityException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    // Certificate only valid for 4 more minutes.
    when(adminApiSslCertsCreateEphemeral.execute())
        .thenReturn(new SslCert().setCert(createEphemeralCert(Duration.standardMinutes(56))));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, port);
    sslSocketFactory.create(INSTANCE_CONNECTION_STRING);

    // Second time, we should renew the certificate since it's about to expire.
    sslSocketFactory.create(INSTANCE_CONNECTION_STRING);

    verify(adminApiInstances, times(2)).get(PROJECT_ID, INSTANCE_NAME);
    verify(adminApiSslCerts, times(2))
        .createEphemeral(
            eq(PROJECT_ID), eq(INSTANCE_NAME), isA(SslCertsCreateEphemeralRequest.class));
  }

  @Test
  public void create_adminApiNotEnabled() throws IOException {
    ErrorInfo error = new ErrorInfo();
    error.setReason(SslSocketFactory.ADMIN_API_NOT_ENABLED_REASON);
    GoogleJsonError details = new GoogleJsonError();
    details.setErrors(ImmutableList.of(error));
    when(adminApiInstancesGet.execute())
        .thenThrow(
            new GoogleJsonResponseException(
                new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()),
                details));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      // TODO(berezv): should we throw something more specific than RuntimeException?
      assertThat(e.getMessage()).contains("Cloud SQL API is not enabled");
    }
  }

  @Test
  public void create_notAuthorizedToGetInstance() throws IOException {
    ErrorInfo error = new ErrorInfo();
    error.setReason(SslSocketFactory.INSTANCE_NOT_AUTHORIZED_REASON);
    GoogleJsonError details = new GoogleJsonError();
    details.setErrors(ImmutableList.of(error));
    when(adminApiInstancesGet.execute())
        .thenThrow(
            new GoogleJsonResponseException(
                new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()),
                details));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail("Expected RuntimeException");
    } catch (RuntimeException e) {
      // TODO(berezv): should we throw something more specific than RuntimeException?
      assertThat(e.getMessage()).contains("not authorized");
    }
  }

  @Test
  public void create_instanceNotFoundErrorCached() throws IOException {
    ErrorInfo error = new ErrorInfo();
    error.setReason(SslSocketFactory.INSTANCE_NOT_AUTHORIZED_REASON);
    GoogleJsonError details = new GoogleJsonError();
    details.setErrors(ImmutableList.of(error));
    when(adminApiInstancesGet.execute())
        .thenThrow(
            new GoogleJsonResponseException(
                new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()),
                details));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(mockClock, clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("not authorized");
    }

    verify(adminApiInstances).get(PROJECT_ID, INSTANCE_NAME);

    // Exception should be cached.
    when(mockClock.now()).thenReturn(59 * 1000L);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("not authorized");
    }
    // Verify no additional interactions with API.
    verify(adminApiInstances).get(PROJECT_ID, INSTANCE_NAME);

    // Enough time has passed that cached exception should be ignored.
    when(mockClock.now()).thenReturn(61 * 1000L);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail();
    } catch (RuntimeException e) {
      assertThat(e.getMessage()).contains("not authorized");
    }
    // Verify that the API was called one more time.
    verify(adminApiInstances, times(2)).get(PROJECT_ID, INSTANCE_NAME);
  }

  @Test
  public void create_notAuthorizedToCreateEphemeralCertificate() throws IOException {
    ErrorInfo error = new ErrorInfo();
    error.setReason(SslSocketFactory.INSTANCE_NOT_AUTHORIZED_REASON);
    GoogleJsonError details = new GoogleJsonError();
    details.setErrors(ImmutableList.of(error));
    when(adminApiSslCertsCreateEphemeral.execute())
        .thenThrow(
            new GoogleJsonResponseException(
                new HttpResponseException.Builder(403, "Forbidden", new HttpHeaders()),
                details));

    SslSocketFactory sslSocketFactory =
        new SslSocketFactory(new Clock(), clientKeyPair, credential, adminApi, 3307);
    try {
      sslSocketFactory.create(INSTANCE_CONNECTION_STRING);
      fail();
    } catch (RuntimeException e) {
      // TODO(berezv): should we throw something more specific than RuntimeException?
      assertThat(e.getMessage()).contains("Unable to obtain ephemeral certificate");
    }
  }

  private String createEphemeralCert(Duration shiftIntoPast)
      throws GeneralSecurityException, IOException {
    Duration validFor = Duration.standardHours(1);
    DateTime notBefore = DateTime.now().minus(shiftIntoPast);
    DateTime notAfter = notBefore.plus(validFor);

    CertificateValidity interval = new CertificateValidity(notBefore.toDate(), notAfter.toDate());

    X509CertInfo info = new X509CertInfo();
    info.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
    info.set(X509CertInfo.SERIAL_NUMBER, new CertificateSerialNumber(1));
    info.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get("SHA1withRSA")));
    info.set(
        X509CertInfo.SUBJECT, new X500Name("C = US, O = Google\\, Inc, CN=temporary-subject"));
    info.set(X509CertInfo.KEY, new CertificateX509Key(clientKeyPair.getPublic()));
    info.set(X509CertInfo.VALIDITY, interval);
    info.set(
        X509CertInfo.ISSUER,
        new X500Name("C = US, O = Google\\, Inc, CN=Google Cloud SQL Signing CA foo:baz"));

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec =
        new PKCS8EncodedKeySpec(DatatypeConverter.parseBase64Binary(
            TestKeys.SIGNING_CA_PRIVATE_KEY));
    PrivateKey signingKey = keyFactory.generatePrivate(keySpec);

    X509CertImpl cert = new X509CertImpl(info);
    cert.sign(signingKey, "SHA1withRSA");

    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN CERTIFICATE-----\n");
    sb.append(
        DatatypeConverter.printBase64Binary(cert.getEncoded())
            .replaceAll("(.{64})", "$1\n"));
    sb.append("\n");
    sb.append("-----END CERTIFICATE-----\n");

    return sb.toString();
  }

  private static class FakeSslServer {
    int start() throws InterruptedException {
      final CountDownLatch countDownLatch = new CountDownLatch(1);
      final AtomicInteger pickedPort = new AtomicInteger();

      new Thread() {
        @Override
        public void run() {
          try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

            KeyStore authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            authKeyStore.load(null, null);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PKCS8EncodedKeySpec keySpec =
                new PKCS8EncodedKeySpec(DatatypeConverter.parseBase64Binary(
                    TestKeys.SERVER_CERT_PRIVATE_KEY));
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            PrivateKeyEntry serverCert =
                new PrivateKeyEntry(
                    privateKey,
                    new Certificate[]{
                        certFactory.generateCertificate(
                            new ByteArrayInputStream(
                                TestKeys.SERVER_CERT.getBytes(StandardCharsets.UTF_8)))});
            authKeyStore.setEntry("serverCert", serverCert, new PasswordProtection(new char[0]));
            KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(authKeyStore, new char[0]);

            final X509Certificate signingCaCert =
                (X509Certificate)
                    certFactory.generateCertificate(
                        new ByteArrayInputStream(
                            TestKeys.SIGNING_CA_CERT.getBytes(StandardCharsets.UTF_8)));

            KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustKeyStore.load(null, null);
            trustKeyStore.setCertificateEntry("instance", signingCaCert);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
            tmf.init(trustKeyStore);

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(
                keyManagerFactory.getKeyManagers(),
                tmf.getTrustManagers(),
                new SecureRandom());
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            SSLServerSocket sslServerSocket=
                (SSLServerSocket) sslServerSocketFactory.createServerSocket(0);
            sslServerSocket.setNeedClientAuth(true);

            pickedPort.set(sslServerSocket.getLocalPort());
            countDownLatch.countDown();

            for (;;) {
              SSLSocket socket = (SSLSocket) sslServerSocket.accept();
              try {
                socket.startHandshake();
                socket.getOutputStream().write(SERVER_MESSAGE.getBytes(StandardCharsets.UTF_8));
                socket.close();
              } catch (Exception e) {
                e.printStackTrace();
              }
            }
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        }
      }.start();

      countDownLatch.await();

      return pickedPort.get();
    }
  }

}
