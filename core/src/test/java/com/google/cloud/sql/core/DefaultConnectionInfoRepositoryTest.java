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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.services.sqladmin.model.DnsNameMapping;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.IpType;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.net.ssl.SSLContext;
import org.bouncycastle.operator.OperatorCreationException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultConnectionInfoRepositoryTest {

  public static final String SAMPLE_PUBLIC_IP = "34.1.2.3";
  public static final String SAMPLE_PRIVATE_IP = "10.0.0.1";
  public static final String SAMPLE_PCS_DNS_NAME = "abcde.12345.us-central1.sql.goog";
  public static final String INSTANCE_CONNECTION_NAME = "p:r:i";
  public static final String DATABASE_VERSION = "POSTGRES14";
  public static final String DEFAULT_BASE_URL = "https://sqladmin.googleapis.com/";

  @Test
  public void testFetchInstanceData_returnsIpAddresses()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {
    MockAdminApi mockAdminApi =
        buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION, DEFAULT_BASE_URL, false);
    ConnectorConfig config = new ConnectorConfig.Builder().build();
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ConnectionInfo connectionInfo =
        repo.getConnectionInfo(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);

    Map<IpType, List<String>> ipAddrs = connectionInfo.getIpAddrs();
    assertThat(ipAddrs.get(IpType.PUBLIC)).containsExactly(SAMPLE_PUBLIC_IP);
    assertThat(ipAddrs.get(IpType.PRIVATE)).containsExactly(SAMPLE_PRIVATE_IP);
    assertThat(ipAddrs.get(IpType.PSC)).containsExactly(SAMPLE_PCS_DNS_NAME);
  }

  @Test
  public void testFetchInstanceData_returnsPscForNonIpDatabase()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {

    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        INSTANCE_CONNECTION_NAME,
        null,
        null,
        DATABASE_VERSION,
        SAMPLE_PCS_DNS_NAME,
        DEFAULT_BASE_URL,
        false);
    mockAdminApi.addGenerateEphemeralCertResponse(
        INSTANCE_CONNECTION_NAME, Duration.ofHours(1), DEFAULT_BASE_URL);
    ConnectorConfig config = new ConnectorConfig.Builder().build();

    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ConnectionInfo connectionInfo =
        repo.getConnectionInfo(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);

    Map<IpType, List<String>> ipAddrs = connectionInfo.getIpAddrs();
    assertThat(ipAddrs.get(IpType.PSC)).containsExactly(SAMPLE_PCS_DNS_NAME);
    assertThat(ipAddrs.size()).isEqualTo(1);
  }

  @Test
  public void testFetchInstanceData_legacyPscDns_returnsPscForNonIpDatabase()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {

    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        INSTANCE_CONNECTION_NAME,
        null,
        null,
        DATABASE_VERSION,
        SAMPLE_PCS_DNS_NAME,
        DEFAULT_BASE_URL,
        true);
    mockAdminApi.addGenerateEphemeralCertResponse(
        INSTANCE_CONNECTION_NAME, Duration.ofHours(1), DEFAULT_BASE_URL);
    ConnectorConfig config = new ConnectorConfig.Builder().build();

    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ConnectionInfo connectionInfo =
        repo.getConnectionInfo(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);

    Map<IpType, List<String>> ipAddrs = connectionInfo.getIpAddrs();
    assertThat(ipAddrs.get(IpType.PSC)).containsExactly(SAMPLE_PCS_DNS_NAME);
    assertThat(ipAddrs.size()).isEqualTo(1);
  }

  private ListeningScheduledExecutorService newTestExecutor() {
    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    //noinspection UnstableApiUsage
    return MoreExecutors.listeningDecorator(
        MoreExecutors.getExitingScheduledExecutorService(executor));
  }

  @Test
  public void testFetchInstanceData_throwsException_whenIamAuthnIsNotSupported()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi =
        buildMockAdminApi(
            INSTANCE_CONNECTION_NAME, "SQLSERVER_2019_STANDARD", DEFAULT_BASE_URL, false);
    ConnectorConfig config = new ConnectorConfig.Builder().build();
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> {
              repo.getConnectionInfo(
                      new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                      () -> Optional.empty(),
                      AuthType.IAM,
                      newTestExecutor(),
                      Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
                  .get();
            });
    assertThat(ex)
        .hasMessageThat()
        .contains("[p:r:i] IAM Authentication is not supported for SQL Server instances");
  }

  @Test
  public void testFetchInstanceData_throwsException_whenRequestsTimeout()
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi =
        buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION, DEFAULT_BASE_URL, false);
    ConnectorConfig config = new ConnectorConfig.Builder().build();
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(new BadConnectionFactory())
            .create(new StubCredentialFactory().create(), config);

    ExecutionException ex =
        assertThrows(
            ExecutionException.class,
            () -> {
              repo.getConnectionInfo(
                      new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                      () -> {
                        throw new IOException("Fake connect timeout");
                      },
                      AuthType.IAM,
                      newTestExecutor(),
                      Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
                  .get();
            });

    assertThat(ex.getCause()).hasMessageThat().contains("Fake connect timeout");
  }

  @Test
  public void testSetAdminUrl_FetchInstanceData_returnsIpAddresses()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {

    String adminRootUrl = "https://googleapis.example.com/";
    String adminServicePath = "sqladmin/";
    String baseUrl = adminRootUrl + adminServicePath;
    MockAdminApi mockAdminApi =
        buildMockAdminApi(INSTANCE_CONNECTION_NAME, DATABASE_VERSION, baseUrl, false);
    ConnectorConfig config =
        new ConnectorConfig.Builder()
            .withAdminRootUrl(adminRootUrl)
            .withAdminServicePath(adminServicePath)
            .build();
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ConnectionInfo connectionInfo =
        repo.getConnectionInfo(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);

    Map<IpType, List<String>> ipAddrs = connectionInfo.getIpAddrs();
    assertThat(ipAddrs.get(IpType.PUBLIC)).containsExactly(SAMPLE_PUBLIC_IP);
    assertThat(ipAddrs.get(IpType.PRIVATE)).containsExactly(SAMPLE_PRIVATE_IP);
    assertThat(ipAddrs.get(IpType.PSC)).containsExactly(SAMPLE_PCS_DNS_NAME);
  }

  @SuppressWarnings("SameParameterValue")
  private MockAdminApi buildMockAdminApi(
      String instanceConnectionName, String databaseVersion, String baseUrl, boolean legacyDnsName)
      throws GeneralSecurityException, OperatorCreationException {
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        instanceConnectionName,
        SAMPLE_PUBLIC_IP,
        SAMPLE_PRIVATE_IP,
        databaseVersion,
        SAMPLE_PCS_DNS_NAME,
        baseUrl,
        legacyDnsName);
    mockAdminApi.addGenerateEphemeralCertResponse(
        instanceConnectionName, Duration.ofHours(1), baseUrl);
    return mockAdminApi;
  }

  @Test
  public void testFetchInstanceData_multiplePscDns_sorted()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException {

    MockAdminApi mockAdminApi = new MockAdminApi();
    List<DnsNameMapping> dnsNames =
        Arrays.asList(
            new DnsNameMapping()
                .setDnsScope("INSTANCE")
                .setConnectionType("PRIVATE_SERVICE_CONNECT")
                .setName("dns1.sql.goog"),
            new DnsNameMapping()
                .setDnsScope("INSTANCE")
                .setConnectionType("PRIVATE_SERVICE_CONNECT")
                .setName("dns2.sql-psc.goog"),
            new DnsNameMapping()
                .setDnsScope("INSTANCE")
                .setConnectionType("PRIVATE_SERVICE_CONNECT")
                .setName("dns3.sql.goog"));

    mockAdminApi.addConnectSettingsResponse(
        INSTANCE_CONNECTION_NAME, null, null, DATABASE_VERSION, dnsNames, DEFAULT_BASE_URL);
    mockAdminApi.addGenerateEphemeralCertResponse(
        INSTANCE_CONNECTION_NAME, Duration.ofHours(1), DEFAULT_BASE_URL);
    ConnectorConfig config = new ConnectorConfig.Builder().build();

    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport())
            .create(new StubCredentialFactory().create(), config);

    ConnectionInfo connectionInfo =
        repo.getConnectionInfo(
                new CloudSqlInstanceName(INSTANCE_CONNECTION_NAME),
                () -> Optional.empty(),
                AuthType.PASSWORD,
                newTestExecutor(),
                Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
            .get();
    assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);

    Map<IpType, List<String>> ipAddrs = connectionInfo.getIpAddrs();
    // Should be sorted: .sql-psc.goog first
    assertThat(ipAddrs.get(IpType.PSC))
        .containsExactly("dns2.sql-psc.goog", "dns1.sql.goog", "dns3.sql.goog")
        .inOrder();
    assertThat(ipAddrs.size()).isEqualTo(1);
  }

  @Test
  public void testFetchInstanceData_withIamAuth_completesSuccessfully()
      throws ExecutionException, InterruptedException, GeneralSecurityException,
          OperatorCreationException, IOException {
    String iamInstanceName = "myProject:myRegion:myInstance";
    FakeSslServer sslServer = new FakeSslServer();
    int port = sslServer.start("127.0.0.1");
    try {
      MockAdminApi mockAdminApi = new MockAdminApi();
      mockAdminApi.addConnectSettingsResponse(
          iamInstanceName,
          "127.0.0.1",
          "127.0.0.1",
          DATABASE_VERSION,
          SAMPLE_PCS_DNS_NAME,
          DEFAULT_BASE_URL,
          false);
      mockAdminApi.addGenerateEphemeralCertResponse(
          iamInstanceName, Duration.ZERO, DEFAULT_BASE_URL);
      mockAdminApi.addConnectSettingsResponse(
          iamInstanceName,
          "127.0.0.1",
          "127.0.0.1",
          DATABASE_VERSION,
          SAMPLE_PCS_DNS_NAME,
          DEFAULT_BASE_URL,
          false);
      mockAdminApi.addGenerateEphemeralCertResponse(
          iamInstanceName, Duration.ZERO, DEFAULT_BASE_URL);

      ConnectorConfig config = new ConnectorConfig.Builder().build();
      ConnectionInfoRepository repo =
          new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport(), port)
              .create(new StubCredentialFactory().create(), config);

      CloudSqlInstanceName instanceName = new CloudSqlInstanceName(iamInstanceName);
      // 1. Probe without recorded IAM principal (TLS handshake only)
      ConnectionInfo connectionInfo =
          repo.getConnectionInfo(
                  instanceName,
                  () -> Optional.empty(),
                  AuthType.IAM,
                  newTestExecutor(),
                  Futures.immediateFuture(mockAdminApi.getClientKeyPair()))
              .get();
      assertThat(connectionInfo.getSslContext()).isInstanceOf(SSLContext.class);
      assertThat(connectionInfo.getIpAddrs().get(IpType.PUBLIC)).containsExactly("127.0.0.1");

      // 2. Probe with recorded IAM principal (TLS handshake + PostgreSQL StartupMessage +
      // Terminate)
      repo.recordIamPrincipal(instanceName, "", "");
      repo.recordIamPrincipal(instanceName, null, null);
      repo.recordIamPrincipal(instanceName, "iam-user@project.iam", "postgres");
      ConnectionInfo syncInfo =
          repo.getConnectionInfoSync(
              instanceName, () -> Optional.empty(), AuthType.IAM, mockAdminApi.getClientKeyPair());
      assertThat(syncInfo.getSslContext()).isInstanceOf(SSLContext.class);
    } finally {
      sslServer.stop();
    }
  }

  @Test
  public void testFetchInstanceData_withIamAuth_handlesUnreachableAndDomainTargets()
      throws GeneralSecurityException, OperatorCreationException {
    String iamInstanceName = "myProject:myRegion:myInstance";
    MockAdminApi mockAdminApi = new MockAdminApi();
    mockAdminApi.addConnectSettingsResponse(
        iamInstanceName,
        "127.0.0.1",
        null,
        DATABASE_VERSION,
        SAMPLE_PCS_DNS_NAME,
        DEFAULT_BASE_URL,
        false);
    mockAdminApi.addGenerateEphemeralCertResponse(iamInstanceName, Duration.ZERO, DEFAULT_BASE_URL);
    mockAdminApi.addConnectSettingsResponse(
        iamInstanceName, null, "127.0.0.1", DATABASE_VERSION, null, DEFAULT_BASE_URL, false);
    mockAdminApi.addGenerateEphemeralCertResponse(iamInstanceName, Duration.ZERO, DEFAULT_BASE_URL);

    ConnectorConfig config = new ConnectorConfig.Builder().build();
    // Port 1 on 127.0.0.1 fails immediately with connection refused
    ConnectionInfoRepository repo =
        new StubConnectionInfoRepositoryFactory(mockAdminApi.getHttpTransport(), 1)
            .create(new StubCredentialFactory().create(), config);

    CloudSqlInstanceName domainInstance =
        new CloudSqlInstanceName(iamInstanceName, "localhost.localdomain");
    repo.recordIamPrincipal(domainInstance, "iam-user@project.iam", "postgres");
    ConnectionInfo info1 =
        repo.getConnectionInfoSync(
            domainInstance, () -> Optional.empty(), AuthType.IAM, mockAdminApi.getClientKeyPair());
    assertThat(info1.getSslContext()).isInstanceOf(SSLContext.class);

    // Target with no matching IP type (only PRIVATE available, requesting PUBLIC only)
    ConnectionInfo info2 =
        repo.getConnectionInfoSync(
            new CloudSqlInstanceName(iamInstanceName),
            () -> Optional.empty(),
            AuthType.IAM,
            mockAdminApi.getClientKeyPair(),
            Arrays.asList(IpType.PUBLIC));
    assertThat(info2.getSslContext()).isInstanceOf(SSLContext.class);
  }

  @Test
  public void testPostgresStartupPacketBuildAndParse() {
    byte[] packet =
        DefaultConnectionInfoRepository.buildPostgresStartupPacket(
            "iam-user@project.iam", "postgres");
    DefaultConnectionInfoRepository.ParsedStartup parsed =
        DefaultConnectionInfoRepository.parsePostgresStartupPacket(packet);
    assertThat(parsed.complete).isTrue();
    assertThat(parsed.user).isEqualTo("iam-user@project.iam");
    assertThat(parsed.database).isEqualTo("postgres");

    // Empty database defaults to user in both build and parse
    byte[] defaultDbPacket =
        DefaultConnectionInfoRepository.buildPostgresStartupPacket("iam-user", "");
    DefaultConnectionInfoRepository.ParsedStartup parsedDefaultDb =
        DefaultConnectionInfoRepository.parsePostgresStartupPacket(defaultDbPacket);
    assertThat(parsedDefaultDb.complete).isTrue();
    assertThat(parsedDefaultDb.user).isEqualTo("iam-user");
    assertThat(parsedDefaultDb.database).isEqualTo("iam-user");

    // Null / short buffer
    assertThat(DefaultConnectionInfoRepository.parsePostgresStartupPacket(null).complete).isFalse();
    assertThat(DefaultConnectionInfoRepository.parsePostgresStartupPacket(new byte[4]).complete)
        .isFalse();

    // SSLRequest (80877103) prefix alone (incomplete) and prepended to valid StartupMessage
    java.nio.ByteBuffer sslReq = java.nio.ByteBuffer.allocate(8);
    sslReq.putInt(8).putInt(80877103);
    assertThat(DefaultConnectionInfoRepository.parsePostgresStartupPacket(sslReq.array()).complete)
        .isFalse();

    java.nio.ByteBuffer combined = java.nio.ByteBuffer.allocate(8 + packet.length);
    combined.put(sslReq.array()).put(packet);
    DefaultConnectionInfoRepository.ParsedStartup parsedWithSsl =
        DefaultConnectionInfoRepository.parsePostgresStartupPacket(combined.array());
    assertThat(parsedWithSsl.complete).isTrue();
    assertThat(parsedWithSsl.user).isEqualTo("iam-user@project.iam");
    assertThat(parsedWithSsl.database).isEqualTo("postgres");

    // Incomplete startup body
    byte[] truncated = Arrays.copyOf(packet, packet.length - 3);
    assertThat(DefaultConnectionInfoRepository.parsePostgresStartupPacket(truncated).complete)
        .isFalse();

    // Non-v3 protocol version
    java.nio.ByteBuffer badProto = java.nio.ByteBuffer.allocate(12);
    badProto.putInt(12).putInt(0x00020000).putInt(0);
    DefaultConnectionInfoRepository.ParsedStartup parsedBadProto =
        DefaultConnectionInfoRepository.parsePostgresStartupPacket(badProto.array());
    assertThat(parsedBadProto.complete).isTrue();
    assertThat(parsedBadProto.user).isEmpty();

    // Startup packet with only user key-value (no database key)
    byte[] userOnlyBody = "user\0alice\0\0".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    java.nio.ByteBuffer userOnlyBuf = java.nio.ByteBuffer.allocate(8 + userOnlyBody.length);
    userOnlyBuf.putInt(8 + userOnlyBody.length).putInt(0x00030000).put(userOnlyBody);
    DefaultConnectionInfoRepository.ParsedStartup parsedUserOnly =
        DefaultConnectionInfoRepository.parsePostgresStartupPacket(userOnlyBuf.array());
    assertThat(parsedUserOnly.complete).isTrue();
    assertThat(parsedUserOnly.user).isEqualTo("alice");
    assertThat(parsedUserOnly.database).isEqualTo("alice");

    // IamPrincipal equals & hashCode
    DefaultConnectionInfoRepository.IamPrincipal p1 =
        new DefaultConnectionInfoRepository.IamPrincipal("u1", "db1");
    DefaultConnectionInfoRepository.IamPrincipal p2 =
        new DefaultConnectionInfoRepository.IamPrincipal("u1", "db1");
    DefaultConnectionInfoRepository.IamPrincipal p3 =
        new DefaultConnectionInfoRepository.IamPrincipal("u1", null);
    assertThat(p1).isEqualTo(p1);
    assertThat(p1).isEqualTo(p2);
    assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    assertThat(p1).isNotEqualTo(p3);
    assertThat(p1).isNotEqualTo("not-a-principal");
  }

  @Test
  public void testPostgresStartupSnifferSocket() throws IOException {
    byte[] packet =
        DefaultConnectionInfoRepository.buildPostgresStartupPacket(
            "sniffer-user@project.iam", "sniffer-db");
    String[] captured = new String[2];
    StubSslSocket delegate = new StubSslSocket();
    PostgresStartupSnifferSocket sniffer =
        new PostgresStartupSnifferSocket(
            delegate,
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });

    // Write byte-by-byte first 4 bytes, then rest of packet, then extra bytes after done
    java.io.OutputStream out = sniffer.getOutputStream();
    for (int i = 0; i < 4; i++) {
      out.write(packet[i]);
    }
    out.write(packet, 4, packet.length - 4);
    assertThat(captured[0]).isEqualTo("sniffer-user@project.iam");
    assertThat(captured[1]).isEqualTo("sniffer-db");

    // Subsequent writes pass through directly after done == true
    out.write(0x58);
    out.write(new byte[] {0, 0, 0, 4});
    out.write(new byte[] {1, 2, 3}, 0, 2);
    out.flush();
    out.close();

    // Test write(byte[]) in one shot
    String[] captured2 = new String[2];
    PostgresStartupSnifferSocket sniffer2 =
        new PostgresStartupSnifferSocket(
            new StubSslSocket(),
            (u, d) -> {
              captured2[0] = u;
              captured2[1] = d;
            });
    sniffer2.getOutputStream().write(packet);
    assertThat(captured2[0]).isEqualTo("sniffer-user@project.iam");
    assertThat(captured2[1]).isEqualTo("sniffer-db");

    // Exercise SSLSocket delegation methods
    assertThat(sniffer.getInputStream()).isNotNull();
    sniffer.connect(null);
    sniffer.connect(null, 100);
    sniffer.bind(null);
    assertThat(sniffer.getInetAddress()).isNull();
    assertThat(sniffer.getLocalAddress()).isNotNull();
    assertThat(sniffer.getPort()).isEqualTo(0);
    assertThat(sniffer.getLocalPort()).isEqualTo(-1);
    assertThat(sniffer.getRemoteSocketAddress()).isNull();
    assertThat(sniffer.getLocalSocketAddress()).isNull();
    assertThat(sniffer.getChannel()).isNull();
    sniffer.setTcpNoDelay(true);
    assertThat(sniffer.getTcpNoDelay()).isFalse();
    sniffer.setSoLinger(true, 1);
    assertThat(sniffer.getSoLinger()).isEqualTo(-1);
    sniffer.sendUrgentData(0);
    sniffer.setOOBInline(true);
    assertThat(sniffer.getOOBInline()).isFalse();
    sniffer.setSoTimeout(100);
    assertThat(sniffer.getSoTimeout()).isEqualTo(0);
    sniffer.setSendBufferSize(1024);
    assertThat(sniffer.getSendBufferSize()).isEqualTo(0);
    sniffer.setReceiveBufferSize(1024);
    assertThat(sniffer.getReceiveBufferSize()).isEqualTo(0);
    sniffer.setKeepAlive(true);
    assertThat(sniffer.getKeepAlive()).isFalse();
    sniffer.setTrafficClass(0);
    assertThat(sniffer.getTrafficClass()).isEqualTo(0);
    sniffer.setReuseAddress(true);
    assertThat(sniffer.getReuseAddress()).isFalse();
    sniffer.shutdownInput();
    sniffer.shutdownOutput();
    assertThat(sniffer.toString()).isNotEmpty();
    assertThat(sniffer.isConnected()).isFalse();
    assertThat(sniffer.isBound()).isFalse();
    assertThat(sniffer.isClosed()).isFalse();
    assertThat(sniffer.isInputShutdown()).isFalse();
    assertThat(sniffer.isOutputShutdown()).isFalse();
    sniffer.setPerformancePreferences(0, 0, 0);
    assertThat(sniffer.getSupportedCipherSuites()).isEmpty();
    assertThat(sniffer.getEnabledCipherSuites()).isEmpty();
    sniffer.setEnabledCipherSuites(new String[0]);
    assertThat(sniffer.getSupportedProtocols()).isEmpty();
    assertThat(sniffer.getEnabledProtocols()).isEmpty();
    sniffer.setEnabledProtocols(new String[0]);
    assertThat(sniffer.getSession()).isNull();
    assertThat(sniffer.getHandshakeSession()).isNull();
    sniffer.addHandshakeCompletedListener(null);
    sniffer.removeHandshakeCompletedListener(null);
    sniffer.startHandshake();
    sniffer.setUseClientMode(true);
    assertThat(sniffer.getUseClientMode()).isFalse();
    sniffer.setNeedClientAuth(false);
    assertThat(sniffer.getNeedClientAuth()).isFalse();
    sniffer.setWantClientAuth(false);
    assertThat(sniffer.getWantClientAuth()).isFalse();
    sniffer.setEnableSessionCreation(true);
    assertThat(sniffer.getEnableSessionCreation()).isFalse();
    assertThat(sniffer.getSSLParameters()).isNotNull();
    sniffer.setSSLParameters(sniffer.getSSLParameters());
    sniffer.close();
  }

  private static class StubSslSocket extends javax.net.ssl.SSLSocket {
    private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    private final java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(new byte[0]);

    @Override
    public java.io.InputStream getInputStream() {
      return in;
    }

    @Override
    public java.io.OutputStream getOutputStream() {
      return out;
    }

    @Override
    public void connect(java.net.SocketAddress endpoint) {}

    @Override
    public void connect(java.net.SocketAddress endpoint, int timeout) {}

    @Override
    public void bind(java.net.SocketAddress bindpoint) {}

    @Override
    public void setTcpNoDelay(boolean on) {}

    @Override
    public boolean getTcpNoDelay() {
      return false;
    }

    @Override
    public void setSoLinger(boolean on, int linger) {}

    @Override
    public int getSoLinger() {
      return -1;
    }

    @Override
    public void sendUrgentData(int data) {}

    @Override
    public void setOOBInline(boolean on) {}

    @Override
    public boolean getOOBInline() {
      return false;
    }

    @Override
    public void setSoTimeout(int timeout) {}

    @Override
    public int getSoTimeout() {
      return 0;
    }

    @Override
    public void setSendBufferSize(int size) {}

    @Override
    public int getSendBufferSize() {
      return 0;
    }

    @Override
    public void setReceiveBufferSize(int size) {}

    @Override
    public int getReceiveBufferSize() {
      return 0;
    }

    @Override
    public void setKeepAlive(boolean on) {}

    @Override
    public boolean getKeepAlive() {
      return false;
    }

    @Override
    public void setTrafficClass(int tc) {}

    @Override
    public int getTrafficClass() {
      return 0;
    }

    @Override
    public void setReuseAddress(boolean on) {}

    @Override
    public boolean getReuseAddress() {
      return false;
    }

    @Override
    public void shutdownInput() {}

    @Override
    public void shutdownOutput() {}

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[0];
    }

    @Override
    public String[] getEnabledCipherSuites() {
      return new String[0];
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {}

    @Override
    public String[] getSupportedProtocols() {
      return new String[0];
    }

    @Override
    public String[] getEnabledProtocols() {
      return new String[0];
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {}

    @Override
    public javax.net.ssl.SSLSession getSession() {
      return null;
    }

    @Override
    public javax.net.ssl.SSLSession getHandshakeSession() {
      return null;
    }

    @Override
    public void addHandshakeCompletedListener(javax.net.ssl.HandshakeCompletedListener listener) {}

    @Override
    public void removeHandshakeCompletedListener(
        javax.net.ssl.HandshakeCompletedListener listener) {}

    @Override
    public void startHandshake() {}

    @Override
    public void setUseClientMode(boolean mode) {}

    @Override
    public boolean getUseClientMode() {
      return false;
    }

    @Override
    public void setNeedClientAuth(boolean need) {}

    @Override
    public boolean getNeedClientAuth() {
      return false;
    }

    @Override
    public void setWantClientAuth(boolean want) {}

    @Override
    public boolean getWantClientAuth() {
      return false;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {}

    @Override
    public boolean getEnableSessionCreation() {
      return false;
    }
  }
}
