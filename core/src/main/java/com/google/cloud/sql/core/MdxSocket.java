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

import com.google.cloud.sql.core.mdx.MetadataExchange;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HandshakeCompletedListener;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

/**
 * MdxSocket delegates to an SSLSocket and filters the InputStream and OutputStream to handle the
 * Metadata Exchange Protocol. This will write the MDX Request when the first OutputStream.write()
 * operation occurs, before writing the bytes to the stream. It will detect the MDX response on the
 * first InputStream.read() operation.
 */
class MdxSocket extends SSLSocket {
  private final ProtocolHandler protocolHandler;
  private final SSLSocket delegate;
  private final MdxInputStream in;
  private final MdxOutputStream out;
  private final AtomicBoolean firstWriteAttempted = new AtomicBoolean(false);
  private final AtomicBoolean firstReadAttempted = new AtomicBoolean(false);
  private final AtomicReference<MetadataExchange.MetadataExchangeResponse> response =
      new AtomicReference();
  private final MetadataExchange.MetadataExchangeRequest.ClientProtocolType clientProtocolType;

  MdxSocket(
      ProtocolHandler protocolHandler,
      SSLSocket delegate,
      MetadataExchange.MetadataExchangeRequest.ClientProtocolType clientProtocolType)
      throws IOException {
    this.protocolHandler = protocolHandler;
    this.delegate = delegate;
    this.in = new MdxInputStream(new BufferedInputStream(delegate.getInputStream()));
    this.out = new MdxOutputStream(delegate.getOutputStream());
    this.clientProtocolType = clientProtocolType;
  }

  void sendMdxIfFirstWrite() throws IOException {
    if (firstWriteAttempted.compareAndSet(false, true)) {
      protocolHandler.sendMdx(out, this.clientProtocolType);
    }
  }

  void readMdxIfFirstRead() throws IOException {
    if (firstReadAttempted.compareAndSet(false, true)) {
      MetadataExchange.MetadataExchangeResponse res = protocolHandler.readMdxResponse(in);
      response.set(res);
    }
  }

  class MdxOutputStream extends OutputStream {
    private final OutputStream delegate;

    MdxOutputStream(OutputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
      sendMdxIfFirstWrite();
      delegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      sendMdxIfFirstWrite();
      delegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      sendMdxIfFirstWrite();
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

  class MdxInputStream extends InputStream {
    private final InputStream delegate;

    MdxInputStream(InputStream delegate) {
      this.delegate = delegate;
    }

    @Override
    public int read() throws IOException {
      readMdxIfFirstRead();
      return delegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      readMdxIfFirstRead();
      return delegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      readMdxIfFirstRead();
      return delegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return delegate.skip(n);
    }

    @Override
    public int available() throws IOException {
      return delegate.available();
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
      delegate.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      delegate.reset();
    }

    @Override
    public boolean markSupported() {
      return delegate.markSupported();
    }
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
