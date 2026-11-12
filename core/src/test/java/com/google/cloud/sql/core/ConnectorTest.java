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

import static com.google.cloud.sql.core.InternalConnectorRegistry.DEFAULT_SERVER_PROXY_PORT;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.BasicAuthentication;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.DnsNameMapping;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.naming.NameNotFoundException;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConnectorTest extends CloudSqlCoreTestingBase {
  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();
  ListeningScheduledExecutorService defaultExecutor;
  private final long TEST_MAX_REFRESH_MS = 5000L;

  @Before
  public void setUp() throws Exception {
    super.setup();
    defaultExecutor = InternalConnectorRegistry.getDefaultExecutor();
  }

  @After
  public void tearDown() throws Exception {
    defaultExecutor.shutdownNow();
  }

  @Test
  public void create_throwsErrorForInvalidInstanceName() throws IOException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject")
            .withIpTypes("PRIMARY")
            .build();

    ConnectionConfig config2 =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion")
            .withIpTypes("PRIMARY")
            .build();

    Connector c =
        newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT, null, null, false);
    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex).hasMessageThat().contains("Cloud SQL connection name is invalid");

    ex =
        assertThrows(IllegalArgumentException.class, () -> c.connect(config2, TEST_MAX_REFRESH_MS));

    assertThat(ex).hasMessageThat().contains("Cloud SQL connection name is invalid");
  }

  @Test
  public void create_throwsErrorForInvalidTlsCommonNameMismatch()
      throws IOException, InterruptedException {
    // The server TLS certificate matches myProject:myRegion:myInstance
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:wrongwrongwrong")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port, null, null, false);
    SSLHandshakeException ex =
        assertThrows(
            SSLHandshakeException.class, () -> connector.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains(
            "Server certificate CN does not match instance name. "
                + "Server certificate CN=myProject:myInstance "
                + "Expected instance name: myProject:wrongwrongwrong");
  }

  /**
   * Start an SSL server on the private IP, and verifies that specifying a preference for private IP
   * results in a connection to the private IP.
   */
  @Test
  public void create_successfulPrivateConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIVATE")
            .build();

    int port = sslServer.start(PRIVATE_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port, null, null, false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulPublicConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port, null, null, false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void connect_SqlDataSuccess() throws IOException, InterruptedException {
    FakeSqlDataService sqlDataService = new FakeSqlDataService();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("SQL_DATA")
            .build();

    Connector connector =
        newConnectorWithSqlData(config.getConnectorConfig(), 0, null, null, false, sqlDataService);

    try (Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS)) {
      assertThat(socket).isNotNull();
      assertThat(socket.isClosed()).isFalse();

      // Verify StartSession was received
      StreamSqlDataRequest receivedRequest = sqlDataService.requests.poll(5, TimeUnit.SECONDS);
      assertThat(receivedRequest).isNotNull();
      assertThat(receivedRequest.hasStartSession()).isTrue();
    }
  }

  @Test
  public void connect_SqlDataFallback() throws IOException, InterruptedException {
    FakeSqlDataService sqlDataService = new FakeSqlDataService();
    sqlDataService.failHandshake = true; // Force fallback

    FakeSslServer sslServer = new FakeSslServer();
    int fallbackPort = sslServer.start(PRIVATE_IP);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("SQL_DATA")
            .build();

    Connector connector =
        newConnectorWithSqlData(
            config.getConnectorConfig(), fallbackPort, null, null, false, sqlDataService);

    try (Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS)) {
      // Should connect to the FakeSslServer (fallback)
      assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);

      // Verify that subsequent connection to the same instance skips SQL Data Service
      assertThat(sqlDataService.requests.size()).isEqualTo(1);

      // Clear requests to make sure we don't see new ones
      sqlDataService.requests.clear();

      // Connect again
      try (Socket socket2 = connector.connect(config, TEST_MAX_REFRESH_MS)) {
        assertThat(readLine(socket2)).isEqualTo(SERVER_MESSAGE);

        // Verify no new requests were sent to SQL Data Service
        assertThat(sqlDataService.requests).isEmpty();
      }
    }
  }

  @Test
  public void connect_SqlDataNoFallbackOnOtherErrors() throws IOException, InterruptedException {
    FakeSqlDataService sqlDataService =
        new FakeSqlDataService() {
          @Override
          public StreamObserver<StreamSqlDataRequest> streamSqlData(
              StreamObserver<StreamSqlDataResponse> responseObserver) {
            return new StreamObserver<StreamSqlDataRequest>() {
              @Override
              public void onNext(StreamSqlDataRequest request) {
                if (request.hasStartSession()) {
                  // Fail with INTERNAL instead of FAILED_PRECONDITION
                  responseObserver.onError(
                      Status.INTERNAL.withDescription("Internal error").asRuntimeException());
                }
              }

              @Override
              public void onError(Throwable t) {}

              @Override
              public void onCompleted() {}
            };
          }
        };

    FakeSslServer sslServer = new FakeSslServer();
    int fallbackPort = sslServer.start(PUBLIC_IP);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("SQL_DATA")
            .build();

    Connector connector =
        newConnectorWithSqlData(
            config.getConnectorConfig(), fallbackPort, null, null, false, sqlDataService);

    // Should NOT fall back, should throw IOException caused by StatusRuntimeException (INTERNAL) on
    // read
    try (Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS)) {
      IOException ex = assertThrows(IOException.class, () -> readLine(socket));
      assertThat(ex).hasMessageThat().contains("Failed to read from SQL Data Service");
      assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) ex.getCause()).getStatus().getCode())
          .isEqualTo(Status.Code.INTERNAL);
    }
  }

  @Test
  public void connect_SqlDataResourceExhausted_Cooldown() throws Exception {
    FakeSqlDataService sqlDataService =
        new FakeSqlDataService() {
          @Override
          public StreamObserver<StreamSqlDataRequest> streamSqlData(
              StreamObserver<StreamSqlDataResponse> responseObserver) {
            return new StreamObserver<StreamSqlDataRequest>() {
              @Override
              public void onNext(StreamSqlDataRequest request) {
                if (request.hasStartSession()) {
                  responseObserver.onError(
                      Status.RESOURCE_EXHAUSTED
                          .withDescription("resource busy")
                          .asRuntimeException());
                }
              }

              @Override
              public void onError(Throwable t) {}

              @Override
              public void onCompleted() {}
            };
          }
        };

    Duration cooldown = Duration.ofMillis(300);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("SQL_DATA")
            .withConnectorConfig(
                new ConnectorConfig.Builder().withResourceExhaustedCooldownPeriod(cooldown).build())
            .build();

    Connector connector =
        newConnectorWithSqlData(config.getConnectorConfig(), 0, null, null, false, sqlDataService);

    // First connect succeeds in creating socket, but first read fails with RESOURCE_EXHAUSTED
    try (Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS)) {
      IOException ex = assertThrows(IOException.class, () -> readLine(socket));
      assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
      assertThat(((StatusRuntimeException) ex.getCause()).getStatus().getCode())
          .isEqualTo(Status.Code.RESOURCE_EXHAUSTED);
    }

    // Second connect immediately should fail with ResourceExhaustedException (cooldown active)
    ResourceExhaustedException reEx =
        assertThrows(
            ResourceExhaustedException.class, () -> connector.connect(config, TEST_MAX_REFRESH_MS));
    assertThat(reEx).hasMessageThat().contains("cooldown active");

    // Wait for cooldown to expire (attempt 1 max backoff ~ 300 * 1.618 = 485ms)
    Thread.sleep(650);

    // Third connect after cooldown should attempt SQL Data Service again
    try (Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS)) {
      IOException ex = assertThrows(IOException.class, () -> readLine(socket));
      assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
    }
  }

  @Test
  public void create_successfulPrivateConnection_UsesInstanceName_DomainNameIgnored()
      throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIVATE")
            .build();

    int port = sslServer.start(PRIVATE_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port, null, null, false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulPrivateConnection_UsesInstanceName_EmptyDomainNameIgnored()
      throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("")
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIVATE")
            .build();

    int port = sslServer.start(PRIVATE_IP);

    Connector connector = newConnector(config.getConnectorConfig(), port, null, null, false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulPublicConnectionWithDomainNameLegacyDns()
      throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector connector =
        newConnectorLegacyDnsField(
            config.getConnectorConfig(),
            port,
            "db.example.com",
            "myProject:myRegion:myInstance",
            false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulPublicConnectionWithDomainName()
      throws IOException, InterruptedException {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector connector =
        newConnector(
            config.getConnectorConfig(),
            port,
            "db.example.com",
            "myProject:myRegion:myInstance",
            false);

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_successfulPrivateConnectionWhenDomainNameValueChanges()
      throws IOException, InterruptedException {

    MutableDnsResolver resolver =
        new MutableDnsResolver("db.example.com", "myProject:myRegion:myInstance");

    FakeSslServer myInstance =
        new FakeSslServer(
            TestKeys.getServerKeyPair().getPrivate(), TestKeys.getCasServerCertChain());

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withIpTypes("PRIMARY")
            .withIpTypes(Collections.singletonList(IpType.PRIVATE))
            .build();

    int port = myInstance.start(PRIVATE_IP);

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpPscCasTransport(Duration.ofSeconds(0)));

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            resolver,
            new ProtocolHandler("test"));

    // Open socket to initial instance
    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);

    // Change the mock DNS value, and restart the fake SSL server for the new instance
    resolver.setInstanceName("myProject:myRegion:myInstance2");
    myInstance.stop();
    myInstance.start(PRIVATE_IP_2, port, TestKeys.getCasServerCertChain2());

    // Attempt to connect to the new instance
    Socket socket2 = connector.connect(config, TEST_MAX_REFRESH_MS);
    assertThat(readLine(socket2)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_refreshConnectorWhenDomainNameValueChanges()
      throws IOException, InterruptedException {

    MutableDnsResolver resolver =
        new MutableDnsResolver("db.example.com", "myProject:myRegion:myInstance");

    FakeSslServer myInstance = new FakeSslServer();
    Duration testFailoverPeriod = Duration.ofSeconds(1);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withIpTypes("PRIMARY")
            .withIpTypes(Collections.singletonList(IpType.PRIVATE))
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(testFailoverPeriod).build())
            .build();

    int port = myInstance.start(PRIVATE_IP);

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            resolver,
            new ProtocolHandler("test"));

    // Open socket to initial instance
    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
    assertThat(socket.isClosed()).isFalse();

    // Change the mock DNS value, and restart the fake SSL server for the new instance
    resolver.setInstanceName("myProject:myRegion:myInstance2");
    myInstance.stop();
    myInstance.start(PRIVATE_IP_2, port, TestKeys.getCasServerCertChain2());

    // Wait for more than 1 failover period to ensure the connector refreshes.
    Thread.sleep(testFailoverPeriod.toMillis() * 2);

    // Check that the socket to the old instance was closed, indicating that the domain changed.
    assertThat(socket.isClosed()).isTrue();
  }

  @Test
  public void create_noChangeWhenDomainNameFailsToResolve()
      throws IOException, InterruptedException {

    MutableDnsResolver resolver =
        new MutableDnsResolver("db.example.com", "myProject:myRegion:myInstance");

    FakeSslServer myInstance = new FakeSslServer();
    Duration testFailoverPeriod = Duration.ofSeconds(1);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("db.example.com")
            .withIpTypes("PRIMARY")
            .withIpTypes(Collections.singletonList(IpType.PRIVATE))
            .withConnectorConfig(
                new ConnectorConfig.Builder().withFailoverPeriod(testFailoverPeriod).build())
            .build();

    int port = myInstance.start(PRIVATE_IP);

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            resolver,
            new ProtocolHandler("test"));

    // Open socket to initial instance
    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);
    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);

    // Change the mock DNS value, and restart the fake SSL server for the new instance
    resolver.clearInstanceName();

    // Wait for more than 1 failover period to ensure the connector refreshes.
    Thread.sleep(testFailoverPeriod.toMillis() * 2);

    // Check that the socket to the old instance is open even though the domain no longer resolves.
    assertThat(socket.isClosed()).isFalse();
  }

  @Test
  public void create_throwsErrorForUnresolvedDomainName() throws IOException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("baddomain.example.com")
            .withIpTypes("PRIMARY")
            .build();
    Connector c =
        newConnector(
            config.getConnectorConfig(),
            DEFAULT_SERVER_PROXY_PORT,
            "baddomain.example.com",
            "invalid-name",
            false);
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains("Cloud SQL connection name is invalid: \"baddomain.example.com\"");
  }

  @Test
  public void create_throwsErrorForDomainNameBadTargetValue() throws IOException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("badvalue.example.com")
            .withIpTypes("PRIMARY")
            .build();
    Connector c =
        newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT, null, null, false);
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains("Cloud SQL connection name is invalid: \"badvalue.example.com\"");
  }

  @Test
  public void create_throwsErrorForDomainNameDoesntMatchServerCert() throws Exception {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("not-in-san.example.com")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector c =
        newConnector(
            config.getConnectorConfig(),
            port,
            "db.example.com",
            "myProject:myRegion:otherInstance",
            true);

    SSLHandshakeException ex =
        assertThrows(SSLHandshakeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex).hasMessageThat().contains("Server certificate does not contain expected name");
  }

  @Test
  public void create_fallbackToInstanceWhenDomainNameDoesntMatchServerCert() throws Exception {
    FakeSslServer sslServer = new FakeSslServer();
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("not-in-san.example.com")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    Connector c =
        newConnector(
            config.getConnectorConfig(),
            port,
            "db.example.com",
            "myProject:myRegion:myInstance",
            true);

    c.connect(config, TEST_MAX_REFRESH_MS);
  }

  @Test
  public void create_successfulPublicCasConnection() throws IOException, InterruptedException {
    PrivateKey privateKey = TestKeys.getServerKeyPair().getPrivate();
    X509Certificate[] cert = TestKeys.getCasServerCertChain();

    FakeSslServer sslServer = new FakeSslServer(privateKey, cert);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpCasTransport(Duration.ZERO));

    DnsResolver dnsResolver = new MockDnsResolver("example.com", "myProject:myRegion:myInstance");

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void testConnect_dnsResolutionFailureFallbackToPrivateIp() throws Exception {
    if (isWindows()) {
      return;
    }

    PrivateKey privateKey = TestKeys.getServerKeyPair().getPrivate();
    X509Certificate[] cert = TestKeys.getCasServerCertChain();
    FakeSslServer sslServer = new FakeSslServer(privateKey, cert);
    int port = sslServer.start(PRIVATE_IP);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("example.com")
            .withIpTypes(Collections.singletonList(IpType.PRIVATE))
            .build();

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpCasTransport(Duration.ZERO));

    DnsResolver dnsResolver =
        new DnsResolver() {
          @Override
          public Collection<String> resolveTxt(String domainName) {
            return Collections.singletonList("myProject:myRegion:myInstance");
          }

          @Override
          public List<InetAddress> resolveHost(String hostName)
              throws java.net.UnknownHostException {
            throw new java.net.UnknownHostException("Mock DNS failure");
          }

          @Override
          public String resolveCname(String domainName) throws NameNotFoundException {
            throw new NameNotFoundException("Not found in mock");
          }
        };

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void testConnect_dnsResolutionEmptyFallbackToPrivateIp() throws Exception {
    if (isWindows()) {
      return;
    }

    PrivateKey privateKey = TestKeys.getServerKeyPair().getPrivate();
    X509Certificate[] cert = TestKeys.getCasServerCertChain();
    FakeSslServer sslServer = new FakeSslServer(privateKey, cert);
    int port = sslServer.start(PRIVATE_IP);

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withDomainName("example.com")
            .withIpTypes(Collections.singletonList(IpType.PRIVATE))
            .build();

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpCasTransport(Duration.ZERO));

    DnsResolver dnsResolver =
        new DnsResolver() {
          @Override
          public Collection<String> resolveTxt(String domainName) {
            return Collections.singletonList("myProject:myRegion:myInstance");
          }

          @Override
          public List<InetAddress> resolveHost(String hostName)
              throws java.net.UnknownHostException {
            return Collections.emptyList();
          }

          @Override
          public String resolveCname(String domainName) throws NameNotFoundException {
            throw new NameNotFoundException("Not found in mock");
          }
        };

    Connector connector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  private boolean isWindows() {
    String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
    return os.contains("win");
  }

  @Test
  public void create_successfulUnixSocketConnection() throws IOException, InterruptedException {
    if (isWindows()) {
      System.out.println("Skipping unix socket test on Windows.");
      return;
    }

    Path socketTestDir = Files.createTempDirectory("sockettest");
    Path socketPath = socketTestDir.resolve("test.sock");
    FakeUnixSocketServer unixSocketServer = new FakeUnixSocketServer(socketPath.toString());

    try {

      ConnectionConfig config =
          new ConnectionConfig.Builder()
              .withCloudSqlInstance("myProject:myRegion:myInstance")
              .withIpTypes("PRIMARY")
              .withUnixSocketPath(socketPath.toString())
              .build();

      unixSocketServer.start();

      Connector connector = newConnector(config.getConnectorConfig(), 10000, null, null, false);

      Socket socket = connector.connect(config, TEST_MAX_REFRESH_MS);

      assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
    } finally {
      unixSocketServer.close();
    }
  }

  @Test
  public void create_successfulDomainScopedConnection() throws IOException, InterruptedException {
    FakeSslServer sslServer =
        new FakeSslServer(
            TestKeys.getDomainServerKeyPair().getPrivate(), TestKeys.getDomainServerCert());
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpTransport(
                TestKeys.getDomainServerCertPem(),
                Duration.ofSeconds(60),
                null,
                false,
                false,
                null,
                null));

    int port = sslServer.start(PUBLIC_IP);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("example.com:myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();

    DnsResolver dnsResolver = new MockDnsResolver();

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void create_throwsErrorForInvalidInstanceRegion() throws IOException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:notMyRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();
    Connector c =
        newConnector(config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT, null, null, false);
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains("The region specified for the Cloud SQL instance is incorrect");
  }

  @Test
  public void create_failOnEmptyTargetPrincipal() throws IOException, InterruptedException {
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .withConnectorConfig(
                new ConnectorConfig.Builder()
                    .withDelegates(
                        Collections.singletonList("delegate-service-principal@example.com"))
                    .build())
            .build();
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                newConnector(
                    config.getConnectorConfig(), DEFAULT_SERVER_PROXY_PORT, null, null, false));

    assertThat(ex.getMessage()).contains(ConnectionConfig.CLOUD_SQL_TARGET_PRINCIPAL_PROPERTY);
  }

  @Test
  public void create_throwsException_adminApiNotEnabled() throws IOException {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeNotConfiguredException());
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("NotMyProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();

    DnsResolver dnsResolver = new MockDnsResolver();
    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            dnsResolver,
            new ProtocolHandler("test"));

    // Use a different project to get Api Not Enabled Error.
    TerminalException ex =
        assertThrows(TerminalException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains(
            String.format(
                "[%s] The Google Cloud SQL Admin API failed for the project",
                "NotMyProject:myRegion:myInstance"));
  }

  @Test
  public void create_throwsException_adminApiReturnsNotAuthorized() throws IOException {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeNotAuthorizedException());
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:NotMyInstance")
            .withIpTypes("PRIMARY")
            .build();

    DnsResolver dnsResolver = new MockDnsResolver();
    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            dnsResolver,
            new ProtocolHandler("test"));

    // Use a different instance to simulate incorrect permissions.
    TerminalException ex =
        assertThrows(TerminalException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    assertThat(ex)
        .hasMessageThat()
        .contains(
            String.format(
                "[%s] The Google Cloud SQL Admin API failed for the project",
                "myProject:myRegion:NotMyInstance"));
  }

  @Test
  public void create_throwsException_badGateway() throws IOException {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeBadGatewayException());
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:NotMyInstance")
            .withIpTypes("PRIMARY")
            .build();

    DnsResolver dnsResolver = new MockDnsResolver();

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            dnsResolver,
            new ProtocolHandler("test"));

    // If the gateway is down, then this is a temporary error, not a fatal error.
    RuntimeException ex =
        assertThrows(RuntimeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));

    // The Connector.connect() method will timeout, and will include details about the instance
    // data in the test.
    assertThat(ex).hasMessageThat().contains("Unable to get valid instance data within");

    assertThat(ex).hasMessageThat().contains("502");

    assertThat(ex).hasMessageThat().contains("myProject:myRegion:NotMyInstance");
  }

  @Test
  public void create_successfulPublicConnection_withIntermittentBadGatewayErrors()
      throws IOException, InterruptedException {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeIntermittentErrorHttpTransport(
                fakeSuccessHttpTransport(Duration.ofSeconds(0)), fakeBadGatewayException()));

    FakeSslServer sslServer = new FakeSslServer();

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .build();

    int port = sslServer.start(PUBLIC_IP);

    DnsResolver dnsResolver = new MockDnsResolver();

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithIAM() throws InterruptedException, IOException {
    FakeSslServer sslServer = new FakeSslServer();
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(
            new StubCredentialFactory("foo", Instant.now().plusSeconds(3600).toEpochMilli()));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpTransport(TestKeys.getServerCertPem(), Duration.ofSeconds(0)));

    int port = sslServer.start(PUBLIC_IP);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .withAuthType(AuthType.IAM)
            .build();

    DnsResolver dnsResolver = new MockDnsResolver();

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);

    assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
  }

  @Test
  public void supportsCustomCredentialFactoryWithNoExpirationTime()
      throws InterruptedException, IOException {
    FakeSslServer sslServer = new FakeSslServer();
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    int port = sslServer.start(PUBLIC_IP);
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .withAuthType(AuthType.IAM)
            .build();
    DnsResolver dnsResolver = new MockDnsResolver();
    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));

    Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);

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
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(stubCredentialFactory);
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withIpTypes("PRIMARY")
            .withAuthType(AuthType.IAM)
            .build();
    DnsResolver dnsResolver = new MockDnsResolver();
    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            dnsResolver,
            new ProtocolHandler("test"));

    assertThrows(RuntimeException.class, () -> c.connect(config, TEST_MAX_REFRESH_MS));
  }

  private Connector newConnectorLegacyDnsField(
      ConnectorConfig config, int port, String domainName, String instanceName, boolean cas) {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpTransport(
                TestKeys.getServerCertPem(),
                Duration.ofSeconds(0),
                null,
                cas,
                false,
                domainName,
                null));
    DnsResolver dnsResolver = new MockDnsResolver(domainName, instanceName);
    Connector connector =
        new Connector(
            config,
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));
    return connector;
  }

  private Connector newConnector(
      ConnectorConfig config, int port, String domainName, String instanceName, boolean cas) {
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpTransport(
                TestKeys.getServerCertPem(),
                Duration.ofSeconds(0),
                null,
                cas,
                false,
                null,
                Collections.singletonList(
                    new DnsNameMapping()
                        .setName(domainName)
                        .setConnectionType("PRIVATE_SERVICE_CONNECT")
                        .setDnsScope("INSTANCE"))));
    DnsResolver dnsResolver = new MockDnsResolver(domainName, instanceName);
    Connector connector =
        new Connector(
            config,
            factory,
            stubCredentialFactoryProvider.getInstanceCredentialFactory(config),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            port,
            dnsResolver,
            new ProtocolHandler("test"));
    return connector;
  }

  private String readLine(Socket socket) throws IOException {
    BufferedReader bufferedReader =
        new BufferedReader(new InputStreamReader(socket.getInputStream(), UTF_8));
    return bufferedReader.readLine();
  }

  private static class MockDnsResolver implements DnsResolver {
    private final String domainName;
    private final String instanceName;
    private final Map<String, InetAddress> hosts = new HashMap<>();

    private MockDnsResolver() {
      this.domainName = null;
      this.instanceName = null;
    }

    private MockDnsResolver(String domainName, String instanceName) {
      this.domainName = domainName;
      this.instanceName = instanceName;
    }

    @Override
    public Collection<String> resolveTxt(String domainName) throws NameNotFoundException {
      if (this.domainName != null && this.domainName.equals(domainName)) {
        return Collections.singletonList(this.instanceName);
      }
      if ("not-in-san.example.com".equals(domainName)) {
        return Collections.singletonList(this.instanceName);
      }
      if ("badvalue.example.com".equals(domainName)) {
        return Collections.singletonList("not-an-instance-name");
      }
      throw new NameNotFoundException("Not found: " + domainName);
    }

    @Override
    public List<InetAddress> resolveHost(String hostName) {
      if (hosts.containsKey(hostName)) {
        return Collections.singletonList(hosts.get(hostName));
      }
      return Collections.emptyList();
    }

    @Override
    public String resolveCname(String domainName) throws NameNotFoundException {
      throw new NameNotFoundException("Not found in mock: " + domainName);
    }
  }

  private static class MutableDnsResolver implements DnsResolver {
    private final String domainName;
    private String instanceName;

    private MutableDnsResolver(String domainName, String instanceName) {
      this.domainName = domainName;
      this.instanceName = instanceName;
    }

    private synchronized void setInstanceName(String instanceName) {
      this.instanceName = instanceName;
    }

    private synchronized void clearInstanceName() {
      this.instanceName = null;
    }

    @Override
    public synchronized Collection<String> resolveTxt(String domainName)
        throws NameNotFoundException {
      if (this.domainName != null
          && this.domainName.equals(domainName)
          && this.instanceName != null) {
        return Collections.singletonList(this.instanceName);
      }
      throw new NameNotFoundException("Not found: " + domainName);
    }

    @Override
    public List<InetAddress> resolveHost(String hostName) {
      return Collections.emptyList();
    }

    @Override
    public synchronized String resolveCname(String domainName) throws NameNotFoundException {
      throw new NameNotFoundException("Not found in mock: " + domainName);
    }
  }

  private Connector newConnectorWithSqlData(
      ConnectorConfig config,
      int port,
      String domainName,
      String instanceName,
      boolean cas,
      FakeSqlDataService sqlDataService)
      throws IOException {

    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(sqlDataService)
            .build()
            .start());

    io.grpc.ManagedChannel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    CredentialFactory credentialFactory =
        stubCredentialFactoryProvider.getInstanceCredentialFactory(config);
    SqlDataClient sqlDataClient =
        new SqlDataClient(config, credentialFactory, "test-agent", channel);

    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(
            fakeSuccessHttpTransport(
                TestKeys.getServerCertPem(),
                Duration.ofSeconds(0),
                null,
                cas,
                false,
                null,
                Collections.singletonList(
                    new DnsNameMapping()
                        .setName(domainName)
                        .setConnectionType("PRIVATE_SERVICE_CONNECT")
                        .setDnsScope("INSTANCE"))));
    DnsResolver dnsResolver = new MockDnsResolver(domainName, instanceName);

    return new Connector(
        config,
        factory,
        credentialFactory,
        defaultExecutor,
        clientKeyPair,
        10,
        TEST_MAX_REFRESH_MS,
        port,
        null,
        dnsResolver,
        new ProtocolHandler("test"),
        sqlDataClient);
  }

  @Test
  public void testIamPostgresWrapsPostgresSocketAndProbesViaDnsResolver() throws Exception {
    KeyStore authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    authKeyStore.load(null, null);
    authKeyStore.setEntry(
        "serverCert",
        new KeyStore.PrivateKeyEntry(
            TestKeys.getServerKeyPair().getPrivate(),
            new X509Certificate[] {TestKeys.getServerCert()}),
        new KeyStore.PasswordProtection(new char[0]));
    KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(authKeyStore, new char[0]);

    KeyStore trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustKeyStore.load(null, null);
    trustKeyStore.setCertificateEntry("instance", TestKeys.getSigningCaCert());
    TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
    tmf.init(trustKeyStore);

    SSLContext serverSslContext = SSLContext.getInstance("TLSv1.2");
    serverSslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
    SSLServerSocket serverSocket =
        (SSLServerSocket)
            serverSslContext
                .getServerSocketFactory()
                .createServerSocket(0, 5, InetAddress.getByName(PUBLIC_IP));
    serverSocket.setNeedClientAuth(true);

    final byte[] startup =
        PostgresSocket.buildPostgresStartupPacket("iam-user@example.com", "mydb");
    final byte[] expectedTerminate = PostgresSocket.buildTerminatePacket();
    AtomicInteger connectionIndex = new AtomicInteger();
    CountDownLatch clientAckReadLatch = new CountDownLatch(1);
    CountDownLatch probeReceivedLatch = new CountDownLatch(1);
    List<byte[]> capturedProbeBytes = new CopyOnWriteArrayList<>();

    Thread serverThread =
        new Thread(
            () -> {
              while (!serverSocket.isClosed()) {
                try {
                  SSLSocket accepted = (SSLSocket) serverSocket.accept();
                  int idx = connectionIndex.getAndIncrement();
                  accepted.startHandshake();
                  if (idx == 0) {
                    // Connection 0: client application socket; consume full split StartupMessage
                    // before responding and keep socket open until client reads ack (Windows-safe).
                    InputStream in = accepted.getInputStream();
                    byte[] buf = new byte[512];
                    int totalRead = 0;
                    while (totalRead < startup.length) {
                      int r = in.read(buf, totalRead, buf.length - totalRead);
                      if (r <= 0) {
                        break;
                      }
                      totalRead += r;
                    }
                    if (totalRead > 0) {
                      accepted.getOutputStream().write(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0});
                      accepted.getOutputStream().flush();
                    }
                    try {
                      clientAckReadLatch.await(TEST_MAX_REFRESH_MS, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    accepted.close();
                  } else {
                    // Connection 1+: proactive IAM startup probe after refresh
                    InputStream in = accepted.getInputStream();
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[512];
                    while (baos.size() < startup.length) {
                      int n = in.read(buf);
                      if (n <= 0) {
                        break;
                      }
                      baos.write(buf, 0, n);
                    }
                    if (baos.size() >= startup.length) {
                      accepted.getOutputStream().write(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0});
                      accepted.getOutputStream().flush();
                      // Read Terminate packet ('X', 0, 0, 0, 4) completely before closing.
                      while (baos.size() < startup.length + expectedTerminate.length) {
                        int n = in.read(buf);
                        if (n <= 0) {
                          break;
                        }
                        baos.write(buf, 0, n);
                      }
                    }
                    capturedProbeBytes.add(baos.toByteArray());
                    probeReceivedLatch.countDown();
                    accepted.close();
                  }
                } catch (SocketException e) {
                  break;
                } catch (IOException e) {
                  // ignore test teardown
                }
              }
            });
    serverThread.setDaemon(true);
    serverThread.start();

    try {
      CredentialFactoryProvider credentialFactoryProvider =
          new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
      ConnectionInfoRepositoryFactory factory =
          new StubConnectionInfoRepositoryFactory(fakeHttpTransportForDbVersion("POSTGRES_15"));

      ConnectionConfig config =
          new ConnectionConfig.Builder()
              .withCloudSqlInstance("myProject:myRegion:myInstance")
              .withDomainName("custom.iam.example.com")
              .withIpTypes("PRIMARY")
              .withAuthType(AuthType.IAM)
              .build();
      DnsResolver dnsResolver =
          new MockDnsResolver() {
            @Override
            public List<InetAddress> resolveHost(String hostName) {
              if ("custom.iam.example.com".equals(hostName)) {
                return Collections.singletonList(InetAddress.getLoopbackAddress());
              }
              return Collections.emptyList();
            }
          };

      Connector c =
          new Connector(
              config.getConnectorConfig(),
              factory,
              credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
              defaultExecutor,
              clientKeyPair,
              0,
              TEST_MAX_REFRESH_MS,
              serverSocket.getLocalPort(),
              dnsResolver,
              new ProtocolHandler("test"));

      Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);
      assertThat(socket).isInstanceOf(PostgresSocket.class);

      // Simulate Postgres driver sending split StartupMessage
      socket.getOutputStream().write(startup, 0, 1);
      socket.getOutputStream().write(startup, 1, startup.length - 1);
      socket.getOutputStream().flush();

      // Wait until the server ack is readable so conn #0 completes before refresh
      byte[] ack = new byte[16];
      assertThat(socket.getInputStream().read(ack)).isGreaterThan(0);
      clientAckReadLatch.countDown();

      // Force refresh -> dials via ProbeDialer + MockDnsResolver and sends StartupMessage +
      // Terminate
      c.getConnection(config).forceRefresh();
      assertThat(probeReceivedLatch.await(TEST_MAX_REFRESH_MS, TimeUnit.MILLISECONDS)).isTrue();
      assertThat(capturedProbeBytes).hasSize(1);
      byte[] expectedProbe = capturedProbeBytes.get(0);
      assertThat(expectedProbe.length).isEqualTo(startup.length + expectedTerminate.length);
      assertThat(java.util.Arrays.copyOfRange(expectedProbe, 0, startup.length)).isEqualTo(startup);
      assertThat(java.util.Arrays.copyOfRange(expectedProbe, startup.length, expectedProbe.length))
          .isEqualTo(expectedTerminate);

      c.close();
    } finally {
      clientAckReadLatch.countDown();
      serverSocket.close();
    }
  }

  @Test
  public void testIamMysqlDoesNotWrapPostgresSocket() throws Exception {
    FakeSslServer sslServer = new FakeSslServer();
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeHttpTransportForDbVersion("MYSQL_8_0"));

    int port = sslServer.start(PUBLIC_IP);
    try {
      ConnectionConfig config =
          new ConnectionConfig.Builder()
              .withCloudSqlInstance("myProject:myRegion:myInstance")
              .withIpTypes("PRIMARY")
              .withAuthType(AuthType.IAM)
              .build();
      Connector c =
          new Connector(
              config.getConnectorConfig(),
              factory,
              credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
              defaultExecutor,
              clientKeyPair,
              10,
              TEST_MAX_REFRESH_MS,
              port,
              new MockDnsResolver(),
              new ProtocolHandler("test"));

      Socket socket = c.connect(config, TEST_MAX_REFRESH_MS);
      assertThat(socket).isNotInstanceOf(PostgresSocket.class);
      assertThat(readLine(socket)).isEqualTo(SERVER_MESSAGE);
      c.close();
    } finally {
      sslServer.stop();
    }
  }

  @Test
  public void testDialDirectSocket_emptyPreferredIpsAndDnsFailureThrowsIoException() {
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeHttpTransportForDbVersion("POSTGRES_15"));
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withDomainName("missing.example.com")
            .build();
    ConnectionMetadata emptyIpsMetadata =
        new ConnectionMetadata(
            Collections.emptyList(),
            Collections.emptyMap(),
            null,
            null,
            null,
            Collections.emptyList());

    // 1. Custom DNS returns empty list -> throws IOException rather than IndexOutOfBoundsException
    DnsResolver emptyResolver =
        new MockDnsResolver() {
          @Override
          public List<InetAddress> resolveHost(String hostName) {
            return Collections.emptyList();
          }
        };
    Connector emptyDnsConnector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            emptyResolver,
            new ProtocolHandler("test"));
    try {
      IOException ex =
          assertThrows(
              IOException.class,
              () -> emptyDnsConnector.dialDirectSocket(config, emptyIpsMetadata, 1000));
      assertThat(ex).hasMessageThat().contains("No target IP addresses available");
    } finally {
      emptyDnsConnector.close();
    }

    // 2. Custom DNS throws UnknownHostException -> throws IOException rather than IOOBE
    DnsResolver failingResolver =
        new DnsResolver() {
          @Override
          public Collection<String> resolveTxt(String domainName) throws NameNotFoundException {
            throw new NameNotFoundException(domainName);
          }

          @Override
          public List<InetAddress> resolveHost(String hostName) throws UnknownHostException {
            throw new UnknownHostException("nxdomain");
          }

          @Override
          public String resolveCname(String domainName) throws NameNotFoundException {
            throw new NameNotFoundException(domainName);
          }
        };
    Connector failingDnsConnector =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            failingResolver,
            new ProtocolHandler("test"));
    try {
      IOException ex =
          assertThrows(
              IOException.class,
              () -> failingDnsConnector.dialDirectSocket(config, emptyIpsMetadata, 1000));
      assertThat(ex).hasMessageThat().contains("No target IP addresses available");
    } finally {
      failingDnsConnector.close();
    }
  }

  @Test
  public void testDialDirectSocket_sharedTimeoutBudgetAcrossMultipleTargets() {
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeHttpTransportForDbVersion("POSTGRES_15"));
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .build();

    List<Integer> observedConnectTimeouts = new ArrayList<>();
    SSLContext fakeSslContext =
        newSslContextForFactory(
            new StubSocketFactory() {
              @Override
              public Socket createSocket() {
                return new PostgresSocketTest.StubSslSocket() {
                  @Override
                  public void connect(SocketAddress endpoint, int timeout) throws IOException {
                    observedConnectTimeouts.add(timeout);
                    try {
                      Thread.sleep(60);
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                    }
                    throw new IOException("simulated dial failure");
                  }
                };
              }
            });

    ConnectionMetadata multiTargetMetadata =
        new ConnectionMetadata(
            Arrays.asList("10.0.0.1", "10.0.0.2", "10.0.0.3"),
            Collections.emptyMap(),
            null,
            null,
            fakeSslContext,
            Collections.emptyList());

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            new MockDnsResolver(),
            new ProtocolHandler("test"));
    try {
      assertThrows(IOException.class, () -> c.dialDirectSocket(config, multiTargetMetadata, 90));
      // Budget is 90ms and each connect attempt sleeps 60ms, so the 3rd target is aborted before
      // dialing and remaining attempt timeouts strictly decrease.
      assertThat(observedConnectTimeouts.size()).isAtMost(2);
      assertThat(observedConnectTimeouts).isNotEmpty();
      assertThat(observedConnectTimeouts.get(0)).isAtMost(90);
      if (observedConnectTimeouts.size() == 2) {
        assertThat(observedConnectTimeouts.get(1)).isLessThan(observedConnectTimeouts.get(0));
      }
    } finally {
      c.close();
    }
  }

  @Test
  public void testDialDirectSocket_mdxFailureClosesSocket() {
    CredentialFactoryProvider credentialFactoryProvider =
        new CredentialFactoryProvider(new StubCredentialFactory("foo", null));
    ConnectionInfoRepositoryFactory factory =
        new StubConnectionInfoRepositoryFactory(fakeHttpTransportForDbVersion("POSTGRES_15"));
    ConnectionConfig config =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("myProject:myRegion:myInstance")
            .withMdxClientProtocolType("tcp")
            .build();

    List<SSLSocket> createdSockets = new ArrayList<>();
    AtomicBoolean closed = new AtomicBoolean(false);
    SSLContext fakeSslContext =
        newSslContextForFactory(
            new StubSocketFactory() {
              @Override
              public Socket createSocket() {
                PostgresSocketTest.StubSslSocket sock =
                    new PostgresSocketTest.StubSslSocket() {
                      @Override
                      public synchronized void close() throws IOException {
                        closed.set(true);
                        super.close();
                      }
                    };
                createdSockets.add(sock);
                return sock;
              }
            });

    ConnectionMetadata mdxMetadata =
        new ConnectionMetadata(
            Collections.singletonList("127.0.0.1"),
            Collections.emptyMap(),
            null,
            null,
            fakeSslContext,
            Collections.singletonList("CLIENT_PROTOCOL_TYPE"));

    ProtocolHandler failingMdxHandler =
        new ProtocolHandler("test") {
          @Override
          MdxSocket connect(SSLSocket socket, String clientProtocolType) throws IOException {
            throw new IOException("simulated mdx failure");
          }
        };

    Connector c =
        new Connector(
            config.getConnectorConfig(),
            factory,
            credentialFactoryProvider.getInstanceCredentialFactory(config.getConnectorConfig()),
            defaultExecutor,
            clientKeyPair,
            10,
            TEST_MAX_REFRESH_MS,
            DEFAULT_SERVER_PROXY_PORT,
            new MockDnsResolver(),
            failingMdxHandler);
    try {
      IOException ex =
          assertThrows(IOException.class, () -> c.dialDirectSocket(config, mdxMetadata, 5000));
      assertThat(ex).hasMessageThat().contains("simulated mdx failure");
      assertThat(createdSockets).hasSize(1);
      assertThat(closed.get()).isTrue();
      assertThat(createdSockets.get(0).isClosed()).isTrue();
    } finally {
      c.close();
    }
  }

  private static SSLContext newSslContextForFactory(SSLSocketFactory factory) {
    return new SSLContext(
        new SSLContextSpi() {
          @Override
          protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) {}

          @Override
          protected SSLSocketFactory engineGetSocketFactory() {
            return factory;
          }

          @Override
          protected javax.net.ssl.SSLServerSocketFactory engineGetServerSocketFactory() {
            return null;
          }

          @Override
          protected javax.net.ssl.SSLEngine engineCreateSSLEngine() {
            return null;
          }

          @Override
          protected javax.net.ssl.SSLEngine engineCreateSSLEngine(String host, int port) {
            return null;
          }

          @Override
          protected javax.net.ssl.SSLSessionContext engineGetServerSessionContext() {
            return null;
          }

          @Override
          protected javax.net.ssl.SSLSessionContext engineGetClientSessionContext() {
            return null;
          }
        },
        null,
        "TLS") {};
  }

  private abstract static class StubSocketFactory extends SSLSocketFactory {
    @Override
    public String[] getDefaultCipherSuites() {
      return new String[0];
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[0];
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) {
      return null;
    }

    @Override
    public Socket createSocket(String host, int port) {
      return null;
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) {
      return null;
    }

    @Override
    public Socket createSocket(InetAddress host, int port) {
      return null;
    }

    @Override
    public Socket createSocket(
        InetAddress address, int port, InetAddress localAddress, int localPort) {
      return null;
    }
  }

  private static HttpTransport fakeHttpTransportForDbVersion(String databaseVersion) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            if (method.equals("GET") && url.contains("connectSettings")) {
              ConnectSettings settings =
                  new ConnectSettings()
                      .setBackendType("SECOND_GEN")
                      .setIpAddresses(
                          ImmutableList.of(
                              new IpMapping().setIpAddress(PUBLIC_IP).setType("PRIMARY"),
                              new IpMapping().setIpAddress(PRIVATE_IP).setType("PRIVATE")))
                      .setServerCaCert(new SslCert().setCert(TestKeys.getServerCertPem()))
                      .setDatabaseVersion(databaseVersion)
                      .setRegion("myRegion")
                      .setServerCaMode("GOOGLE_MANAGED_INTERNAL_CA");
              settings.setFactory(jsonFactory);
              response
                  .setContent(settings.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            } else if (method.equals("POST") && url.contains("generateEphemeralCert")) {
              GenerateEphemeralCertResponse certResponse = new GenerateEphemeralCertResponse();
              certResponse.setEphemeralCert(
                  new SslCert()
                      .setCert(
                          TestKeys.createEphemeralCert("myProject:myInstance", Duration.ZERO)));
              certResponse.setFactory(jsonFactory);
              response
                  .setContent(certResponse.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            }
            return response;
          }
        };
      }
    };
  }
}
