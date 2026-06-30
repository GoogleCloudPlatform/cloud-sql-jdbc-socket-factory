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

import com.google.cloud.sql.v1beta4.DataPacket;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqlDataSocket extends Socket {
  private static final Logger logger = LoggerFactory.getLogger(SqlDataSocket.class);

  private final StreamObserver<StreamSqlDataRequest> requestObserver;
  private final BlockingQueue<byte[]> readQueue = new LinkedBlockingQueue<>();
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private volatile boolean closed = false;
  private Throwable error = null;

  SqlDataSocket(StreamObserver<StreamSqlDataRequest> requestObserver) {
    this.requestObserver = requestObserver;
    this.inputStream = new SqlDataInputStream();
    this.outputStream = new SqlDataOutputStream();
  }

  // Called by the client's response observer when new data arrives
  void onNextResponse(StreamSqlDataResponse response) {
    if (response.hasData()) {
      byte[] data = response.getData().getData().toByteArray();
      if (data.length > 0) {
        readQueue.add(data);
      }
    } else if (response.hasTerminateSession()) {
      logger.debug("Received TerminateSession from server");
      closeQuietly();
    }
  }

  void onErrorResponse(Throwable t) {
    logger.debug("Received error from server stream", t);
    this.error = t;
    readQueue.add(new byte[0]); // Sentinel to unblock read
    closeQuietly();
  }

  void onCompletedResponse() {
    logger.debug("Server stream completed");
    readQueue.add(new byte[0]); // Sentinel to unblock read
    closeQuietly();
  }

  private void closeQuietly() {
    try {
      close();
    } catch (IOException e) {
      // ignore
    }
  }

  @Override
  public InputStream getInputStream() throws IOException {
    if (closed) {
      throw new IOException("Socket is closed");
    }
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    if (closed) {
      throw new IOException("Socket is closed");
    }
    return outputStream;
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      requestObserver.onCompleted();
    } catch (Exception e) {
      // ignore if already closed or failed
    }
    // Sentinel to unblock any waiting reader
    readQueue.add(new byte[0]);
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
  public void connect(java.net.SocketAddress endpoint) throws IOException {
    // Already connected
  }

  @Override
  public void connect(java.net.SocketAddress endpoint, int timeout) throws IOException {
    // Already connected
  }

  private class SqlDataInputStream extends InputStream {
    private byte[] currentBlock = null;
    private int currentOffset = 0;

    @Override
    public int read() throws IOException {
      byte[] b = new byte[1];
      int n = read(b, 0, 1);
      if (n == -1) {
        return -1;
      }
      return b[0] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (closed) {
        throw new IOException("Stream is closed");
      }
      if (error != null) {
        throw new IOException("Stream failed", error);
      }

      if (currentBlock == null || currentOffset >= currentBlock.length) {
        try {
          currentBlock = readQueue.take();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Read interrupted", e);
        }

        if (closed) {
          throw new IOException("Socket closed during read");
        }
        if (error != null) {
          throw new IOException("Stream failed", error);
        }
        if (currentBlock == null || currentBlock.length == 0) {
          // EOF sentinel (empty byte array)
          return -1;
        }
        currentOffset = 0;
      }

      int remaining = currentBlock.length - currentOffset;
      int toCopy = Math.min(len, remaining);
      System.arraycopy(currentBlock, currentOffset, b, off, toCopy);
      currentOffset += toCopy;
      return toCopy;
    }
  }

  private class SqlDataOutputStream extends OutputStream {
    @Override
    public void write(int b) throws IOException {
      write(new byte[] {(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      if (closed) {
        throw new IOException("Socket is closed");
      }
      if (error != null) {
        throw new IOException("Stream failed", error);
      }

      StreamSqlDataRequest request =
          StreamSqlDataRequest.newBuilder()
              .setData(
                  DataPacket.newBuilder()
                      .setData(com.google.protobuf.ByteString.copyFrom(b, off, len))
                      .build())
              .build();

      try {
        synchronized (requestObserver) {
          requestObserver.onNext(request);
        }
      } catch (Exception e) {
        throw new IOException("Failed to write to stream", e);
      }
    }
  }
}
