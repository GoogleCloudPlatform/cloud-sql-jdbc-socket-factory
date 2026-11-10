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
import static org.junit.Assert.assertThrows;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.IpType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PostgresSocketTest {

  @Test
  public void testPostgresStartupPacketBuildAndParse() {
    byte[] packet = PostgresSocket.buildPostgresStartupPacket("iam-user@project.iam", "postgres");
    PostgresSocket.ParsedStartup parsed = PostgresSocket.parsePostgresStartupPacket(packet);
    assertThat(parsed.complete).isTrue();
    assertThat(parsed.user).isEqualTo("iam-user@project.iam");
    assertThat(parsed.database).isEqualTo("postgres");

    // Empty database defaults to user in both build and parse
    byte[] defaultDbPacket = PostgresSocket.buildPostgresStartupPacket("iam-user", "");
    PostgresSocket.ParsedStartup parsedDefaultDb =
        PostgresSocket.parsePostgresStartupPacket(defaultDbPacket);
    assertThat(parsedDefaultDb.complete).isTrue();
    assertThat(parsedDefaultDb.user).isEqualTo("iam-user");
    assertThat(parsedDefaultDb.database).isEqualTo("iam-user");

    // Null / short buffer
    assertThat(PostgresSocket.parsePostgresStartupPacket(null).complete).isFalse();
    assertThat(PostgresSocket.parsePostgresStartupPacket(new byte[4]).complete).isFalse();

    // SSLRequest (80877103) prefix alone (incomplete) and prepended to valid StartupMessage
    ByteBuffer sslReq = ByteBuffer.allocate(8);
    sslReq.putInt(8).putInt(80877103);
    assertThat(PostgresSocket.parsePostgresStartupPacket(sslReq.array()).complete).isFalse();

    ByteBuffer combined = ByteBuffer.allocate(8 + packet.length);
    combined.put(sslReq.array()).put(packet);
    PostgresSocket.ParsedStartup parsedWithSsl =
        PostgresSocket.parsePostgresStartupPacket(combined.array());
    assertThat(parsedWithSsl.complete).isTrue();
    assertThat(parsedWithSsl.user).isEqualTo("iam-user@project.iam");
    assertThat(parsedWithSsl.database).isEqualTo("postgres");

    // Double negotiation: GSSENCRequest followed by SSLRequest prepended to StartupMessage
    ByteBuffer gssReq = ByteBuffer.allocate(8);
    gssReq.putInt(8).putInt(80877104);
    ByteBuffer doubleNegotiation = ByteBuffer.allocate(16 + packet.length);
    doubleNegotiation.put(gssReq.array()).put(sslReq.array()).put(packet);
    PostgresSocket.ParsedStartup parsedDoubleNeg =
        PostgresSocket.parsePostgresStartupPacket(doubleNegotiation.array());
    assertThat(parsedDoubleNeg.complete).isTrue();
    assertThat(parsedDoubleNeg.user).isEqualTo("iam-user@project.iam");
    assertThat(parsedDoubleNeg.database).isEqualTo("postgres");

    // Incomplete startup body
    byte[] truncated = Arrays.copyOf(packet, packet.length - 3);
    assertThat(PostgresSocket.parsePostgresStartupPacket(truncated).complete).isFalse();

    // Non-v3 protocol version
    ByteBuffer badProto = ByteBuffer.allocate(12);
    badProto.putInt(12).putInt(0x00020000).putInt(0);
    PostgresSocket.ParsedStartup parsedBadProto =
        PostgresSocket.parsePostgresStartupPacket(badProto.array());
    assertThat(parsedBadProto.complete).isTrue();
    assertThat(parsedBadProto.user).isEmpty();

    // Startup packet with only user key-value (no database key)
    byte[] userOnlyBody = "user\0alice\0\0".getBytes(StandardCharsets.UTF_8);
    ByteBuffer userOnlyBuf = ByteBuffer.allocate(8 + userOnlyBody.length);
    userOnlyBuf.putInt(8 + userOnlyBody.length).putInt(0x00030000).put(userOnlyBody);
    PostgresSocket.ParsedStartup parsedUserOnly =
        PostgresSocket.parsePostgresStartupPacket(userOnlyBuf.array());
    assertThat(parsedUserOnly.complete).isTrue();
    assertThat(parsedUserOnly.user).isEqualTo("alice");
    assertThat(parsedUserOnly.database).isEqualTo("alice");

    // IamPrincipal equals & hashCode
    PostgresSocket.IamPrincipal p1 = new PostgresSocket.IamPrincipal("u1", "db1");
    PostgresSocket.IamPrincipal p2 = new PostgresSocket.IamPrincipal("u1", "db1");
    PostgresSocket.IamPrincipal p3 = new PostgresSocket.IamPrincipal("u1", null);
    assertThat(p1).isEqualTo(p1);
    assertThat(p1).isEqualTo(p2);
    assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    assertThat(p1).isNotEqualTo(p3);
    assertThat(p1).isNotEqualTo("not-a-principal");
  }

  @Test
  public void testPostgresSocket_delegationsAndSingleShotWrite() throws IOException {
    byte[] packet =
        PostgresSocket.buildPostgresStartupPacket("sniffer-user@project.iam", "sniffer-db");
    String[] captured = new String[2];
    StubSslSocket delegate = new StubSslSocket();
    PostgresSocket socket =
        new PostgresSocket(
            delegate,
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });

    // Write byte-by-byte first 4 bytes, then rest of packet, then extra bytes after completion
    OutputStream out = socket.getOutputStream();
    for (int i = 0; i < 4; i++) {
      out.write(packet[i]);
    }
    out.write(packet, 4, packet.length - 4);
    assertThat(captured[0]).isEqualTo("sniffer-user@project.iam");
    assertThat(captured[1]).isEqualTo("sniffer-db");

    // Subsequent writes pass through directly after startupMessageSent == true
    out.write(0x58);
    out.write(new byte[] {0, 0, 0, 4});
    out.write(new byte[] {1, 2, 3}, 0, 2);
    out.flush();
    out.close();

    // Test write(byte[]) in one shot
    String[] captured2 = new String[2];
    PostgresSocket socket2 =
        new PostgresSocket(
            new StubSslSocket(),
            (u, d) -> {
              captured2[0] = u;
              captured2[1] = d;
            });
    socket2.getOutputStream().write(packet);
    assertThat(captured2[0]).isEqualTo("sniffer-user@project.iam");
    assertThat(captured2[1]).isEqualTo("sniffer-db");

    // Exercise SSLSocket delegation methods
    assertThat(socket.getInputStream()).isNotNull();
    socket.connect(null);
    socket.connect(null, 100);
    socket.bind(null);
    assertThat(socket.getInetAddress()).isNull();
    assertThat(socket.getLocalAddress()).isNotNull();
    assertThat(socket.getPort()).isEqualTo(0);
    assertThat(socket.getLocalPort()).isEqualTo(-1);
    assertThat(socket.getRemoteSocketAddress()).isNull();
    assertThat(socket.getLocalSocketAddress()).isNull();
    assertThat(socket.getChannel()).isNull();
    socket.setTcpNoDelay(true);
    assertThat(socket.getTcpNoDelay()).isFalse();
    socket.setSoLinger(true, 1);
    assertThat(socket.getSoLinger()).isEqualTo(-1);
    socket.sendUrgentData(0);
    socket.setOOBInline(true);
    assertThat(socket.getOOBInline()).isFalse();
    socket.setSoTimeout(100);
    assertThat(socket.getSoTimeout()).isEqualTo(0);
    socket.setSendBufferSize(1024);
    assertThat(socket.getSendBufferSize()).isEqualTo(0);
    socket.setReceiveBufferSize(1024);
    assertThat(socket.getReceiveBufferSize()).isEqualTo(0);
    socket.setKeepAlive(true);
    assertThat(socket.getKeepAlive()).isFalse();
    socket.setTrafficClass(0);
    assertThat(socket.getTrafficClass()).isEqualTo(0);
    socket.setReuseAddress(true);
    assertThat(socket.getReuseAddress()).isFalse();
    socket.shutdownInput();
    socket.shutdownOutput();
    assertThat(socket.toString()).isNotEmpty();
    assertThat(socket.isConnected()).isFalse();
    assertThat(socket.isBound()).isFalse();
    assertThat(socket.isClosed()).isFalse();
    assertThat(socket.isInputShutdown()).isFalse();
    assertThat(socket.isOutputShutdown()).isFalse();
    socket.setPerformancePreferences(0, 0, 0);
    assertThat(socket.getSupportedCipherSuites()).isEmpty();
    assertThat(socket.getEnabledCipherSuites()).isEmpty();
    socket.setEnabledCipherSuites(new String[0]);
    assertThat(socket.getSupportedProtocols()).isEmpty();
    assertThat(socket.getEnabledProtocols()).isEmpty();
    socket.setEnabledProtocols(new String[0]);
    assertThat(socket.getSession()).isNull();
    assertThat(socket.getHandshakeSession()).isNull();
    socket.addHandshakeCompletedListener(null);
    socket.removeHandshakeCompletedListener(null);
    socket.startHandshake();
    socket.setUseClientMode(true);
    assertThat(socket.getUseClientMode()).isFalse();
    socket.setNeedClientAuth(false);
    assertThat(socket.getNeedClientAuth()).isFalse();
    socket.setWantClientAuth(false);
    assertThat(socket.getWantClientAuth()).isFalse();
    socket.setEnableSessionCreation(true);
    assertThat(socket.getEnableSessionCreation()).isFalse();
    assertThat(socket.getSSLParameters()).isNotNull();
    socket.setSSLParameters(socket.getSSLParameters());
    socket.close();
  }

  @Test
  public void testPostgresSocket_splitWriteCalls() throws IOException {
    byte[] packet = PostgresSocket.buildPostgresStartupPacket("split-user@project.iam", "split-db");
    String[] captured = new String[2];
    StubSslSocket delegate = new StubSslSocket();
    PostgresSocket socket =
        new PostgresSocket(
            delegate,
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });
    OutputStream out = socket.getOutputStream();

    // 1. Write the first byte only (split write)
    out.write(packet, 0, 1);
    assertThat(captured[0]).isNull();
    assertThat(captured[1]).isNull();

    // 2. Write the remainder of the packet
    out.write(packet, 1, packet.length - 1);
    assertThat(captured[0]).isEqualTo("split-user@project.iam");
    assertThat(captured[1]).isEqualTo("split-db");

    // 3. Subsequent writes go straight to delegate with zero locking overhead
    out.write(new byte[] {'Q', 0, 0, 0, 5, 0});
    out.flush();
    out.close();
    assertThat(delegate.getWrittenBytes().length).isEqualTo(packet.length + 6);
  }

  @Test
  public void testPostgresSocket_splitWriteSingleByteInt() throws IOException {
    byte[] packet = PostgresSocket.buildPostgresStartupPacket("int-user@project.iam", "int-db");
    String[] captured = new String[2];
    StubSslSocket delegate = new StubSslSocket();
    PostgresSocket socket =
        new PostgresSocket(
            delegate,
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });
    OutputStream out = socket.getOutputStream();

    out.write(packet[0]);
    assertThat(captured[0]).isNull();

    out.write(packet, 1, packet.length - 1);
    assertThat(captured[0]).isEqualTo("int-user@project.iam");
    assertThat(captured[1]).isEqualTo("int-db");
    out.close();
  }

  @Test
  public void testPostgresSocket_outputStreamContractBeforeInspect() throws IOException {
    byte[] packet = PostgresSocket.buildPostgresStartupPacket("ok-user", "ok-db");
    String[] captured = new String[2];
    AtomicBoolean failNextWrite = new AtomicBoolean(true);
    StubSslSocket delegate = new StubSslSocket(new byte[0], failNextWrite);
    PostgresSocket socket =
        new PostgresSocket(
            delegate,
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });
    OutputStream out = socket.getOutputStream();

    // Delegate throws IOException -> buffer must NOT be polluted by the failed write
    assertThrows(IOException.class, () -> out.write(new byte[] {1, 2, 3}, 0, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> out.write(packet, -1, 4));
    assertThrows(NullPointerException.class, () -> out.write((byte[]) null, 0, 1));

    // Once delegate succeeds, writing the actual packet parses cleanly from offset 0
    failNextWrite.set(false);
    out.write(packet);
    assertThat(captured[0]).isEqualTo("ok-user");
    assertThat(captured[1]).isEqualTo("ok-db");
  }

  @Test
  public void testPostgresSocket_doubleNegotiationLargeStartupPacket() throws IOException {
    // Construct a StartupMessage close to MAX_PG_STARTUP_PACKET_LEN preceded by 16 negotiation
    // bytes
    StringBuilder paddingUser = new StringBuilder();
    for (int i = 0; i < 9900; i++) {
      paddingUser.append('a');
    }
    byte[] packet = PostgresSocket.buildPostgresStartupPacket(paddingUser.toString(), "postgres");
    assertThat(packet.length).isLessThan(PostgresSocket.MAX_PG_STARTUP_PACKET_LEN);

    ByteBuffer neg = ByteBuffer.allocate(16);
    neg.putInt(8).putInt(80877104); // GSSENCRequest
    neg.putInt(8).putInt(80877103); // SSLRequest

    String[] captured = new String[2];
    PostgresSocket socket =
        new PostgresSocket(
            new StubSslSocket(),
            (u, d) -> {
              captured[0] = u;
              captured[1] = d;
            });
    OutputStream out = socket.getOutputStream();
    out.write(neg.array());
    out.write(packet);

    assertThat(captured[0]).isEqualTo(paddingUser.toString());
    assertThat(captured[1]).isEqualTo("postgres");
  }

  @Test
  public void testPostgresProbe_immediateConnectionTermination_success() throws IOException {
    // Server sends PostgreSQL AuthenticationOk (R + len 8 + code 0) followed by EOF
    byte[] serverAuthOk = new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0};
    StubSslSocket stubSocket = new StubSslSocket(serverAuthOk);

    PostgresSocket.sendStartupProbe(stubSocket, "iam-user@project.iam", "postgres");

    byte[] written = stubSocket.getWrittenBytes();
    byte[] expectedStartup =
        PostgresSocket.buildPostgresStartupPacket("iam-user@project.iam", "postgres");
    byte[] expectedTerminate = PostgresSocket.buildTerminatePacket();

    assertThat(written.length).isEqualTo(expectedStartup.length + expectedTerminate.length);
    assertThat(Arrays.copyOfRange(written, 0, expectedStartup.length)).isEqualTo(expectedStartup);
    assertThat(Arrays.copyOfRange(written, expectedStartup.length, written.length))
        .isEqualTo(expectedTerminate);
    // EOF should be drained completely
    assertThat(stubSocket.getInputStream().read()).isEqualTo(-1);
  }

  @Test
  public void testPostgresProbe_immediateConnectionTermination_serverEof() throws IOException {
    // Server immediately closes connection (EOF / 0 bytes returned)
    StubSslSocket stubSocket = new StubSslSocket(new byte[0]);

    // Must complete immediately without exception or hang
    PostgresSocket.sendStartupProbe(stubSocket, "iam-user@project.iam", "postgres");

    byte[] written = stubSocket.getWrittenBytes();
    byte[] expectedStartup =
        PostgresSocket.buildPostgresStartupPacket("iam-user@project.iam", "postgres");
    assertThat(written).isEqualTo(expectedStartup);
  }

  @Test
  public void testPostgresProbe_drainsMultiChunkEofAfterTerminate() throws IOException {
    AtomicInteger readCalls = new AtomicInteger();
    // 1st read: AuthenticationOk, 2nd & 3rd reads: extra trailing bytes, 4th read: EOF (-1)
    InputStream chunkedInput =
        new InputStream() {
          @Override
          public int read() {
            return -1;
          }

          @Override
          public int read(byte[] b, int off, int len) {
            int call = readCalls.getAndIncrement();
            if (call == 0) {
              byte[] authOk = new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0};
              System.arraycopy(authOk, 0, b, off, authOk.length);
              return authOk.length;
            }
            if (call <= 2) {
              b[off] = 'Z';
              return 1;
            }
            return -1;
          }
        };
    StubSslSocket stubSocket = new StubSslSocket(chunkedInput);

    PostgresSocket.sendStartupProbe(stubSocket, "iam-user@project.iam", "postgres");

    assertThat(readCalls.get()).isEqualTo(4);
  }

  @Test
  public void testPostgresProbe_emptyUser_noOp() throws IOException {
    StubSslSocket stubSocket = new StubSslSocket();
    PostgresSocket.sendStartupProbe(stubSocket, "", "postgres");
    PostgresSocket.sendStartupProbe(stubSocket, null, "postgres");
    assertThat(stubSocket.getWrittenBytes()).isEmpty();
  }

  @Test
  public void testProbeConnection_gatingAndErrorResilience() throws Exception {
    ConnectionConfig iamConfig =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("p:r:i")
            .withAuthType(AuthType.IAM)
            .build();
    ConnectionConfig passwordConfig =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("p:r:i")
            .withAuthType(AuthType.PASSWORD)
            .build();
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("p:r:i");

    ConnectionInfo postgresInfo = newConnectionInfo("POSTGRES_15");
    ConnectionInfo mysqlInfo = newConnectionInfo("MYSQL_8_0");

    Set<PostgresSocket.IamPrincipal> principals = new HashSet<>();
    principals.add(new PostgresSocket.IamPrincipal("u1@iam", "db1"));

    AtomicInteger dialCount = new AtomicInteger();
    ByteArrayOutputStream writtenBytes = new ByteArrayOutputStream();
    PostgresSocket.ProbeDialer recordingDialer =
        (metadata, timeoutMs) -> {
          dialCount.incrementAndGet();
          return new StubSslSocket(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0}, writtenBytes);
        };

    // 1. Non-Postgres instance -> skipped
    PostgresSocket.probeConnection(iamConfig, instanceName, mysqlInfo, recordingDialer, principals);
    assertThat(dialCount.get()).isEqualTo(0);

    // 2. Non-IAM auth type -> skipped
    PostgresSocket.probeConnection(
        passwordConfig, instanceName, postgresInfo, recordingDialer, principals);
    assertThat(dialCount.get()).isEqualTo(0);

    // 3. Postgres + IAM + empty/null principals -> skipped (no 0-principal TLS dial)
    PostgresSocket.probeConnection(
        iamConfig, instanceName, postgresInfo, recordingDialer, Collections.emptySet());
    PostgresSocket.probeConnection(iamConfig, instanceName, postgresInfo, recordingDialer, null);
    assertThat(dialCount.get()).isEqualTo(0);
    assertThat(writtenBytes.toByteArray()).isEmpty();

    // 4. Postgres + IAM + recorded principal -> dials and writes StartupMessage + Terminate
    PostgresSocket.probeConnection(
        iamConfig, instanceName, postgresInfo, recordingDialer, principals);
    assertThat(dialCount.get()).isEqualTo(1);
    byte[] expected = PostgresSocket.buildPostgresStartupPacket("u1@iam", "db1");
    assertThat(writtenBytes.toByteArray().length)
        .isEqualTo(expected.length + PostgresSocket.buildTerminatePacket().length);

    // 5. Dialer IOException does not propagate
    PostgresSocket.ProbeDialer failingDialer =
        (metadata, timeoutMs) -> {
          throw new IOException("connection refused");
        };
    PostgresSocket.probeConnection(
        iamConfig, instanceName, postgresInfo, failingDialer, principals);
  }

  @Test
  public void testIamPrincipal_normalizesEmptyAndNullDatabaseAndDeduplicatesProbe()
      throws Exception {
    PostgresSocket.IamPrincipal emptyDb = new PostgresSocket.IamPrincipal("alice", "");
    PostgresSocket.IamPrincipal nullDb = new PostgresSocket.IamPrincipal("alice", null);
    PostgresSocket.IamPrincipal sameDb = new PostgresSocket.IamPrincipal("alice", "alice");
    PostgresSocket.IamPrincipal otherDb = new PostgresSocket.IamPrincipal("alice", "otherdb");

    assertThat(emptyDb.database).isEqualTo("alice");
    assertThat(nullDb.database).isEqualTo("alice");
    assertThat(emptyDb).isEqualTo(sameDb);
    assertThat(nullDb).isEqualTo(sameDb);
    assertThat(emptyDb.hashCode()).isEqualTo(sameDb.hashCode());
    assertThat(nullDb.hashCode()).isEqualTo(sameDb.hashCode());
    assertThat(emptyDb).isNotEqualTo(otherDb);

    Set<PostgresSocket.IamPrincipal> deduplicated =
        new HashSet<>(Arrays.asList(emptyDb, nullDb, sameDb));
    assertThat(deduplicated).hasSize(1);

    ConnectionConfig iamConfig =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("p:r:i")
            .withAuthType(AuthType.IAM)
            .build();
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("p:r:i");
    ConnectionInfo postgresInfo = newConnectionInfo("POSTGRES_15");
    AtomicInteger dialCount = new AtomicInteger();
    PostgresSocket.probeConnection(
        iamConfig,
        instanceName,
        postgresInfo,
        (metadata, timeoutMs) -> {
          dialCount.incrementAndGet();
          return new StubSslSocket(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0});
        },
        deduplicated);
    assertThat(dialCount.get()).isEqualTo(1);
  }

  @Test
  public void testProbeConnection_sharedDeadlineTruncatesRemainingPrincipals() throws Exception {
    ConnectionConfig iamConfig =
        new ConnectionConfig.Builder()
            .withCloudSqlInstance("p:r:i")
            .withAuthType(AuthType.IAM)
            .build();
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("p:r:i");
    ConnectionInfo postgresInfo = newConnectionInfo("POSTGRES_15");
    Set<PostgresSocket.IamPrincipal> principals =
        new HashSet<>(
            Arrays.asList(
                new PostgresSocket.IamPrincipal("u1@iam", "db1"),
                new PostgresSocket.IamPrincipal("u2@iam", "db2"),
                new PostgresSocket.IamPrincipal("u3@iam", "db3")));

    List<Integer> observedDialTimeouts = new ArrayList<>();
    PostgresSocket.probeConnection(
        iamConfig,
        instanceName,
        postgresInfo,
        (metadata, timeoutMs) -> {
          observedDialTimeouts.add(timeoutMs);
          try {
            Thread.sleep(60);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          throw new IOException("simulated slow dial failure");
        },
        principals,
        90);

    // Budget is 90ms and each dial takes 60ms, so at most 2 dials can start before deadline
    // expires and remaining dial timeouts shrink strictly across attempts.
    assertThat(observedDialTimeouts.size()).isAtMost(2);
    assertThat(observedDialTimeouts).isNotEmpty();
    assertThat(observedDialTimeouts.get(0)).isAtMost(90);
    if (observedDialTimeouts.size() == 2) {
      assertThat(observedDialTimeouts.get(1)).isLessThan(observedDialTimeouts.get(0));
    }

    // Zero or negative probe timeout returns immediately without dialing.
    observedDialTimeouts.clear();
    PostgresSocket.probeConnection(
        iamConfig,
        instanceName,
        postgresInfo,
        (metadata, timeoutMs) -> {
          observedDialTimeouts.add(timeoutMs);
          return new StubSslSocket();
        },
        principals,
        0);
    assertThat(observedDialTimeouts).isEmpty();
  }

  @Test
  public void testSendStartupProbe_eofDrainEnforcesWallClockDeadline() throws IOException {
    AtomicInteger reads = new AtomicInteger();
    InputStream trickleIn =
        new InputStream() {
          @Override
          public int read() throws IOException {
            byte[] buf = new byte[1];
            int n = read(buf, 0, 1);
            return n == -1 ? -1 : (buf[0] & 0xFF);
          }

          @Override
          public int read(byte[] b, int off, int len) {
            int call = reads.incrementAndGet();
            if (call == 1) {
              b[off] = 'R';
              return 1;
            }
            try {
              Thread.sleep(150);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            b[off] = 'Z';
            return 1;
          }
        };

    StubSslSocket socket = new StubSslSocket(trickleIn);
    long startNanos = System.nanoTime();
    PostgresSocket.sendStartupProbe(socket, "u1@iam", "db1");
    long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L;

    // Drain timeout is 500ms wall-clock (not 16 * 500ms or 16 * 150ms = 2400ms).
    assertThat(reads.get()).isLessThan(10);
    assertThat(elapsedMs).isLessThan(1200L);
    List<Integer> timeouts = socket.getRecordedSoTimeouts();
    assertThat(timeouts.get(0)).isEqualTo(5000);
    assertThat(timeouts.size()).isAtLeast(2);
    assertThat(timeouts.get(1)).isAtMost(500);
    assertThat(timeouts.get(timeouts.size() - 1)).isLessThan(timeouts.get(1));

    // Caller budget < PROBE_READ_TIMEOUT_MS clamps initial setSoTimeout as well.
    StubSslSocket shortBudgetSocket = new StubSslSocket(new byte[] {'R', 0, 0, 0, 8, 0, 0, 0, 0});
    PostgresSocket.sendStartupProbe(shortBudgetSocket, "u1@iam", "db1", 120);
    assertThat(shortBudgetSocket.getRecordedSoTimeouts().get(0)).isEqualTo(120);
  }

  private static ConnectionInfo newConnectionInfo(String databaseVersion)
      throws NoSuchAlgorithmException {
    Map<IpType, List<String>> ips =
        Collections.singletonMap(IpType.PUBLIC, Collections.singletonList("10.1.1.1"));
    return new ConnectionInfo(
        new InstanceMetadata(
            new CloudSqlInstanceName("p:r:i"), ips, null, false, "", false, null, databaseVersion),
        new SslData(
            null, KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()), null),
        Instant.now().plus(1, ChronoUnit.HOURS));
  }

  static class StubSslSocket extends SSLSocket {
    private final ByteArrayOutputStream internalOut;
    private final OutputStream out;
    private final InputStream in;
    private final AtomicBoolean failOnWrite;
    private final List<Integer> recordedSoTimeouts = new ArrayList<>();

    StubSslSocket() {
      this(new byte[0]);
    }

    StubSslSocket(byte[] inputBytes) {
      this(inputBytes, new AtomicBoolean(false));
    }

    StubSslSocket(byte[] inputBytes, AtomicBoolean failOnWrite) {
      this(new ByteArrayInputStream(inputBytes), new ByteArrayOutputStream(), failOnWrite);
    }

    StubSslSocket(byte[] inputBytes, ByteArrayOutputStream sharedOut) {
      this(new ByteArrayInputStream(inputBytes), sharedOut, new AtomicBoolean(false));
    }

    StubSslSocket(InputStream in) {
      this(in, new ByteArrayOutputStream(), new AtomicBoolean(false));
    }

    StubSslSocket(InputStream in, ByteArrayOutputStream sharedOut, AtomicBoolean failOnWrite) {
      this.in = in;
      this.internalOut = sharedOut;
      this.failOnWrite = failOnWrite;
      this.out =
          new OutputStream() {
            @Override
            public void write(int b) throws IOException {
              if (failOnWrite.get()) {
                throw new IOException("simulated write failure");
              }
              internalOut.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
              if (b == null) {
                throw new NullPointerException();
              }
              if (off < 0 || len < 0 || off + len > b.length) {
                throw new IndexOutOfBoundsException();
              }
              if (failOnWrite.get()) {
                throw new IOException("simulated write failure");
              }
              internalOut.write(b, off, len);
            }
          };
    }

    byte[] getWrittenBytes() {
      return internalOut.toByteArray();
    }

    @Override
    public InputStream getInputStream() {
      return in;
    }

    @Override
    public OutputStream getOutputStream() {
      return out;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {}

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {}

    @Override
    public void bind(SocketAddress bindpoint) {}

    @Override
    public InetAddress getInetAddress() {
      return null;
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
      return null;
    }

    @Override
    public SocketAddress getLocalSocketAddress() {
      return null;
    }

    @Override
    public SocketChannel getChannel() {
      return null;
    }

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
    public void setSoTimeout(int timeout) {
      recordedSoTimeouts.add(timeout);
    }

    List<Integer> getRecordedSoTimeouts() {
      return recordedSoTimeouts;
    }

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
    public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {}

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
    public SSLSession getSession() {
      return null;
    }

    @Override
    public SSLSession getHandshakeSession() {
      return null;
    }

    @Override
    public void addHandshakeCompletedListener(HandshakeCompletedListener listener) {}

    @Override
    public void removeHandshakeCompletedListener(HandshakeCompletedListener listener) {}

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

    @Override
    public SSLParameters getSSLParameters() {
      return new SSLParameters();
    }

    @Override
    public void setSSLParameters(SSLParameters params) {}
  }
}
