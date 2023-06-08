/*
 * Copyright 2023 Google LLC
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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class BadConnectionFactory extends HttpTransport {

  @Override
  protected LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
    return new FailToConnectRequest();
  }

  private static class FailToConnectRequest extends LowLevelHttpRequest {

    @Override
    public void addHeader(String name, String value) throws IOException {
      // do nothing.
    }

    @Override
    public LowLevelHttpResponse execute() throws IOException {
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        // Ignore the interruption
      }
      throw new SocketTimeoutException("Fake connect timeout");
    }
  }
}
