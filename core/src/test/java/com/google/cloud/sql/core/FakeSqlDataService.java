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

import com.google.cloud.sql.v1beta4.SessionMetadata;
import com.google.cloud.sql.v1beta4.SqlDataServiceGrpc;
import com.google.cloud.sql.v1beta4.StreamSqlDataRequest;
import com.google.cloud.sql.v1beta4.StreamSqlDataResponse;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

class FakeSqlDataService extends SqlDataServiceGrpc.SqlDataServiceImplBase {
  final BlockingQueue<StreamSqlDataRequest> requests = new ArrayBlockingQueue<>(10);
  final java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
  volatile StreamObserver<StreamSqlDataResponse> responseObserver;
  boolean failHandshake = false;
  boolean delayHandshake = false;

  @Override
  public StreamObserver<StreamSqlDataRequest> streamSqlData(
      StreamObserver<StreamSqlDataResponse> responseObserver) {
    this.responseObserver = responseObserver;

    return new StreamObserver<StreamSqlDataRequest>() {
      @Override
      public void onNext(StreamSqlDataRequest request) {
        requests.add(request);
        if (request.hasStartSession()) {
          if (failHandshake) {
            responseObserver.onError(
                Status.FAILED_PRECONDITION
                    .withDescription("AI Developer Edition not enabled")
                    .asRuntimeException());
          } else if (delayHandshake) {
            // Do nothing, let it timeout
          } else {
            // Send SessionMetadata back to establish connection
            responseObserver.onNext(
                StreamSqlDataResponse.newBuilder()
                    .setSessionMetadata(SessionMetadata.getDefaultInstance())
                    .build());
          }
        }
      }

      @Override
      public void onError(Throwable t) {
        // ignore
      }

      @Override
      public void onCompleted() {
        completed.countDown();
        responseObserver.onCompleted();
      }
    };
  }
}
