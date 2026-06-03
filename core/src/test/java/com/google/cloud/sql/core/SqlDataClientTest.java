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
  public void testConnect_HandshakeFailure() throws Exception {
    serviceImpl.failHandshake = true;
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    IOException ex = assertThrows(IOException.class, () -> client.connect(instanceName, 5000));
    assertThat(ex).hasMessageThat().contains("Failed to connect via SQL Data Service");
    assertThat(ex.getCause()).isInstanceOf(StatusRuntimeException.class);
    StatusRuntimeException statusEx = (StatusRuntimeException) ex.getCause();
    assertThat(statusEx.getStatus().getCode()).isEqualTo(Status.Code.FAILED_PRECONDITION);
  }

  @Test
  public void testConnect_Timeout() throws Exception {
    serviceImpl.delayHandshake = true;
    CloudSqlInstanceName instanceName = new CloudSqlInstanceName("proj:reg:inst");

    IOException ex = assertThrows(IOException.class, () -> client.connect(instanceName, 500));
    assertThat(ex)
        .hasMessageThat()
        .contains("Connection timed out waiting for SQL Data Service response");
  }
}
