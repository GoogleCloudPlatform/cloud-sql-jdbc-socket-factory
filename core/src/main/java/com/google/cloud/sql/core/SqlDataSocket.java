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

import com.google.api.gax.rpc.BidiStream;
import com.google.cloud.sql.v1beta4.DataPacket;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import io.grpc.StatusException;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqlDataSocket extends Socket {
  private static final Logger logger = LoggerFactory.getLogger(SqlDataSocket.class);
  static final int DEFAULT_QUEUE_CAPACITY = 256;

  private final BidiStream<StreamSqlDataRequest, StreamSqlDataResponse> bidiStream;
  private final BlockingQueue<byte[]> readQueue;
  private final InputStream inputStream;
  private final OutputStream outputStream;
  private volatile boolean closed = false;
  private volatile Throwable error = null;
  private volatile int soTimeout = 0;
  private boolean keepAlive = false;
  private boolean tcpNoDelay = false;

  SqlDataSocket(BidiStream<StreamSqlDataRequest, StreamSqlDataResponse> bidiStream) {
    this(bidiStream, 0, DEFAULT_QUEUE_CAPACITY);
  }

  SqlDataSocket(
      BidiStream<StreamSqlDataRequest, StreamSqlDataResponse> bidiStream, int connectTimeoutMs) {
    this(bidiStream, connectTimeoutMs, DEFAULT_QUEUE_CAPACITY);
  }

  SqlDataSocket(
      BidiStream<StreamSqlDataRequest, StreamSqlDataResponse> bidiStream,
      int connectTimeoutMs,
      int queueCapacity) {
    this.bidiStream = bidiStream;
    this.soTimeout = Math.max(0, connectTimeoutMs);
    this.readQueue = new LinkedBlockingQueue<>(queueCapacity);
    this.inputStream = new SqlDataInputStream();
    this.outputStream = new SqlDataOutputStream();
  }

  // Called by the client's response observer when new data arrives
  void onNextResponse(StreamSqlDataResponse response) {
    if (response.hasData()) {
      byte[] data = response.getData().getData().toByteArray();
      if (data.length > 0) {
        try {
          while (!closed) {
            if (readQueue.offer(data, 100, TimeUnit.MILLISECONDS)) {
              break;
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          logger.debug("Interrupted while offering data to readQueue", e);
        }
      }
    } else if (response.hasTerminateSession()) {
      logger.debug("Received TerminateSession from server");
      closeQuietly();
    }
  }

  void onErrorResponse(Throwable t) {
    logger.debug("Received error from server stream", t);
    this.error = t;
    readQueue.offer(new byte[0]); // Sentinel to unblock read
  }

  void onCompletedResponse() {
    logger.debug("Server stream completed");
    readQueue.offer(new byte[0]); // Sentinel to unblock read
  }

  private IOException createIoException(Throwable err) {
    Throwable rootCause = err;
    while (rootCause != null) {
      if (rootCause instanceof StatusRuntimeException || rootCause instanceof StatusException) {
        break;
      }
      if (rootCause.getCause() == null) {
        break;
      }
      rootCause = rootCause.getCause();
    }
    if (rootCause instanceof StatusRuntimeException || rootCause instanceof StatusException) {
      return new IOException("Failed to read from SQL Data Service", rootCause);
    }
    if (err instanceof IOException) {
      return (IOException) err;
    }
    return new IOException("Stream failed", err);
  }

  void closeQuietly() {
    try {
      close();
    } catch (IOException e) {
      // ignore
    }
  }

  @Override
  public InputStream getInputStream() {
    return inputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return outputStream;
  }

  @Override
  public synchronized void close() throws IOException {
    if (closed) {
      return;
    }
    closed = true;
    try {
      bidiStream.closeSend();
    } catch (Exception e) {
      // ignore if already closed or failed
    }

    // Sentinel to unblock any waiting reader
    readQueue.offer(new byte[0]);
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
  public boolean getKeepAlive() {
    return this.keepAlive;
  }

  @Override
  public void setTcpNoDelay(boolean on) {
    this.tcpNoDelay = on;
  }

  @Override
  public boolean getTcpNoDelay() {
    return this.tcpNoDelay;
  }

  @Override
  public void setSoTimeout(int timeout) throws SocketException {
    if (timeout < 0) {
      throw new IllegalArgumentException("timeout can't be negative");
    }
    this.soTimeout = timeout;
  }

  @Override
  public int getSoTimeout() {
    return this.soTimeout;
  }

  @Override
  public void shutdownInput() throws IOException {
    // No-op for bidi stream socket
  }

  @Override
  public void shutdownOutput() throws IOException {
    try {
      bidiStream.closeSend();
    } catch (Exception e) {
      throw new IOException("Failed to shutdown stream output", e);
    }
  }

  @Override
  public boolean isInputShutdown() {
    return closed;
  }

  @Override
  public boolean isOutputShutdown() {
    return closed;
  }

  @Override
  public void connect(SocketAddress endpoint) throws IOException {
    // Already connected
  }

  @Override
  public void connect(SocketAddress endpoint, int timeout) throws IOException {
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
      if (b == null) {
        throw new NullPointerException();
      }
      if (off < 0 || len < 0 || len > b.length - off) {
        throw new IndexOutOfBoundsException();
      }
      if (len == 0) {
        return 0;
      }
      if (error != null) {
        throw createIoException(error);
      }
      if (closed && readQueue.isEmpty() && currentBlock == null) {
        return -1;
      }

      if (currentBlock == null || currentOffset >= currentBlock.length) {
        try {
          int timeout = soTimeout;
          if (timeout > 0) {
            currentBlock = readQueue.poll(timeout, TimeUnit.MILLISECONDS);
            if (currentBlock == null) {
              if (closed) {
                return -1;
              }
              throw new SocketTimeoutException(
                  "Read timed out after " + timeout + "ms from SQL Data Service");
            }
          } else {
            currentBlock = readQueue.take();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IOException("Read interrupted", e);
        }

        if (error != null) {
          throw createIoException(error);
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
      if (b == null) {
        throw new NullPointerException();
      }
      if (off < 0 || len < 0 || len > b.length - off) {
        throw new IndexOutOfBoundsException();
      }
      if (len == 0) {
        return;
      }
      if (error != null) {
        throw createIoException(error);
      }
      if (closed) {
        throw new IOException("Socket is closed");
      }

      StreamSqlDataRequest request =
          StreamSqlDataRequest.newBuilder()
              .setData(
                  DataPacket.newBuilder()
                      .setData(com.google.protobuf.ByteString.copyFrom(b, off, len))
                      .build())
              .build();

      try {
        synchronized (bidiStream) {
          bidiStream.send(request);
        }
      } catch (Exception e) {
        throw new IOException("Failed to write to stream", e);
      }
    }
  }
}
