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

import com.google.api.client.http.HttpStatusCodes;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh.OAuth2RefreshHandler;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bouncycastle.operator.OperatorCreationException;

public class MockAdminApi {

  private static final Pattern CONNECT_SETTINGS_PATTERN =
      Pattern.compile(
          "(?<baseUrl>.*)sql/v1beta4/projects/(?<project>.*)/instances/(?<instance>.*)/connectSettings");
  private static final Pattern GENERATE_EPHEMERAL_CERT_PATTERN =
      Pattern.compile(
          "(?<baseUrl>.*)sql/v1beta4/projects/(?<project>.*)/instances/(?<instance>.*):generateEphemeralCert");
  private final KeyPair clientKeyPair;
  private final List<ConnectSettingsRequest> connectSettingsRequests;
  private final AtomicInteger allConnectSettingsRequestsIndex;
  private final List<GenerateEphemeralCertRequest> generateEphemeralCertRequests;
  private final AtomicInteger generateEphemeralCertRequestsIndex;

  public MockAdminApi() throws NoSuchAlgorithmException, InvalidKeySpecException {
    connectSettingsRequests = new ArrayList<>();
    allConnectSettingsRequestsIndex = new AtomicInteger(0);
    generateEphemeralCertRequests = new ArrayList<>();
    generateEphemeralCertRequestsIndex = new AtomicInteger(0);

    // Decode proxy server private test key
    clientKeyPair = TestKeys.getClientKeyPair();
  }

  public KeyPair getClientKeyPair() {
    return clientKeyPair;
  }

  public OAuth2RefreshHandler getRefreshHandler(String refreshToken, Date expirationTime) {
    return new MockRefreshHandler(refreshToken, expirationTime);
  }

  public void addConnectSettingsResponse(
      String instanceConnectionName,
      String publicIp,
      String privateIp,
      String databaseVersion,
      String pscHostname,
      String baseUrl) {
    CloudSqlInstanceName cloudSqlInstanceName = new CloudSqlInstanceName(instanceConnectionName);

    ArrayList<IpMapping> ipMappings = new ArrayList<>();
    if (publicIp != null && !publicIp.isEmpty()) {
      ipMappings.add(new IpMapping().setIpAddress(publicIp).setType("PRIMARY"));
    }
    if (privateIp != null && !privateIp.isEmpty()) {
      ipMappings.add(new IpMapping().setIpAddress(privateIp).setType("PRIVATE"));
    }
    if (ipMappings.isEmpty()) {
      ipMappings = null;
    }
    ConnectSettings settings =
        new ConnectSettings()
            .setBackendType("SECOND_GEN")
            .setIpAddresses(ipMappings)
            .setServerCaCert(new SslCert().setCert(TestKeys.getServerCertPem()))
            .setDatabaseVersion(databaseVersion)
            .setDnsName(pscHostname)
            .setRegion(cloudSqlInstanceName.getRegionId());
    settings.setFactory(GsonFactory.getDefaultInstance());

    connectSettingsRequests.add(
        new ConnectSettingsRequest(cloudSqlInstanceName, settings, baseUrl));
  }

  public void addGenerateEphemeralCertResponse(
      String instanceConnectionName, Duration ephemeralCertExpiration, String baseUrl)
      throws GeneralSecurityException, OperatorCreationException {
    CloudSqlInstanceName cloudSqlInstanceName = new CloudSqlInstanceName(instanceConnectionName);

    SslCert ephemeralCert =
        new SslCert().setCert(TestKeys.createEphemeralCert(ephemeralCertExpiration));

    GenerateEphemeralCertResponse generateEphemeralCertResponse =
        new GenerateEphemeralCertResponse().setEphemeralCert(ephemeralCert);
    generateEphemeralCertResponse.setFactory(GsonFactory.getDefaultInstance());

    generateEphemeralCertRequests.add(
        new GenerateEphemeralCertRequest(
            cloudSqlInstanceName, generateEphemeralCertResponse, baseUrl));
  }

