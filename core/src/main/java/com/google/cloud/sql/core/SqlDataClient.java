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

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.cloud.sql.v1beta4.SqlDataServiceGrpc;
import com.google.cloud.sql.v1beta4.StartSession;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import io.grpc.CallCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.auth.MoreCallCredentials;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SqlDataClient {
  private static final Logger logger = LoggerFactory.getLogger(SqlDataClient.class);

  private final String endpoint;
  private final CredentialFactory credentialFactory;
  private final String quotaProject;
  private final String userAgent;
  private final Duration timeout;
  private final Object channelLock = new Object();
  private final ManagedChannel externalChannel;
  private ManagedChannel channel;

  SqlDataClient(ConnectorConfig config, CredentialFactory credentialFactory, String userAgent) {
    this(config, credentialFactory, userAgent, null);
  }

  SqlDataClient(
      ConnectorConfig config,
      CredentialFactory credentialFactory,
      String userAgent,
      ManagedChannel externalChannel) {
    this.endpoint = config.getSqlDataEndpoint();
    this.credentialFactory = credentialFactory;
    this.quotaProject = config.getAdminQuotaProject();
    this.userAgent = userAgent;
    this.timeout = config.getSqlDataStreamTimeout();
    this.externalChannel = externalChannel;
  }

  private ManagedChannel getChannel() {
    synchronized (channelLock) {
      if (channel == null) {
        if (externalChannel != null) {
          channel = externalChannel;
        } else {
          logger.debug("Initializing gRPC channel to {}", endpoint);
          // Split host and port if present
          String host = endpoint;
          int port = 443;
          int colonIndex = endpoint.indexOf(':');
          if (colonIndex > 0) {
            host = endpoint.substring(0, colonIndex);
            port = Integer.parseInt(endpoint.substring(colonIndex + 1));
          }

          ManagedChannelBuilder<?> builder =
              ManagedChannelBuilder.forAddress(host, port).userAgent(userAgent);

          // For development/testing we might want to support insecure, but default is secure.
          // Go has useInsecure flag. In Java we can check if host is localhost or similar,
          // or just default to transport security.
          if (host.equals("localhost") || host.equals("127.0.0.1")) {
            builder.usePlaintext();
          } else {
            builder.useTransportSecurity();
          }

          channel = builder.build();
        }
      }
      return channel;
    }
  }

  Socket connect(CloudSqlInstanceName instanceName, long connectTimeoutMs) throws IOException {
    ManagedChannel currentChannel = getChannel();

    GoogleCredentials credentials = credentialFactory.getCredentials();
    if (quotaProject != null && !quotaProject.isEmpty()) {
      credentials = credentials.createWithQuotaProject(quotaProject);
    }

    CallCredentials creds = MoreCallCredentials.from(credentials);

    Metadata metadata = new Metadata();
    String instanceId =
        String.format(
            "projects/%s/instances/%s", instanceName.getProjectId(), instanceName.getInstanceId());
    String locationId = String.format("locations/%s", instanceName.getRegionId());

    metadata.put(
        Metadata.Key.of("x-goog-request-params", Metadata.ASCII_STRING_MARSHALLER),
        String.format("instance_id=%s&location_id=%s", instanceId, locationId));

    SqlDataServiceGrpc.SqlDataServiceStub stub =
        SqlDataServiceGrpc.newStub(currentChannel)
            .withCallCredentials(creds)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));

    if (timeout != null && timeout.toMillis() > 0) {
      stub = stub.withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    class ResponseObserver implements StreamObserver<StreamSqlDataResponse> {
      private final CompletableFuture<Void> handshakeFuture;
      private SqlDataSocket socket;

      ResponseObserver(CompletableFuture<Void> handshakeFuture) {
        this.handshakeFuture = handshakeFuture;
      }

      void setSocket(SqlDataSocket socket) {
        this.socket = socket;
      }

      @Override
      public void onNext(StreamSqlDataResponse response) {
        if (response.hasSessionMetadata()) {
          logger.debug("Received SessionMetadata, connection established");
          handshakeFuture.complete(null);
        } else if (socket != null) {
          socket.onNextResponse(response);
        }
      }

      @Override
      public void onError(Throwable t) {
        if (!handshakeFuture.isDone()) {
          handshakeFuture.completeExceptionally(t);
        }
        if (socket != null) {
          socket.onErrorResponse(t);
        }
      }

      @Override
      public void onCompleted() {
        if (!handshakeFuture.isDone()) {
          handshakeFuture.completeExceptionally(
              new IOException("Stream completed before handshake"));
        }
        if (socket != null) {
          socket.onCompletedResponse();
        }
      }
    }

    CompletableFuture<Void> handshakeFuture = new CompletableFuture<>();
    ResponseObserver responseObserver = new ResponseObserver(handshakeFuture);
    StreamObserver<StreamSqlDataRequest> requestObserver = stub.streamSqlData(responseObserver);

    SqlDataSocket socket = new SqlDataSocket(requestObserver);
    responseObserver.setSocket(socket);

    // Send StartSession
    StreamSqlDataRequest startRequest =
        StreamSqlDataRequest.newBuilder()
            .setStartSession(
                StartSession.newBuilder()
                    .setInstanceId(instanceId)
                    .setLocationId(locationId)
                    .build())
            .build();

    try {
      requestObserver.onNext(startRequest);
    } catch (Exception e) {
      throw new IOException("Failed to send StartSession request", e);
    }

    try {
      if (connectTimeoutMs > 0) {
        handshakeFuture.get(connectTimeoutMs, TimeUnit.MILLISECONDS);
      } else {
        handshakeFuture.get();
      }
    } catch (ExecutionException e) {
      Throwable cause = e.getCause();
      if (cause instanceof StatusRuntimeException) {
        throw new IOException("Failed to connect via SQL Data Service", cause);
      }
      throw new IOException("Failed to connect via SQL Data Service", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Connection interrupted", e);
    } catch (TimeoutException e) {
      throw new IOException("Connection timed out waiting for SQL Data Service response", e);
    }

    return socket;
  }

  void close() {
    synchronized (channelLock) {
      if (channel != null) {
        logger.debug("Closing gRPC channel");
        channel.shutdown();
        try {
          if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow();
          }
        } catch (InterruptedException e) {
          channel.shutdownNow();
          Thread.currentThread().interrupt();
        }
        channel = null;
      }
    }
  }
}
