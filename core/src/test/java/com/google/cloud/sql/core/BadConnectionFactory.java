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

  private class FailToConnectRequest extends LowLevelHttpRequest {

    @Override
    public void addHeader(String name, String value) throws IOException {
      // do nothing.
    }

    @Override
    public LowLevelHttpResponse execute() throws IOException {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
      }
      throw new SocketTimeoutException("Fake connect timeout");
    }
  }
}