  public HttpTransport getHttpTransport() {
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            // GET connect settings
            Matcher connectSettingsMatcher = CONNECT_SETTINGS_PATTERN.matcher(url);
            if (method.equals("GET") && connectSettingsMatcher.matches()) {
              int i = allConnectSettingsRequestsIndex.getAndIncrement();
              ConnectSettingsRequest connectSettingsRequest = connectSettingsRequests.get(i);
              if (isRequestUnknown(
                  connectSettingsMatcher,
                  connectSettingsRequest.getCloudSqlInstanceName(),
                  connectSettingsRequest.getBaseUrl())) {
                throw new RuntimeException("Unrecognized request: GET " + url);
              }
              return new MockLowLevelHttpResponse()
                  .setContent(connectSettingsRequest.getSettings().toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            }

            // POST ephemeral certificate
            Matcher generateEphemeralMatcher = GENERATE_EPHEMERAL_CERT_PATTERN.matcher(url);
            if (method.equals("POST") && generateEphemeralMatcher.matches()) {
              int i = generateEphemeralCertRequestsIndex.getAndIncrement();
              GenerateEphemeralCertRequest generateEphemeralCertRequest =
                  generateEphemeralCertRequests.get(i);
              if (isRequestUnknown(
                  generateEphemeralMatcher,
                  generateEphemeralCertRequest.getCloudSqlInstanceName(),
                  generateEphemeralCertRequest.getBaseUrl())) {
                throw new RuntimeException("Unrecognized request: GET " + url);
              }
              return new MockLowLevelHttpResponse()
                  .setContent(
                      generateEphemeralCertRequest
                          .getGenerateEphemeralCertResponse()
                          .toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            }

            throw new RuntimeException(
                "Unregistered Mock SQL Admin API request: " + method + " " + url);
          }
        };
      }
    };
  }

  private boolean isRequestUnknown(
      Matcher urlMatcher, CloudSqlInstanceName cloudSqlInstanceName, String baseUrl) {
    return !urlMatcher.group("project").equals(cloudSqlInstanceName.getProjectId())
        || !urlMatcher.group("instance").equals(cloudSqlInstanceName.getInstanceId())
        || !urlMatcher.group("baseUrl").equals(baseUrl);
  }

  private static class ConnectSettingsRequest {

    private final CloudSqlInstanceName cloudSqlInstanceName;
    private final ConnectSettings settings;
    private final String baseUrl;

    public ConnectSettingsRequest(
        CloudSqlInstanceName cloudSqlInstanceName, ConnectSettings settings, String baseUrl) {
      this.cloudSqlInstanceName = cloudSqlInstanceName;
      this.settings = settings;
      this.baseUrl = baseUrl;
    }

    public ConnectSettings getSettings() {
      return settings;
    }

    public CloudSqlInstanceName getCloudSqlInstanceName() {
      return cloudSqlInstanceName;
    }

    public String getBaseUrl() {
      return baseUrl;
    }
  }

  private static class GenerateEphemeralCertRequest {

    private final CloudSqlInstanceName cloudSqlInstanceName;
    private final GenerateEphemeralCertResponse generateEphemeralCertResponse;
    private final String baseUrl;

    public GenerateEphemeralCertRequest(
        CloudSqlInstanceName instanceConnectionName,
        GenerateEphemeralCertResponse generateEphemeralCertResponse,
        String baseUrl) {
      this.cloudSqlInstanceName = instanceConnectionName;
      this.generateEphemeralCertResponse = generateEphemeralCertResponse;
      this.baseUrl = baseUrl;
    }

    public CloudSqlInstanceName getCloudSqlInstanceName() {
      return cloudSqlInstanceName;
    }

    public GenerateEphemeralCertResponse getGenerateEphemeralCertResponse() {
      return generateEphemeralCertResponse;
    }

    public String getBaseUrl() {
      return baseUrl;
    }
  }

  private static class MockRefreshHandler implements OAuth2RefreshHandler {
    private final String refreshToken;
    private final Date expirationTime;

    public MockRefreshHandler(String refreshToken, Date expirationTime) {
      this.refreshToken = refreshToken;
      this.expirationTime = expirationTime;
    }

    @Override
    public AccessToken refreshAccessToken() throws IOException {
      return new AccessToken(refreshToken, expirationTime);
    }
  }
}
