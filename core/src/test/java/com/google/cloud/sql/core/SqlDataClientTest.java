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

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.cloud.sql.v1beta4.DataPacket;
import com.google.cloud.sql.v1beta4.StartSession;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SqlDataClientTest {

  @Rule public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

  private final FakeSqlDataService serviceImpl = new FakeSqlDataService();
  private SqlDataClient client;
  private CredentialFactory credentialFactory;

  @Before
  public void setUp() throws Exception {
    String serverName = InProcessServerBuilder.generateName();
    grpcCleanup.register(
        InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serviceImpl)
            .build()
            .start());

    io.grpc.ManagedChannel channel =
        grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build());

    ConnectorConfig config =
        new ConnectorConfig.Builder()
            .withSqlDataEndpoint("unused-endpoint")
            .withSqlDataStreamTimeout(Duration.ofSeconds(10))
            .build();

    credentialFactory = new StubCredentialFactory();
    client = new SqlDataClient(config, credentialFactory, "test-agent", channel);
  }

  @After
  public void tearDown() {
    client.close();
  }

  @Test
  public void testConnect_Success() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    assertThat(socket).isNotNull();
    assertThat(socket.isClosed()).isFalse();

    // Verify StartSession was received by server
    StreamSqlDataRequest receivedRequest = serviceImpl.requests.poll(5, TimeUnit.SECONDS);
    assertThat(receivedRequest).isNotNull();
    assertThat(receivedRequest.hasStartSession()).isTrue();
    StartSession startSession = receivedRequest.getStartSession();
    assertThat(startSession.getInstanceId()).isEqualTo("projects/proj/instances/inst");
    assertThat(startSession.getLocationId()).isEqualTo("locations/reg");

    // Test Data Transfer
    OutputStream out = socket.getOutputStream();
    InputStream in = socket.getInputStream();

    // Client -> Server
    byte[] clientData = "Hello Server".getBytes(UTF_8);
    out.write(clientData);
    out.flush();

    StreamSqlDataRequest dataRequest = serviceImpl.requests.poll(5, TimeUnit.SECONDS);
    assertThat(dataRequest).isNotNull();
    assertThat(dataRequest.hasData()).isTrue();
    assertThat(dataRequest.getData().getData().toByteArray()).isEqualTo(clientData);

    // Server -> Client
    byte[] serverData = "Hello Client".getBytes(UTF_8);
    serviceImpl.responseObserver.onNext(
        StreamSqlDataResponse.newBuilder()
            .setData(DataPacket.newBuilder().setData(ByteString.copyFrom(serverData)).build())
            .build());

    byte[] readBuffer = new byte[serverData.length];
    int bytesRead = in.read(readBuffer);
    assertThat(bytesRead).isEqualTo(serverData.length);
    assertThat(readBuffer).isEqualTo(serverData);

    socket.close();

    // Verify client close sends onCompleted to server
    assertThat(serviceImpl.completed.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  public void testRead_HandshakeFailure() throws Exception {
    serviceImpl.failHandshake = true;
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    assertThat(socket).isNotNull();
    assertThat(socket.isClosed()).isFalse();

    InputStream in = socket.getInputStream();
    byte[] buf = new byte[10];
    IOException ex = assertThrows(IOException.class, () -> in.read(buf));
    assertThat(ex).hasMessageThat().contains("Failed to read from SQL Data Service");
    assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
    StatusRuntimeException statusEx = (StatusRuntimeException) ex.getCause();
    assertThat(statusEx.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
  }

  @Test
  public void testSingleByteWriteAndRead() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    socket.connect(new java.net.InetSocketAddress("127.0.0.1", 1234));
    socket.connect(new java.net.InetSocketAddress("127.0.0.1", 1234), 100);
    assertThat(socket.isConnected()).isTrue();

    // Consume StartSession
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    // Write single byte
    socket.getOutputStream().write(42);
    StreamSqlDataRequest dataRequest = serviceImpl.requests.poll(5, TimeUnit.SECONDS);
    assertThat(dataRequest).isNotNull();
    assertThat(dataRequest.hasData()).isTrue();
    assertThat(dataRequest.getData().getData().toByteArray()).isEqualTo(new byte[] {42});

    // Server sends single byte response
    serviceImpl.responseObserver.onNext(
        StreamSqlDataResponse.newBuilder()
            .setData(DataPacket.newBuilder().setData(ByteString.copyFrom(new byte[] {99})).build())
            .build());

    int readByte = socket.getInputStream().read();
    assertThat(readByte).isEqualTo(99);

    socket.close();
    assertThat(socket.isClosed()).isTrue();
    assertThat(socket.isConnected()).isFalse();

    // Writing to closed socket throws IOException
    assertThrows(IOException.class, () -> socket.getOutputStream().write(1));
    assertThrows(IOException.class, () -> socket.getOutputStream().write(new byte[5]));
  }

  @Test
  public void testServerTerminateSession() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    // Consume StartSession
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    serviceImpl.responseObserver.onNext(
        StreamSqlDataResponse.newBuilder()
            .setTerminateSession(com.google.cloud.sql.v1beta4.TerminateSession.getDefaultInstance())
            .build());

    // Reading after terminate session and empty queue should return -1 (EOF)
    int read = socket.getInputStream().read();
    assertThat(read).isEqualTo(-1);
    assertThat(socket.isClosed()).isTrue();
  }

  @Test
  public void testServerOnCompleted() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    // Consume StartSession
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    serviceImpl.responseObserver.onCompleted();

    // Reading after server onCompleted should return -1 (EOF)
    int read = socket.getInputStream().read();
    assertThat(read).isEqualTo(-1);
  }

  @Test
  public void testRead_TimeoutThrowsSocketTimeoutException() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    // Connect with 200ms timeout
    Socket socket = client.connect(instanceName, 200);
    // Consume StartSession
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    assertThat(socket.getSoTimeout()).isEqualTo(200);

    // No data sent by server; read should time out after 200ms
    InputStream in = socket.getInputStream();
    SocketTimeoutException ex =
        assertThrows(SocketTimeoutException.class, () -> in.read(new byte[10]));
    assertThat(ex).hasMessageThat().contains("Read timed out after 200ms");

    socket.close();
  }

  @Test
  public void testSetSoTimeout() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    assertThat(socket.getSoTimeout()).isEqualTo(5000);

    socket.setSoTimeout(150);
    assertThat(socket.getSoTimeout()).isEqualTo(150);

    // Consume StartSession
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    InputStream in = socket.getInputStream();
    SocketTimeoutException ex = assertThrows(SocketTimeoutException.class, () -> in.read());
    assertThat(ex).hasMessageThat().contains("Read timed out after 150ms");

    socket.close();
  }

  @Test
  public void testStreamClose_ClosesSocket() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    socket.getInputStream().close();
    assertThat(socket.isClosed()).isTrue();

    Socket socket2 = client.connect(instanceName, 5000);
    socket2.getOutputStream().close();
    assertThat(socket2.isClosed()).isTrue();
  }

  @Test
  public void testStreamAvailable() throws Exception {
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    Socket socket = client.connect(instanceName, 5000);
    serviceImpl.requests.poll(5, TimeUnit.SECONDS);

    assertThat(socket.getInputStream().available()).isEqualTo(0);

    byte[] serverData = "packet".getBytes(UTF_8);
    serviceImpl.responseObserver.onNext(
        StreamSqlDataResponse.newBuilder()
            .setData(DataPacket.newBuilder().setData(ByteString.copyFrom(serverData)).build())
            .build());

    // Read 2 bytes
    byte[] buf = new byte[2];
    int n = socket.getInputStream().read(buf);
    assertThat(n).isEqualTo(2);

    // available() should report remaining 4 bytes in block
    assertThat(socket.getInputStream().available()).isEqualTo(4);

    socket.close();
  }
}
