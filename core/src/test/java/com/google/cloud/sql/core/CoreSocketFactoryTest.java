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

package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential;
import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.testing.http.HttpTesting;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertRequest;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.InetAddress;
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
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

// TODO(berezv): add multithreaded test
@RunWith(JUnit4.class)
public class CoreSocketFactoryTest {

  private static final String SERVER_MESSAGE = "HELLO";

  private static final String PUBLIC_IP = "127.0.0.1";
  // If running tests on Mac, need to run "ifconfig lo0 alias 127.0.0.2 up" first
  private static final String PRIVATE_IP = "127.0.0.2";

  // TODO(kvg): Remove this when updating tests to use single CoreSocketFactory
  private ListeningScheduledExecutorService defaultExecutor;

  private final CredentialFactory credentialFactory = new StubCredentialFactory();
  @Mock
  private SQLAdmin adminApi;
  @Mock
  private SQLAdmin.Connect adminApiConnect;
  @Mock
  private SQLAdmin.Connect.Get adminApiConnectGet;

  @Mock
  private SQLAdmin.Connect.GenerateEphemeralCert adminApiConnectGenerateEphemeralCert;


  private GenerateEphemeralCertResponse generateEphemeralCertResponse = new GenerateEphemeralCertResponse();


  private ListenableFuture<KeyPair> clientKeyPair;

  // Creates a fake "accessNotConfigured" exception that can be used for testing.
  private static GoogleJsonResponseException fakeNotConfiguredException() throws IOException {
    return fakeGoogleJsonResponseException(
        HttpStatusCodes.STATUS_CODE_FORBIDDEN,
        "accessNotConfigured",
        "Cloud SQL Admin API has not been used in project 12345 before or it is disabled. Enable"
            + " it by visiting "
            + " https://console.developers.google.com/apis/api/sqladmin.googleapis.com/overview?project=12345"
            + " then retry. If you enabled this API recently, wait a few minutes for the action to"
            + " propagate to our systems and retry.");
  }

  // Creates a fake "notAuthorized" exception that can be used for testing.
  private static GoogleJsonResponseException fakeNotAuthorizedException() throws IOException {
    return fakeGoogleJsonResponseException(
        HttpStatusCodes.STATUS_CODE_FORBIDDEN,
        "notAuthorized",
        "The client is not authorized to make this request");
  }

  // Builds a fake GoogleJsonResponseException for testing API error handling.
  private static GoogleJsonResponseException fakeGoogleJsonResponseException(
      int httpStatus, String reason, String message) throws IOException {
    ErrorInfo errorInfo = new ErrorInfo();
    errorInfo.setReason(reason);
    errorInfo.setMessage(message);
    return fakeGoogleJsonResponseException(httpStatus, errorInfo, message);
  }

