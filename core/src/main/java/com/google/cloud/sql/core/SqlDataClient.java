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

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.BidiStream;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.cloud.sql.v1beta4.SqlDataServiceClient;
import com.google.cloud.sql.v1beta4.SqlDataServiceSettings;
import com.google.cloud.sql.v1beta4.StartSession;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
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
  private SqlDataServiceClient client;

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

  private SqlDataServiceClient getClient() throws IOException {
    synchronized (channelLock) {
      if (client == null) {
        ManagedChannel currentChannel = getChannel();
        GoogleCredentials credentials = credentialFactory.getCredentials();
        if (quotaProject != null && !quotaProject.isEmpty()) {
          credentials = credentials.createWithQuotaProject(quotaProject);
        }

        SqlDataServiceSettings settings =
            SqlDataServiceSettings.newBuilder()
                .setTransportChannelProvider(
                    FixedTransportChannelProvider.create(
                        GrpcTransportChannel.create(currentChannel)))
                .setCredentialsProvider(FixedCredentialsProvider.create(credentials))
                .build();
        client = SqlDataServiceClient.create(settings);
      }
      return client;
    }
  }

  Socket connect(CloudSqlInstanceName instanceName, long connectTimeoutMs) throws IOException {
    SqlDataServiceClient currentClient = getClient();

    String instanceId =
        String.format(
            "projects/%s/instances/%s", instanceName.getProjectId(), instanceName.getInstanceId());
    String locationId = String.format("locations/%s", instanceName.getRegionId());

    Map<String, List<String>> headers = new HashMap<>();
    headers.put(
        "x-goog-request-params",
        Collections.singletonList(
            String.format("instance_id=%s&location_id=%s", instanceId, locationId)));

    GrpcCallContext context = GrpcCallContext.createDefault().withExtraHeaders(headers);

    if (timeout != null && timeout.toMillis() > 0) {
      context = context.withTimeoutDuration(timeout);
    }

    BidiStream<StreamSqlDataRequest, StreamSqlDataResponse> bidiStream =
        currentClient.streamSqlDataCallable().call(context);

    SqlDataSocket socket = new SqlDataSocket(bidiStream);

    Thread readerThread =
        new Thread(
            () -> {
              try {
                for (StreamSqlDataResponse response : bidiStream) {
                  socket.onNextResponse(response);
                }
                socket.onCompletedResponse();
              } catch (Exception e) {
                socket.onErrorResponse(e);
              }
            },
            "sql-data-reader-" + instanceName.getConnectionName());
    readerThread.setDaemon(true);
    readerThread.start();

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
      bidiStream.send(startRequest);
    } catch (Exception e) {
      socket.closeQuietly();
      throw new IOException("Failed to send StartSession request", e);
    }

    return socket;
  }

  void close() {
    synchronized (channelLock) {
      if (client != null) {
        logger.debug("Closing SqlDataServiceClient");
        client.close();
        client = null;
      }
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
