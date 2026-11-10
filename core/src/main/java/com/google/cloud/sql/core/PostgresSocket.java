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

import com.google.cloud.sql.AuthType;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps an {@link SSLSocket} and handles client-side PostgreSQL database protocol operations.
 * Inspects initial bytes written to {@link #getOutputStream()} to extract the PostgreSQL v3
 * StartupMessage {@code (user, database)} pair for Auto-IAM refresh probes. Consistent with the
 * {@link OutputStream} contract, callers are responsible for synchronizing any concurrent writes to
 * {@link #getOutputStream()}. Once the startup message is processed (or determined not to be a
 * PostgreSQL v3 startup packet), all subsequent writes pass directly through to the underlying
 * stream with zero locking or synchronization overhead.
 */
class PostgresSocket extends SSLSocket {
  static final int MAX_PG_STARTUP_PACKET_LEN = 10000;
  private static final int PG_PROTOCOL_VERSION_30 = 0x00030000;
  private static final int PG_SSL_REQUEST_CODE = 80877103;
  private static final int PG_GSS_ENC_REQUEST_CODE = 80877104;
  private static final int PROBE_READ_TIMEOUT_MS = 5000;
  private static final int PROBE_EOF_DRAIN_TIMEOUT_MS = 500;
  private static final int PROBE_EOF_MAX_DRAIN_READS = 16;
  private static final Logger logger = LoggerFactory.getLogger(PostgresSocket.class);

  @FunctionalInterface
  interface ProbeDialer {
    SSLSocket dial(ConnectionMetadata metadata, int timeoutMs) throws IOException;
  }

  static final class IamPrincipal {
    final String user;
    final String database;

    IamPrincipal(String user, String database) {
      this.user = user != null ? user : "";
      this.database = (database == null || database.isEmpty()) ? this.user : database;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof IamPrincipal)) {
        return false;
      }
      IamPrincipal that = (IamPrincipal) o;
      return Objects.equals(user, that.user) && Objects.equals(database, that.database);
    }

    @Override
    public int hashCode() {
      return Objects.hash(user, database);
    }
  }

  private final SSLSocket delegate;
  private final OutputStream out;

  PostgresSocket(SSLSocket delegate, BiConsumer<String, String> onStartup) throws IOException {
    this.delegate = delegate;
    this.out = new PostgresOutputStream(delegate.getOutputStream(), onStartup);
  }

  static final class ParsedStartup {
    final String user;
    final String database;
    final boolean complete;

    ParsedStartup(String user, String database, boolean complete) {
      this.user = user;
      this.database = database;
      this.complete = complete;
    }
  }

  static ParsedStartup parsePostgresStartupPacket(byte[] buf) {
    // Step 1: Wait for at least 8 bytes (4-byte packet length + 4-byte protocol/request code).
    if (buf == null || buf.length < 8) {
      return new ParsedStartup("", "", false);
    }
    int offset = 0;
    // Step 2: Skip GSSENCRequest (80877104) or SSLRequest (80877103) packets if prepended.
    // Clients like libpq / pgx may send GSSENCRequest followed by SSLRequest before StartupMessage.
    while (buf.length >= offset + 8) {
      int len = ByteBuffer.wrap(buf, offset, 4).getInt();
      int code = ByteBuffer.wrap(buf, offset + 4, 4).getInt();
      if (len == 8 && (code == PG_SSL_REQUEST_CODE || code == PG_GSS_ENC_REQUEST_CODE)) {
        offset += 8;
        continue;
      }
      break;
    }
    if (buf.length < offset + 8) {
      return new ParsedStartup("", "", false);
    }
    int pktLen = ByteBuffer.wrap(buf, offset, 4).getInt();
    int protoVer = ByteBuffer.wrap(buf, offset + 4, 4).getInt();
    // Step 3: Validate protocol version (v3.0) and packet length bounds.
    if (protoVer != PG_PROTOCOL_VERSION_30 || pktLen < 8 || pktLen > MAX_PG_STARTUP_PACKET_LEN) {
      return new ParsedStartup("", "", true);
    }
    // Step 4: Wait until the complete packet body has arrived.
    if (buf.length < offset + pktLen) {
      return new ParsedStartup("", "", false);
    }
    // Step 5: Decode null-terminated key-value pairs (user, database) from the StartupMessage.
    int pos = offset + 8;
    int end = offset + pktLen;
    String user = "";
    String database = "";
    while (pos < end) {
      int keyEnd = findNullByte(buf, pos, end);
      if (keyEnd < 0 || keyEnd == pos) {
        break;
      }
      final String key = new String(buf, pos, keyEnd - pos, StandardCharsets.UTF_8);
      pos = keyEnd + 1;
      if (pos >= end) {
        break;
      }
      int valEnd = findNullByte(buf, pos, end);
      if (valEnd < 0) {
        break;
      }
      final String val = new String(buf, pos, valEnd - pos, StandardCharsets.UTF_8);
      pos = valEnd + 1;
      if ("user".equals(key)) {
        user = val;
      } else if ("database".equals(key)) {
        database = val;
      }
    }
    if (!user.isEmpty() && database.isEmpty()) {
      database = user;
    }
    return new ParsedStartup(user, database, true);
  }

  private static int findNullByte(byte[] buf, int start, int end) {
    for (int i = start; i < end; i++) {
      if (buf[i] == 0) {
        return i;
      }
    }
    return -1;
  }

  static byte[] buildPostgresStartupPacket(String user, String database) {
    String dbName = (database == null || database.isEmpty()) ? user : database;
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    try {
      payload.write("user".getBytes(StandardCharsets.UTF_8));
      payload.write(0);
      payload.write(user.getBytes(StandardCharsets.UTF_8));
      payload.write(0);
      payload.write("database".getBytes(StandardCharsets.UTF_8));
      payload.write(0);
      payload.write(dbName.getBytes(StandardCharsets.UTF_8));
      payload.write(0);
      payload.write(0);
    } catch (IOException e) {
      throw new AssertionError("ByteArrayOutputStream cannot throw IOException", e);
    }
    byte[] body = payload.toByteArray();
    int totalLen = 8 + body.length;
    ByteBuffer packet = ByteBuffer.allocate(totalLen);
    packet.putInt(totalLen);
    packet.putInt(PG_PROTOCOL_VERSION_30);
    packet.put(body);
    return packet.array();
  }

  static byte[] buildTerminatePacket() {
    return new byte[] {'X', 0, 0, 0, 4};
  }

  static void probeConnection(
      ConnectionConfig config,
      CloudSqlInstanceName instanceName,
      ConnectionInfo info,
      ProbeDialer probeDialer,
      Set<IamPrincipal> principals) {
    probeConnection(
        config,
        instanceName,
        info,
        probeDialer,
        principals,
        Math.toIntExact(InternalConnectorRegistry.DEFAULT_CONNECT_TIMEOUT_MS));
  }

  static void probeConnection(
      ConnectionConfig config,
      CloudSqlInstanceName instanceName,
      ConnectionInfo info,
      ProbeDialer probeDialer,
      Set<IamPrincipal> principals,
      int probeTimeoutMs) {
    if (probeDialer == null
        || info == null
        || config.getAuthType() != AuthType.IAM
        || !info.isPostgres()
        || principals == null
        || principals.isEmpty()
        || probeTimeoutMs <= 0) {
      return;
    }
    try {
      final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(probeTimeoutMs);
      ConnectionMetadata metadata = info.toConnectionMetadata(config, instanceName);
      List<IamPrincipal> snapshot = new ArrayList<>(principals);
      for (IamPrincipal principal : snapshot) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
          break;
        }
        int dialTimeoutMs =
            (int)
                Math.min(
                    Integer.MAX_VALUE, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
        try (SSLSocket sslSocket = probeDialer.dial(metadata, dialTimeoutMs)) {
          long postDialRemainingNanos = deadlineNanos - System.nanoTime();
          if (postDialRemainingNanos <= 0) {
            break;
          }
          int readBudgetMs =
              (int)
                  Math.min(
                      Integer.MAX_VALUE,
                      Math.max(1L, TimeUnit.NANOSECONDS.toMillis(postDialRemainingNanos)));
          sendStartupProbe(sslSocket, principal.user, principal.database, readBudgetMs);
        } catch (IOException e) {
          logger.debug(
              String.format(
                  "[%s] Failed to send proactive IAM startup probe for user [%s]",
                  instanceName.getConnectionName(), principal.user),
              e);
        }
      }
    } catch (RuntimeException e) {
      logger.debug(
          String.format(
              "[%s] Failed to prepare proactive IAM refresh probe",
              instanceName.getConnectionName()),
          e);
    }
  }

  static void sendStartupProbe(SSLSocket sslSocket, String user, String database)
      throws IOException {
    sendStartupProbe(sslSocket, user, database, PROBE_READ_TIMEOUT_MS);
  }

  static void sendStartupProbe(SSLSocket sslSocket, String user, String database, int maxTimeoutMs)
      throws IOException {
    if (user == null || user.isEmpty() || maxTimeoutMs <= 0) {
      return;
    }
    final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxTimeoutMs);
    sslSocket.setSoTimeout(Math.min(PROBE_READ_TIMEOUT_MS, maxTimeoutMs));
    OutputStream out = sslSocket.getOutputStream();
    InputStream in = sslSocket.getInputStream();
    out.write(buildPostgresStartupPacket(user, database));
    out.flush();
    byte[] resp = new byte[1024];
    int bytesRead = in.read(resp);
    if (bytesRead != -1) {
      try {
        out.write(buildTerminatePacket());
        out.flush();
        // Drain server response until EOF or a brief wall-clock timeout to ensure clean teardown
        long remainingAfterReadNanos = deadlineNanos - System.nanoTime();
        if (remainingAfterReadNanos <= 0) {
          return;
        }
        long drainBudgetNanos =
            Math.min(
                TimeUnit.MILLISECONDS.toNanos(PROBE_EOF_DRAIN_TIMEOUT_MS), remainingAfterReadNanos);
        long drainDeadlineNanos = System.nanoTime() + drainBudgetNanos;
        int drainReads = 0;
        while (drainReads < PROBE_EOF_MAX_DRAIN_READS) {
          long remDrainNanos = drainDeadlineNanos - System.nanoTime();
          if (remDrainNanos <= 0) {
            break;
          }
          int remDrainMs =
              (int)
                  Math.min(
                      Integer.MAX_VALUE,
                      Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remDrainNanos)));
          sslSocket.setSoTimeout(remDrainMs);
          if (in.read(resp) == -1) {
            break;
          }
          drainReads++;
        }
      } catch (IOException e) {
        // Peer may have already terminated the connection or drain timed out; ignore
      }
    }
  }

  static class PostgresOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final BiConsumer<String, String> onStartup;
    private ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
    private boolean startupMessageSent = false;

    PostgresOutputStream(OutputStream delegate, BiConsumer<String, String> onStartup) {
      this.delegate = delegate;
      this.onStartup = onStartup;
    }

    private void inspect(byte[] b, int off, int len) {
      if (startupMessageSent || len <= 0) {
        return;
      }
      buf.write(b, off, len);
      byte[] bytes = buf.toByteArray();
      ParsedStartup parsed = parsePostgresStartupPacket(bytes);
      if (parsed.complete) {
        startupMessageSent = true;
        buf = null;
        if (parsed.user != null && !parsed.user.isEmpty()) {
          onStartup.accept(parsed.user, parsed.database);
        }
      } else if (bytes.length > MAX_PG_STARTUP_PACKET_LEN + 16) {
        startupMessageSent = true;
        buf = null;
      }
    }

    private void inspect(int b) {
      inspect(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(int b) throws IOException {
      if (startupMessageSent) {
        delegate.write(b);
        return;
      }
      delegate.write(b);
      inspect(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      if (startupMessageSent) {
        delegate.write(b);
        return;
      }
      delegate.write(b);
      inspect(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (startupMessageSent) {
        delegate.write(b, off, len);
        return;
      }
      delegate.write(b, off, len);
      inspect(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      try {
        delegate.close();
      } finally {
        startupMessageSent = true;
        buf = null;
      }
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return delegate.getInputStream();
  }

  @Override
  public OutputStream getOutputStream() {
    return out;
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    delegate.connect(endpoint);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    delegate.connect(endpoint, timeout);
  }

  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    delegate.bind(bindpoint);
  }

  @Override
  public InetAddress getInetAddress() {
    return delegate.getInetAddress();
  }

  @Override
  public InetAddress getLocalAddress() {
    return delegate.getLocalAddress();
  }

  @Override
  public int getPort() {
    return delegate.getPort();
  }

  @Override
  public int getLocalPort() {
    return delegate.getLocalPort();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return delegate.getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return delegate.getLocalSocketAddress();
  }

  @Override
  public SocketChannel getChannel() {
    return delegate.getChannel();
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    delegate.setTcpNoDelay(on);
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return delegate.getTcpNoDelay();
  }

  @Override
  public void setSoLinger(boolean on, int linger) throws SocketException {
    delegate.setSoLinger(on, linger);
  }

  @Override
  public int getSoLinger() throws SocketException {
    return delegate.getSoLinger();
  }

  @Override
  public void sendUrgentData(int data) throws IOException {
    delegate.sendUrgentData(data);
  }

  @Override
  public void setOOBInline(boolean on) throws SocketException {
    delegate.setOOBInline(on);
  }

  @Override
  public boolean getOOBInline() throws SocketException {
    return delegate.getOOBInline();
  }

  @Override
  public synchronized void setSoTimeout(int timeout) throws SocketException {
    delegate.setSoTimeout(timeout);
  }

  @Override
  public synchronized int getSoTimeout() throws SocketException {
    return delegate.getSoTimeout();
  }

  @Override
  public synchronized void setSendBufferSize(int size) throws SocketException {
    delegate.setSendBufferSize(size);
  }

  @Override
  public synchronized int getSendBufferSize() throws SocketException {
    return delegate.getSendBufferSize();
  }

  @Override
  public synchronized void setReceiveBufferSize(int size) throws SocketException {
    delegate.setReceiveBufferSize(size);
  }

  @Override
  public synchronized int getReceiveBufferSize() throws SocketException {
    return delegate.getReceiveBufferSize();
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    delegate.setKeepAlive(on);
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return delegate.getKeepAlive();
  }

  @Override
  public void setTrafficClass(int tc) throws SocketException {
    delegate.setTrafficClass(tc);
  }

  @Override
  public int getTrafficClass() throws SocketException {
    return delegate.getTrafficClass();
  }

  @Override
  public void setReuseAddress(boolean on) throws SocketException {
    delegate.setReuseAddress(on);
  }

  @Override
  public boolean getReuseAddress() throws SocketException {
    return delegate.getReuseAddress();
  }

  @Override
  public synchronized void close() throws IOException {
    delegate.close();
  }

  @Override
  public void shutdownInput() throws IOException {
    delegate.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    delegate.shutdownOutput();
  }

  @Override
  public String toString() {
    return delegate.toString();
  }

  @Override
  public boolean isConnected() {
    return delegate.isConnected();
  }

  @Override
  public boolean isBound() {
    return delegate.isBound();
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public boolean isInputShutdown() {
    return delegate.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return delegate.isOutputShutdown();
  }

  @Override
  public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    delegate.setPerformancePreferences(connectionTime, latency, bandwidth);
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return delegate.getSupportedCipherSuites();
  }

  @Override
  public String[] getEnabledCipherSuites() {
    return delegate.getEnabledCipherSuites();
  }

  @Override
  public void setEnabledCipherSuites(String[] strings) {
    delegate.setEnabledCipherSuites(strings);
  }

  @Override
  public String[] getSupportedProtocols() {
    return delegate.getSupportedProtocols();
  }

  @Override
  public String[] getEnabledProtocols() {
    return delegate.getEnabledProtocols();
  }

  @Override
  public void setEnabledProtocols(String[] strings) {
    delegate.setEnabledProtocols(strings);
  }

  @Override
  public SSLSession getSession() {
    return delegate.getSession();
  }

  @Override
  public SSLSession getHandshakeSession() {
    return delegate.getHandshakeSession();
  }

  @Override
  public void addHandshakeCompletedListener(HandshakeCompletedListener handshakeCompletedListener) {
    delegate.addHandshakeCompletedListener(handshakeCompletedListener);
  }

  @Override
  public void removeHandshakeCompletedListener(
      HandshakeCompletedListener handshakeCompletedListener) {
    delegate.removeHandshakeCompletedListener(handshakeCompletedListener);
  }

  @Override
  public void startHandshake() throws IOException {
    delegate.startHandshake();
  }

  @Override
  public void setUseClientMode(boolean b) {
    delegate.setUseClientMode(b);
  }

  @Override
  public boolean getUseClientMode() {
    return delegate.getUseClientMode();
  }

  @Override
  public void setNeedClientAuth(boolean b) {
    delegate.setNeedClientAuth(b);
  }

  @Override
  public boolean getNeedClientAuth() {
    return delegate.getNeedClientAuth();
  }

  @Override
  public void setWantClientAuth(boolean b) {
    delegate.setWantClientAuth(b);
  }

  @Override
  public boolean getWantClientAuth() {
    return delegate.getWantClientAuth();
  }

  @Override
  public void setEnableSessionCreation(boolean b) {
    delegate.setEnableSessionCreation(b);
  }

  @Override
  public boolean getEnableSessionCreation() {
    return delegate.getEnableSessionCreation();
  }

  @Override
  public SSLParameters getSSLParameters() {
    return delegate.getSSLParameters();
  }

  @Override
  public void setSSLParameters(SSLParameters params) {
    delegate.setSSLParameters(params);
  }
}
