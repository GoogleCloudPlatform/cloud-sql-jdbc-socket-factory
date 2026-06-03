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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FallbackSocketTest {

  static class MockSocket extends Socket {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    InputStream inStream;
    boolean closed = false;
    boolean keepAlive = false;
    boolean tcpNoDelay = false;
    int soTimeout = 0;
    boolean connected = false;

    MockSocket(byte[] inputData) {
      this.inStream = new ByteArrayInputStream(inputData);
    }

    MockSocket(InputStream inStream) {
      this.inStream = inStream;
    }

    @Override
    public InputStream getInputStream() {
      return inStream;
    }

    @Override
    public OutputStream getOutputStream() {
      return outStream;
    }

    @Override
    public synchronized void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }

    @Override
    public boolean isConnected() {
      return !closed;
    }

    @Override
    public void setKeepAlive(boolean on) {
      this.keepAlive = on;
    }

    @Override
    public void setTcpNoDelay(boolean on) {
      this.tcpNoDelay = on;
    }

    @Override
    public void setSoTimeout(int timeout) {
      this.soTimeout = timeout;
    }

    @Override
    public void connect(java.net.SocketAddress endpoint) {
      this.connected = true;
    }

    @Override
    public void connect(java.net.SocketAddress endpoint, int timeout) {
      this.connected = true;
    }
  }

  @Test
  public void testNormalOperationWithoutFallback() throws Exception {
    MockSocket mockSqlData = new MockSocket("Server Response".getBytes(UTF_8));
    AtomicBoolean fallbackCalled = new AtomicBoolean(false);

    FallbackSocket socket =
        new FallbackSocket(
            mockSqlData,
            () -> {
              throw new AssertionError("Fallback should not be called");
            },
            () -> fallbackCalled.set(true));

    socket.setKeepAlive(true);
    socket.setTcpNoDelay(true);
    socket.setSoTimeout(1000);
    socket.connect(new InetSocketAddress("127.0.0.1", 1234));
    socket.connect(new InetSocketAddress("127.0.0.1", 1234), 500);

    assertThat(socket.isClosed()).isFalse();
    assertThat(socket.isConnected()).isTrue();

    // Write single byte and byte array
    socket.getOutputStream().write(65);
    socket.getOutputStream().write("BCDE".getBytes(UTF_8));
    socket.getOutputStream().flush();

    assertThat(mockSqlData.outStream.toByteArray()).isEqualTo("ABCDE".getBytes(UTF_8));

    // Read single byte
    int firstByte = socket.getInputStream().read();
    assertThat(firstByte).isEqualTo('S');

    // Read byte array
    byte[] buf = new byte[100];
    int readLen = socket.getInputStream().read(buf);
    assertThat(readLen).isEqualTo("erver Response".getBytes(UTF_8).length);
    assertThat(new String(buf, 0, readLen, UTF_8)).isEqualTo("erver Response");

    assertThat(fallbackCalled.get()).isFalse();

    socket.close();
    assertThat(socket.isClosed()).isTrue();
  }

  @Test
  public void testFallbackOnFailedPrecondition() throws Exception {
    InputStream failingIn =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException(
                "Failed to read from SQL Data Service",
                new StatusRuntimeException(Status.FAILED_PRECONDITION));
          }
        };
    MockSocket mockSqlData = new MockSocket(failingIn);
    MockSocket mockDirect = new MockSocket("Direct Response".getBytes(UTF_8));
    AtomicBoolean fallbackCalled = new AtomicBoolean(false);

    FallbackSocket socket =
        new FallbackSocket(mockSqlData, () -> mockDirect, () -> fallbackCalled.set(true));

    // Write initial bytes before read (should be buffered and replayed to direct socket)
    socket.getOutputStream().write("Client Handshake".getBytes(UTF_8));
    socket.getOutputStream().flush();

    assertThat(mockSqlData.outStream.toByteArray()).isEqualTo("Client Handshake".getBytes(UTF_8));
    assertThat(mockDirect.outStream.toByteArray()).isEmpty();

    // Read triggers fallback
    byte[] buf = new byte[100];
    int readLen = socket.getInputStream().read(buf);
    assertThat(readLen).isEqualTo("Direct Response".getBytes(UTF_8).length);
    assertThat(new String(buf, 0, readLen, UTF_8)).isEqualTo("Direct Response");

    assertThat(fallbackCalled.get()).isTrue();
    assertThat(mockSqlData.isClosed()).isTrue();
    assertThat(mockDirect.outStream.toByteArray()).isEqualTo("Client Handshake".getBytes(UTF_8));

    // Subsequent writes go directly to direct socket
    socket.getOutputStream().write(" Next Query".getBytes(UTF_8));
    assertThat(mockDirect.outStream.toByteArray())
        .isEqualTo("Client Handshake Next Query".getBytes(UTF_8));

    socket.close();
    assertThat(mockDirect.isClosed()).isTrue();
  }

  @Test
  public void testNoFallbackOnInternalError() {
    InputStream failingIn =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException(
                "Failed to read from SQL Data Service",
                new StatusRuntimeException(Status.INTERNAL));
          }
        };
    MockSocket mockSqlData = new MockSocket(failingIn);
    AtomicBoolean fallbackCalled = new AtomicBoolean(false);

    FallbackSocket socket =
        new FallbackSocket(
            mockSqlData,
            () -> {
              throw new AssertionError("Fallback should not be called");
            },
            () -> fallbackCalled.set(true));

    IOException ex = assertThrows(IOException.class, () -> socket.getInputStream().read());
    assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
    assertThat(((StatusRuntimeException) ex.getCause()).getStatus().getCode())
        .isEqualTo(Status.Code.INTERNAL);
    assertThat(fallbackCalled.get()).isFalse();
  }
}