  private static GoogleJsonResponseException fakeGoogleJsonResponseException(
      int status, ErrorInfo errorInfo, String message) throws IOException {
    final JsonFactory jsonFactory = new JacksonFactory();
    HttpTransport transport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
            errorInfo.setFactory(jsonFactory);
            GoogleJsonError jsonError = new GoogleJsonError();
            jsonError.setCode(status);
            jsonError.setErrors(Collections.singletonList(errorInfo));
            jsonError.setMessage(message);
            jsonError.setFactory(jsonFactory);
            GenericJson errorResponse = new GenericJson();
            errorResponse.set("error", jsonError);
            errorResponse.setFactory(jsonFactory);
            return new MockLowLevelHttpRequest()
                .setResponse(
                    new MockLowLevelHttpResponse()
                        .setContent(errorResponse.toPrettyString())
                        .setContentType(Json.MEDIA_TYPE)
                        .setStatusCode(status));
          }
        };
    HttpRequest request =
        transport.createRequestFactory().buildGetRequest(HttpTesting.SIMPLE_GENERIC_URL);
    request.setThrowExceptionOnExecuteError(false);
    HttpResponse response = request.execute();
    return GoogleJsonResponseException.from(jsonFactory, response);
  }

  private static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
  }

  @Before
  public void setup()
      throws IOException, GeneralSecurityException, ExecutionException, OperatorCreationException {
    MockitoAnnotations.openMocks(this);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PRIVATE_KEY));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    X509EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PUBLIC_KEY));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    clientKeyPair = Futures.immediateFuture(new KeyPair(publicKey, privateKey));

    defaultExecutor = CoreSocketFactory.getDefaultExecutor();

    // Stub the API client for testing
    when(adminApi.connect()).thenReturn(adminApiConnect);

    // Stub when generic cases for project/instance
    when(adminApiConnect.get(anyString(), anyString())).thenThrow(fakeNotConfiguredException());
    // Stub when correct project, but generic instance
    when(adminApiConnect.get(eq("myProject"), anyString()))
        .thenThrow(fakeNotAuthorizedException());

    // Stub when correct instance
    when(adminApiConnect.get(eq("myProject"), eq("myInstance"))).thenReturn(adminApiConnectGet);
    when(adminApiConnect.get(eq("example.com:myProject"), eq("myInstance")))
        .thenReturn(adminApiConnectGet);

    when(adminApiConnect.get(eq("myProject"), eq("myInstance")))
        .thenReturn(adminApiConnectGet);
    when(adminApiConnect.get(eq("example.com:myProject"), eq("myInstance")))
        .thenReturn(adminApiConnectGet);

    when(adminApiConnect.generateEphemeralCert(
        anyString(), anyString(), isA(GenerateEphemeralCertRequest.class)))
        .thenReturn(adminApiConnectGenerateEphemeralCert);

    when(adminApiConnectGet.execute())
        .thenReturn(
            new ConnectSettings()
                .setBackendType("SECOND_GEN")
                .setIpAddresses(
                    ImmutableList.of(
                        new IpMapping().setIpAddress(PUBLIC_IP).setType("PRIMARY"),
                        new IpMapping().setIpAddress(PRIVATE_IP).setType("PRIVATE")))
                .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT))
                .setDatabaseVersion("POSTGRES14")
                .setRegion("myRegion"));

    when(adminApiConnectGenerateEphemeralCert.execute())
        .thenReturn(generateEphemeralCertResponse);
    generateEphemeralCertResponse.setEphemeralCert(
        new SslCert().setCert(createEphemeralCert(Duration.ofSeconds(0))));

  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, 3307, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket("myProject", Arrays.asList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException | InterruptedException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    }

    try {
      coreSocketFactory.createSslSocket("myProject:myRegion", Arrays.asList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e).hasMessageThat().contains("Cloud SQL connection name is invalid");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, 3307, defaultExecutor);
    try {
      coreSocketFactory.createSslSocket(
          "myProject:notMyRegion:myInstance", Arrays.asList("PRIMARY"));
      fail();
    } catch (IllegalArgumentException e) {
      assertThat(e)
          .hasMessageThat()
          .contains("The region specified for the Cloud SQL instance is incorrect");
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Start an SSL server on the private IP, and verifies that specifying a preference for private IP
   * results in a connection to the private IP.
   */
  @Test
  public void create_successfulPrivateConnection()
      throws IOException, GeneralSecurityException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start(PRIVATE_IP);

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Arrays.asList("PRIVATE"));

    verify(adminApiConnect).get("myProject", "myInstance");
    verify(adminApiConnect)
        .generateEphemeralCert(
            eq("myProject"), eq("myInstance"), isA(GenerateEphemeralCertRequest.class));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Arrays.asList("PRIMARY"));

    verify(adminApiConnect).get("myProject", "myInstance");
    verify(adminApiConnect)
        .generateEphemeralCert(
            eq("myProject"), eq("myInstance"), isA(GenerateEphemeralCertRequest.class));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulDomainScopedConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "example.com:myProject:myRegion:myInstance", Arrays.asList("PRIMARY"));

    verify(adminApiConnect).get("example.com:myProject", "myInstance");
    verify(adminApiConnect)
        .generateEphemeralCert(
            eq("example.com:myProject"), eq("myInstance"),
            isA(GenerateEphemeralCertRequest.class));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  @Ignore
  // test disabled because the connection to the test server produces a different error than when
  // connecting to the real thing.
  // TODO(berezv): figure out why the test server produces a different error on an expired
  // certificate
  public void create_expiredCertificateOnFirstConnection_certificateRenewed()
      throws IOException, GeneralSecurityException, ExecutionException, InterruptedException, OperatorCreationException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    // Certificate already expired.
    when(adminApiConnectGenerateEphemeralCert.execute())
        .thenReturn(generateEphemeralCertResponse);
    when(generateEphemeralCertResponse.getEphemeralCert())
        .thenReturn(new SslCert().setCert(createEphemeralCert(Duration.ofMinutes(65))));

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Arrays.asList("PRIMARY"));

    verify(adminApiConnect, times(2)).get("myProject", "myInstance");
    verify(adminApiConnect, times(2))
        .generateEphemeralCert(
            eq("myProject"), eq("myInstance"), isA(GenerateEphemeralCertRequest.class));

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_certificateReusedIfNotExpired() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, port, defaultExecutor);
    coreSocketFactory.createSslSocket("myProject:myRegion:myInstance", Arrays.asList("PRIMARY"));

    verify(adminApiConnect).get("myProject", "myInstance");
    verify(adminApiConnect)
        .generateEphemeralCert(
            eq("myProject"), eq("myInstance"), isA(GenerateEphemeralCertRequest.class));

    coreSocketFactory.createSslSocket("myProject:myRegion:myInstance", Arrays.asList("PRIMARY"));

    verifyNoMoreInteractions(adminApiConnect);
  }

  @Test
  public void create_adminApiNotEnabled() throws IOException {
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, 3307, defaultExecutor);
    try {
      // Use a different project to get Api Not Enabled Error.
      coreSocketFactory.createSslSocket(
          "NotMyProject:myRegion:myInstance", Arrays.asList("PRIMARY"));
      fail("Expected RuntimeException");
    } catch (RuntimeException | InterruptedException e) {
      // TODO(berezv): should we throw something more specific than RuntimeException?
      assertThat(e)
          .hasMessageThat()
          .contains(
              String.format(
                  "[%s] The Google Cloud SQL Admin API is not enabled for the project",
                  "NotMyProject:myRegion:myInstance"));
    }
  }

  @Test
  public void create_notAuthorized() throws IOException {
    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, 3307, defaultExecutor);
    try {
      // Use a different instance to simulate incorrect permissions.
      coreSocketFactory.createSslSocket(
          "myProject:myRegion:NotMyInstance", Arrays.asList("PRIMARY"));
      fail();
    } catch (RuntimeException e) {
      assertThat(e)
          .hasMessageThat()
          .contains(
              String.format(
                  "[%s] The Cloud SQL Instance does not exist or your account is not authorized",
                  "myProject:myRegion:NotMyInstance"));
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void supportsCustomCredentialFactoryWithIAM() throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo", 6000L);

    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, stubCredentialFactory, port,
            defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Arrays.asList("PRIMARY"), true);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithNoExpirationTime()
      throws InterruptedException, IOException {
    CredentialFactory stubCredentialFactory = new StubCredentialFactory("foo");


    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, stubCredentialFactory, port, defaultExecutor);
    Socket socket =
        coreSocketFactory.createSslSocket(
            "myProject:myRegion:myInstance", Arrays.asList("PRIMARY"), true);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void doesNotSupportNonGoogleCredentialWithIAM() throws InterruptedException {
    class BasicAuthStubCredentialFactory implements CredentialFactory {

      @Override
      public HttpRequestInitializer create() {
        return new BasicAuthentication("user", "password");
      }
    }

    CredentialFactory stubCredentialFactory = new BasicAuthStubCredentialFactory();


    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start();

    CoreSocketFactory coreSocketFactory =
        new CoreSocketFactory(clientKeyPair, adminApi, stubCredentialFactory, port, defaultExecutor);
    assertThrows(RuntimeException.class, () -> {
      coreSocketFactory.createSslSocket(
          "myProject:myRegion:myInstance", Arrays.asList("PRIMARY"), true);
    });
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader = new BufferedReader(
        new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }

  private String createEphemeralCert(Duration shiftIntoPast)
      throws GeneralSecurityException, ExecutionException, IOException, OperatorCreationException {
    Duration validFor = Duration.ofHours(1);
    ZonedDateTime notBefore = ZonedDateTime.now().minus(shiftIntoPast);
    ZonedDateTime notAfter = notBefore.plus(validFor);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec =
        new PKCS8EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.SIGNING_CA_PRIVATE_KEY));
    PrivateKey signingKey = keyFactory.generatePrivate(keySpec);

    final ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA")
        .build(signingKey);

    X500Principal issuer = new X500Principal(
        "C = US, O = Google\\, Inc, CN=Google Cloud SQL Signing CA foo:baz");
    X500Principal subject = new X500Principal("C = US, O = Google\\, Inc, CN=temporary-subject");

    JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
        issuer,
        BigInteger.ONE,
        Date.from(notBefore.toInstant()),
        Date.from(notAfter.toInstant()),
        subject,
        Futures.getDone(clientKeyPair).getPublic()
    );

    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

    Certificate cert = new JcaX509CertificateConverter()
        .getCertificate(certificateHolder);

    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN CERTIFICATE-----\n");
    sb.append(Base64.getEncoder().encodeToString(cert.getEncoded())
        .replaceAll("(.{64})", "$1\n"));
    sb.append("\n");
    sb.append("-----END CERTIFICATE-----\n");
    return sb.toString();
  }

  private static class FakeSslServer {

    int start() throws InterruptedException {
      return start(PUBLIC_IP);
    }

    int start(final String ip) throws InterruptedException {
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
                new PKCS8EncodedKeySpec(
                    decodeBase64StripWhitespace(TestKeys.SERVER_CERT_PRIVATE_KEY));
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
            PrivateKeyEntry serverCert =
                new PrivateKeyEntry(
                    privateKey,
                    new Certificate[]{
                        certFactory.generateCertificate(
                            new ByteArrayInputStream(
                                TestKeys.SERVER_CERT.getBytes(StandardCharsets.UTF_8)))
                    });
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
                keyManagerFactory.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            SSLServerSocket sslServerSocket =
                (SSLServerSocket)
                    sslServerSocketFactory.createServerSocket(0, 5, InetAddress.getByName(ip));
            sslServerSocket.setNeedClientAuth(true);

            pickedPort.set(sslServerSocket.getLocalPort());
            countDownLatch.countDown();

            for (; ; ) {
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
