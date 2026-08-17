/*
 * Copyright 2026 Google LLC
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

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

class FallbackSocket extends Socket {
  @FunctionalInterface
  interface FallbackSupplier {
    Socket get() throws IOException;
  }

  private final Socket sqlDataSocket;
  private final FallbackSupplier fallbackSupplier;
  private final Runnable onFallback;
  private final ByteArrayOutputStream initialWrites = new ByteArrayOutputStream();
  private volatile Socket directSocket = null;
  private volatile boolean firstReadDone = false;
  private final InputStream in;
  private final OutputStream out;

  FallbackSocket(Socket sqlDataSocket, FallbackSupplier fallbackSupplier, Runnable onFallback) {
    this.sqlDataSocket = sqlDataSocket;
    this.fallbackSupplier = fallbackSupplier;
    this.onFallback = onFallback;
    this.in = new FallbackInputStream();
    this.out = new FallbackOutputStream();
  }

  private Socket getActiveSocket() {
    Socket direct = directSocket;
    return direct != null ? direct : sqlDataSocket;
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
  public synchronized void close() throws IOException {
    try {
      sqlDataSocket.close();
    } finally {
      if (directSocket != null) {
        directSocket.close();
      }
    }
  }

  @Override
  public boolean isClosed() {
    return getActiveSocket().isClosed();
  }

  @Override
  public boolean isConnected() {
    return getActiveSocket().isConnected();
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    getActiveSocket().setKeepAlive(on);
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    return getActiveSocket().getKeepAlive();
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    getActiveSocket().setTcpNoDelay(on);
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    return getActiveSocket().getTcpNoDelay();
  }

  @Override
  public void setSoTimeout(int timeout) throws SocketException {
    getActiveSocket().setSoTimeout(timeout);
  }

  @Override
  public int getSoTimeout() throws SocketException {
    return getActiveSocket().getSoTimeout();
  }

  @Override
  public void shutdownInput() throws IOException {
    getActiveSocket().shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    getActiveSocket().shutdownOutput();
  }

  @Override
  public boolean isInputShutdown() {
    return getActiveSocket().isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    return getActiveSocket().isOutputShutdown();
  }

  @Override
  public java.net.InetAddress getInetAddress() {
    return getActiveSocket().getInetAddress();
  }

  @Override
  public java.net.InetAddress getLocalAddress() {
    return getActiveSocket().getLocalAddress();
  }

  @Override
  public int getPort() {
    return getActiveSocket().getPort();
  }

  @Override
  public int getLocalPort() {
    return getActiveSocket().getLocalPort();
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    return getActiveSocket().getRemoteSocketAddress();
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    return getActiveSocket().getLocalSocketAddress();
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    getActiveSocket().connect(endpoint);
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
    getActiveSocket().connect(endpoint, timeout);
  }

  private static boolean isPreconditionFailed(Throwable t) {
    while (t != null) {
      if (t instanceof StatusRuntimeException) {
        return ((StatusRuntimeException) t).getStatus().getCode()
            == Status.Code.FAILED_PRECONDITION;
      }
      if (t instanceof StatusException) {
        return ((StatusException) t).getStatus().getCode() == Status.Code.FAILED_PRECONDITION;
      }
      if (t instanceof ApiException) {
        return ((ApiException) t).getStatusCode().getCode() == StatusCode.Code.FAILED_PRECONDITION;
      }
      t = t.getCause();
    }
    return false;
  }

  private class FallbackOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (b == null) {
        throw new NullPointerException();
      }
      if (off < 0 || len < 0 || len > b.length - off) {
        throw new IndexOutOfBoundsException();
      }
      if (len == 0) {
        return;
      }
      synchronized (FallbackSocket.this) {
        if (directSocket != null) {
          directSocket.getOutputStream().write(b, off, len);
          return;
        }
        if (!firstReadDone) {
          initialWrites.write(b, off, len);
        }
        sqlDataSocket.getOutputStream().write(b, off, len);
      }
    }

    @Override
    public void flush() throws IOException {
      synchronized (FallbackSocket.this) {
        getActiveSocket().getOutputStream().flush();
      }
    }
  }

  private class FallbackInputStream extends InputStream {
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

      if (directSocket != null) {
        return directSocket.getInputStream().read(b, off, len);
      }

      try {
        int n = sqlDataSocket.getInputStream().read(b, off, len);
        synchronized (FallbackSocket.this) {
          firstReadDone = true;
          initialWrites.reset();
        }
        return n;
      } catch (IOException e) {
        Socket targetSocket;
        synchronized (FallbackSocket.this) {
          if (!firstReadDone && isPreconditionFailed(e)) {
            onFallback.run();
            try {
              sqlDataSocket.close();
            } catch (IOException ignored) {
              // ignore
            }

            Socket fallback = fallbackSupplier.get();
            byte[] buffered = initialWrites.toByteArray();
            initialWrites.reset();
            if (buffered.length > 0) {
              fallback.getOutputStream().write(buffered);
              fallback.getOutputStream().flush();
            }
            directSocket = fallback;
            firstReadDone = true;
            targetSocket = fallback;
          } else {
            throw e;
          }
        }
        return targetSocket.getInputStream().read(b, off, len);
      }
    }
  }
}
