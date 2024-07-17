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

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonError.ErrorInfo;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.GenericJson;
import com.google.api.client.json.Json;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.Before;

public class CloudSqlCoreTestingBase {

  static final String PUBLIC_IP = "127.0.0.1";
  // If running tests on Mac, need to run "ifconfig lo0 alias 127.0.0.2 up" first
  static final String PRIVATE_IP = "127.0.0.2";

  static final String SERVER_MESSAGE = "HELLO";

  static final String ERROR_MESSAGE_BAD_GATEWAY =
      "The server encountered a temporary error and could not complete your request.";
  static final String ERROR_MESSAGE_NOT_AUTHORIZED =
      "The client is not authorized to make this request.";

  final CredentialFactoryProvider stubCredentialFactoryProvider =
      new CredentialFactoryProvider(new StubCredentialFactory());

  ListenableFuture<KeyPair> clientKeyPair;

  public CloudSqlCoreTestingBase() {}

  // Creates a fake "accessNotConfigured" exception that can be used for testing.
  static MockHttpTransport fakeNotConfiguredException() {
    return fakeGoogleJsonResponseException(
        "accessNotConfigured",
        "Cloud SQL Admin API has not been used in project 12345 before or it is disabled. Enable"
            + " it by visiting "
            + " https://console.developers.google.com/apis/api/sqladmin.googleapis.com/overview?project=12345"
            + " then retry. If you enabled this API recently, wait a few minutes for the action to"
            + " propagate to our systems and retry.",
        HttpStatusCodes.STATUS_CODE_FORBIDDEN);
  }

  // Creates a fake "notAuthorized" exception that can be used for testing.
  static MockHttpTransport fakeNotAuthorizedException() {
    return fakeGoogleJsonResponseException(
        "notAuthorized", ERROR_MESSAGE_NOT_AUTHORIZED, HttpStatusCodes.STATUS_CODE_UNAUTHORIZED);
  }

  // Creates a fake "serverError" exception that can be used for testing.
  static MockHttpTransport fakeBadGatewayException() {
    return fakeGoogleJsonResponseException(
        "serverError", ERROR_MESSAGE_BAD_GATEWAY, HttpStatusCodes.STATUS_CODE_BAD_GATEWAY);
  }

  // Builds a fake GoogleJsonResponseException for testing API error handling.
  private static MockHttpTransport fakeGoogleJsonResponseException(
      String reason, String message, int statusCode) {
    ErrorInfo errorInfo = new ErrorInfo();
    errorInfo.setReason(reason);
    errorInfo.setMessage(message);
    return fakeGoogleJsonResponseExceptionTransport(errorInfo, message, statusCode);
  }

  private static MockHttpTransport fakeGoogleJsonResponseExceptionTransport(
      ErrorInfo errorInfo, String message, int statusCode) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        errorInfo.setFactory(jsonFactory);
        GoogleJsonError jsonError = new GoogleJsonError();
        jsonError.setCode(statusCode);
        jsonError.setErrors(Collections.singletonList(errorInfo));
        jsonError.setMessage(message);
        jsonError.setFactory(jsonFactory);
        GenericJson errorResponse = new GenericJson();
        errorResponse.set("error", jsonError);
        errorResponse.setFactory(jsonFactory);
        return new MockLowLevelHttpRequest()
            .setResponse(
                new MockLowLevelHttpResponse()
                    .setContent(errorResponse.toPrettyString())
                    .setContentType(Json.MEDIA_TYPE)
                    .setStatusCode(statusCode));
      }
    };
  }

  static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
  }

  @Before
  public void setup() throws GeneralSecurityException {
    clientKeyPair = Futures.immediateFuture(TestKeys.getClientKeyPair());
  }

  MockHttpTransport fakeSuccessHttpTransport(Duration certDuration) {
    return fakeSuccessHttpTransport(TestKeys.getServerCertPem(), certDuration, null);
  }

  MockHttpTransport fakeSuccessHttpTransport(Duration certDuration, String baseUrl) {
    return fakeSuccessHttpTransport(TestKeys.getServerCertPem(), certDuration, baseUrl);
  }

  MockHttpTransport fakeSuccessHttpTransport(String serverCert, Duration certDuration) {
    return fakeSuccessHttpTransport(serverCert, certDuration, null);
  }

  MockHttpTransport fakeSuccessHttpTransport(
      String serverCert, Duration certDuration, String baseUrl) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            if (baseUrl != null && !url.startsWith(baseUrl)) {
              throw new RuntimeException("url " + url + " does not start with baseUrl " + baseUrl);
            }
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            if (method.equals("GET") && url.contains("connectSettings")) {
              ConnectSettings settings =
                  new ConnectSettings()
                      .setBackendType("SECOND_GEN")
                      .setIpAddresses(
                          ImmutableList.of(
                              new IpMapping().setIpAddress(PUBLIC_IP).setType("PRIMARY"),
                              new IpMapping().setIpAddress(PRIVATE_IP).setType("PRIVATE")))
                      .setServerCaCert(new SslCert().setCert(serverCert))
                      .setDatabaseVersion("POSTGRES14")
                      .setRegion("myRegion");
              settings.setFactory(jsonFactory);
              response
                  .setContent(settings.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            } else if (method.equals("POST") && url.contains("generateEphemeralCert")) {
              GenerateEphemeralCertResponse certResponse = new GenerateEphemeralCertResponse();
              certResponse.setEphemeralCert(
                  new SslCert().setCert(TestKeys.createEphemeralCert(certDuration)));
              certResponse.setFactory(jsonFactory);
              response
                  .setContent(certResponse.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            }
            return response;
          }
        };
      }
    };
  }

  HttpTransport fakeIntermittentErrorHttpTransport(
      MockHttpTransport successTransport, MockHttpTransport errorTransport) {
    // Odd request counts get errors, Even request count gets success.
    ConcurrentHashMap<String, Integer> counterByUrl = new ConcurrentHashMap<>();

    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            int count =
                counterByUrl.compute(
                    url,
                    (url, val) -> {
                      if (val == null) {
                        return 1;
                      }
                      return val + 1;
                    });
            if (count % 2 == 0) {
              return successTransport.buildRequest(method, url).execute();
            }
            return errorTransport.buildRequest(method, url).execute();
          }
        };
      }
    };
  }
}
