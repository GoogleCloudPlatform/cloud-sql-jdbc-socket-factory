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

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.function.Consumer;

class ResourceExhaustedTrackingSocket extends Socket {
  private final Socket socket;
  private final Consumer<Throwable> onResourceExhausted;
  private final Runnable onSuccess;
  private volatile boolean firstReadDone = false;
  private final InputStream in;
  private final OutputStream out;

  ResourceExhaustedTrackingSocket(
      Socket socket, Consumer<Throwable> onResourceExhausted, Runnable onSuccess) {
    this.socket = socket;
    this.onResourceExhausted = onResourceExhausted;
    this.onSuccess = onSuccess;
    this.in = new TrackingInputStream();
    this.out = new TrackingOutputStream();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return in;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    return out;
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }

  @Override
  public boolean isClosed() {
    return socket.isClosed();
  }

  @Override
  public boolean isConnected() {
    return socket.isConnected();
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    socket.setKeepAlive(on);
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return socket.getKeepAlive();
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    socket.setTcpNoDelay(on);
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return socket.getTcpNoDelay();
  }

  @Override
  public void setSoTimeout(int timeout) throws SocketException {
    socket.setSoTimeout(timeout);
  }

  @Override
  public int getSoTimeout() throws SocketException {
    return socket.getSoTimeout();
  }

  @Override
  public void shutdownInput() throws IOException {
    socket.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    socket.shutdownOutput();
  }

  @Override
  public boolean isInputShutdown() {
    return socket.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return socket.isOutputShutdown();
  }

  @Override
  public java.net.InetAddress getInetAddress() {
    return socket.getInetAddress();
  }

  @Override
  public java.net.InetAddress getLocalAddress() {
    return socket.getLocalAddress();
  }

  @Override
  public int getPort() {
    return socket.getPort();
  }

  @Override
  public int getLocalPort() {
    return socket.getLocalPort();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return socket.getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return socket.getLocalSocketAddress();
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    socket.connect(endpoint);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    socket.connect(endpoint, timeout);
  }

  static boolean isResourceExhausted(Throwable t) {
    while (t != null) {
      if (t instanceof ResourceExhaustedException) {
        return true;
      }
      if (t instanceof StatusRuntimeException) {
        return ((StatusRuntimeException) t).getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
      }
      if (t instanceof StatusException) {
        return ((StatusException) t).getStatus().getCode() == Status.Code.RESOURCE_EXHAUSTED;
      }
      if (t instanceof ApiException) {
        return ((ApiException) t).getStatusCode().getCode() == StatusCode.Code.RESOURCE_EXHAUSTED;
      }
      t = t.getCause();
    }
    return false;
  }

  private class TrackingOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      try {
        socket.getOutputStream().write(b);
      } catch (IOException e) {
        if (isResourceExhausted(e)) {
          onResourceExhausted.accept(e);
        }
        throw e;
      }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      try {
        socket.getOutputStream().write(b, off, len);
      } catch (IOException e) {
        if (isResourceExhausted(e)) {
          onResourceExhausted.accept(e);
        }
        throw e;
      }
    }

    @Override
    public void flush() throws IOException {
      try {
        socket.getOutputStream().flush();
      } catch (IOException e) {
        if (isResourceExhausted(e)) {
          onResourceExhausted.accept(e);
        }
        throw e;
      }
    }

    @Override
    public void close() throws IOException {
      try {
        ResourceExhaustedTrackingSocket.this.close();
      } catch (IOException e) {
        if (isResourceExhausted(e)) {
          onResourceExhausted.accept(e);
        }
        throw e;
      }
    }
  }

  private class TrackingInputStream extends InputStream {
    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      int n = read(b, 0, 1);
      return n == -1 ? -1 : (b[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (b == null) {
        throw new NullPointerException();
      }
      if (off < 0 || len < 0 || len > b.length - off) {
        throw new IndexOutOfBoundsException();
      }
      if (len == 0) {
        return 0;
      }
      try {
        int n = socket.getInputStream().read(b, off, len);
        if (!firstReadDone && (n > 0 || n == -1)) {
          synchronized (ResourceExhaustedTrackingSocket.this) {
            if (!firstReadDone) {
              firstReadDone = true;
              onSuccess.run();
            }
          }
        }
        return n;
      } catch (IOException e) {
        if (isResourceExhausted(e)) {
          onResourceExhausted.accept(e);
        }
        throw e;
      }
    }

    @Override
    public int available() throws IOException {
      return socket.getInputStream().available();
    }

    @Override
    public void close() throws IOException {
      ResourceExhaustedTrackingSocket.this.close();
    }
  }
}
