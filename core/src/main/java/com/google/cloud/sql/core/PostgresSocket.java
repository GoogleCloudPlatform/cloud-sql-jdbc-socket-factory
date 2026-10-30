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
import java.util.function.BiConsumer;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * Wraps an {@link SSLSocket} and handles client-side PostgreSQL database protocol operations.
 * Inspects initial bytes written to {@link #getOutputStream()} to extract the PostgreSQL v3
 * StartupMessage {@code (user, database)} pair for Auto-IAM refresh probes. Once the startup
 * message is processed (or determined not to be a PostgreSQL v3 startup packet), all subsequent
 * writes pass directly through to the underlying stream with zero locking or synchronization
 * overhead.
 */
class PostgresSocket extends SSLSocket {
  static final int MAX_PG_STARTUP_PACKET_LEN = 10000;
  private static final int PG_PROTOCOL_VERSION_30 = 0x00030000;
  private static final int PG_SSL_REQUEST_CODE = 80877103;
  private static final int PG_GSS_ENC_REQUEST_CODE = 80877104;

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
    if (buf == null || buf.length < 8) {
      return new ParsedStartup("", "", false);
    }
    int offset = 0;
    int firstLen = ByteBuffer.wrap(buf, 0, 4).getInt();
    int firstCode = ByteBuffer.wrap(buf, 4, 4).getInt();
    // Skip GSENCRequest (80877104) or SSLRequest (80877103) if prepended
    if (firstLen == 8
        && (firstCode == PG_SSL_REQUEST_CODE || firstCode == PG_GSS_ENC_REQUEST_CODE)) {
      offset = 8;
      if (buf.length < offset + 8) {
        return new ParsedStartup("", "", false);
      }
    }
    int pktLen = ByteBuffer.wrap(buf, offset, 4).getInt();
    int protoVer = ByteBuffer.wrap(buf, offset + 4, 4).getInt();
    if (protoVer != PG_PROTOCOL_VERSION_30 || pktLen < 8 || pktLen > MAX_PG_STARTUP_PACKET_LEN) {
      return new ParsedStartup("", "", true);
    }
    if (buf.length < offset + pktLen) {
      return new ParsedStartup("", "", false);
    }
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
      throw new RuntimeException(e);
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

  static void sendStartupProbe(SSLSocket sslSocket, String user, String database)
      throws IOException {
    if (user == null || user.isEmpty()) {
      return;
    }
    sslSocket.setSoTimeout(5000);
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
      } catch (IOException e) {
        // Peer may have already terminated the connection; ignore
      }
    }
  }

  static class PostgresOutputStream extends OutputStream {
    private final OutputStream delegate;
    private final BiConsumer<String, String> onStartup;
    private ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
    private volatile boolean startupMessageSent = false;

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
      } else if (bytes.length > MAX_PG_STARTUP_PACKET_LEN + 8) {
        startupMessageSent = true;
        buf = null;
      }
    }

    private void inspect(int b) {
      if (startupMessageSent) {
        return;
      }
      buf.write(b);
      byte[] bytes = buf.toByteArray();
      ParsedStartup parsed = parsePostgresStartupPacket(bytes);
      if (parsed.complete) {
        startupMessageSent = true;
        buf = null;
        if (parsed.user != null && !parsed.user.isEmpty()) {
          onStartup.accept(parsed.user, parsed.database);
        }
      } else if (bytes.length > MAX_PG_STARTUP_PACKET_LEN + 8) {
        startupMessageSent = true;
        buf = null;
      }
    }

    @Override
    public void write(int b) throws IOException {
      if (startupMessageSent) {
        delegate.write(b);
        return;
      }
      inspect(b);
      delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      if (startupMessageSent) {
        delegate.write(b);
        return;
      }
      inspect(b, 0, b.length);
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (startupMessageSent) {
        delegate.write(b, off, len);
        return;
      }
      inspect(b, off, len);
      delegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      delegate.flush();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
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
