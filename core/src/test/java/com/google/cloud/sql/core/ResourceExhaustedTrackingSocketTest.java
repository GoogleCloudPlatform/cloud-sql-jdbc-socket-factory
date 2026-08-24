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

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ResourceExhaustedTrackingSocketTest {

  @Test
  public void testNormalOperation_CallsOnSuccessOnFirstRead() throws Exception {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MockSocket mockSocket = new MockSocket(in, out);

    AtomicBoolean successCalled = new AtomicBoolean(false);
    AtomicReference<Throwable> exhaustedErr = new AtomicReference<>();

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(
            mockSocket, exhaustedErr::set, () -> successCalled.set(true));

    trackingSocket.getOutputStream().write(new byte[] {10, 20});
    assertThat(successCalled.get()).isFalse();

    byte[] buf = new byte[3];
    int n = trackingSocket.getInputStream().read(buf);
    assertThat(n).isEqualTo(3);
    assertThat(successCalled.get()).isTrue();
    assertThat(exhaustedErr.get()).isNull();

    trackingSocket.close();
    assertThat(mockSocket.isClosed()).isTrue();
  }

  @Test
  public void testReadResourceExhausted_CallsOnResourceExhausted() {
    InputStream in =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException(
                "busy",
                new StatusRuntimeException(
                    Status.RESOURCE_EXHAUSTED.withDescription("rate limit")));
          }
        };
    MockSocket mockSocket = new MockSocket(in, new ByteArrayOutputStream());

    AtomicBoolean successCalled = new AtomicBoolean(false);
    AtomicReference<Throwable> exhaustedErr = new AtomicReference<>();

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(
            mockSocket, exhaustedErr::set, () -> successCalled.set(true));

    IOException ex = assertThrows(IOException.class, () -> trackingSocket.getInputStream().read());
    assertThat(exhaustedErr.get()).isSameInstanceAs(ex);
    assertThat(successCalled.get()).isFalse();
  }

  @Test
  public void testWriteResourceExhausted_CallsOnResourceExhausted() {
    OutputStream out =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            throw new IOException(
                "busy",
                new StatusRuntimeException(
                    Status.RESOURCE_EXHAUSTED.withDescription("write rate limit")));
          }
        };
    MockSocket mockSocket = new MockSocket(new ByteArrayInputStream(new byte[0]), out);

    AtomicBoolean successCalled = new AtomicBoolean(false);
    AtomicReference<Throwable> exhaustedErr = new AtomicReference<>();

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(
            mockSocket, exhaustedErr::set, () -> successCalled.set(true));

    IOException ex =
        assertThrows(IOException.class, () -> trackingSocket.getOutputStream().write(42));
    assertThat(exhaustedErr.get()).isSameInstanceAs(ex);
    assertThat(successCalled.get()).isFalse();
  }

  @Test
  public void testOtherErrors_DoNotTriggerResourceExhausted() {
    InputStream in =
        new InputStream() {
          @Override
          public int read() throws IOException {
            throw new IOException(
                "generic IO error",
                new StatusRuntimeException(Status.INTERNAL.withDescription("internal")));
          }
        };
    MockSocket mockSocket = new MockSocket(in, new ByteArrayOutputStream());

    AtomicBoolean successCalled = new AtomicBoolean(false);
    AtomicReference<Throwable> exhaustedErr = new AtomicReference<>();

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(
            mockSocket, exhaustedErr::set, () -> successCalled.set(true));

    assertThrows(IOException.class, () -> trackingSocket.getInputStream().read());
    assertThat(exhaustedErr.get()).isNull();
    assertThat(successCalled.get()).isFalse();
  }

  @Test
  public void testStreamClose_ClosesSocket() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MockSocket mockSocket = new MockSocket(in, out);

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(mockSocket, t -> {}, () -> {});

    trackingSocket.getInputStream().close();
    assertThat(mockSocket.isClosed()).isTrue();

    MockSocket mockSocket2 = new MockSocket(in, out);
    ResourceExhaustedTrackingSocket trackingSocket2 =
        new ResourceExhaustedTrackingSocket(mockSocket2, t -> {}, () -> {});

    trackingSocket2.getOutputStream().close();
    assertThat(mockSocket2.isClosed()).isTrue();
  }

  @Test
  public void testStreamAvailable_Delegates() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3, 4});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MockSocket mockSocket = new MockSocket(in, out);

    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(mockSocket, t -> {}, () -> {});

    assertThat(trackingSocket.getInputStream().available()).isEqualTo(4);
  }

  @Test
  public void testResourceExhaustedException_Matches() {
    ResourceExhaustedException ex = new ResourceExhaustedException("busy");
    assertThat(ResourceExhaustedTrackingSocket.isResourceExhausted(ex)).isTrue();
  }

  @Test
  public void testZeroLengthRead_DoesNotTriggerSuccess() throws IOException {
    ByteArrayInputStream in = new ByteArrayInputStream(new byte[] {1, 2, 3});
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    MockSocket mockSocket = new MockSocket(in, out);

    AtomicBoolean successCalled = new AtomicBoolean(false);
    ResourceExhaustedTrackingSocket trackingSocket =
        new ResourceExhaustedTrackingSocket(mockSocket, t -> {}, () -> successCalled.set(true));

    int n = trackingSocket.getInputStream().read(new byte[0]);
    assertThat(n).isEqualTo(0);
    assertThat(successCalled.get()).isFalse();

    n = trackingSocket.getInputStream().read(new byte[1]);
    assertThat(n).isEqualTo(1);
    assertThat(successCalled.get()).isTrue();
  }

  private static class MockSocket extends Socket {
    private final InputStream in;
    private final OutputStream out;
    private boolean closed = false;

    MockSocket(InputStream in, OutputStream out) {
      this.in = in;
      this.out = out;
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
    public synchronized void close() {
      closed = true;
    }

    @Override
    public boolean isClosed() {
      return closed;
    }
  }
}
