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
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class MockAdminApi {

  private static final Pattern CONNECT_SETTINGS_PATTERN =
      Pattern.compile(
          ".*/sql/v1beta4/projects/(?<project>.*)/instances/(?<instance>.*)/connectSettings");
  private static final Pattern GENERATE_EPHEMERAL_CERT_PATTERN =
      Pattern.compile(
          ".*/sql/v1beta4/projects/(?<project>.*)/instances/(?<instance>.*):generateEphemeralCert");
  private final KeyPair clientKeyPair;
  private final PrivateKey proxyServerPrivateKey;
  private final List<ConnectSettingsRequest> connectSettingsRequests;
  private final AtomicInteger allConnectSettingsRequestsIndex;
  private final List<GenerateEphemeralCertRequest> generateEphemeralCertRequests;
  private final AtomicInteger generateEphemeralCertRequestsIndex;

  public MockAdminApi() throws NoSuchAlgorithmException, InvalidKeySpecException {
    connectSettingsRequests = new ArrayList<>();
    allConnectSettingsRequestsIndex = new AtomicInteger(0);
    generateEphemeralCertRequests = new ArrayList<>();
    generateEphemeralCertRequestsIndex = new AtomicInteger(0);

    Decoder decoder = Base64.getDecoder();
    // Decode client private test key
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(decoder.decode(TestKeys.CLIENT_PRIVATE_KEY.replaceAll("\\s", "")));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    // Decode client public test key
    X509EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(decoder.decode(TestKeys.CLIENT_PUBLIC_KEY.replaceAll("\\s", "")));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    // Decode proxy server private test key
    PKCS8EncodedKeySpec keySpec =
        new PKCS8EncodedKeySpec(
            decoder.decode(TestKeys.SIGNING_CA_PRIVATE_KEY.replaceAll("\\s", "")));

    proxyServerPrivateKey = keyFactory.generatePrivate(keySpec);
    clientKeyPair = new KeyPair(publicKey, privateKey);
  }

  public KeyPair getClientKeyPair() {
    return clientKeyPair;
  }

  public OAuth2RefreshHandler getRefreshHandler(String refreshToken, Date expirationTime) {
    return new MockRefreshHandler(refreshToken, expirationTime);
  }

  public void addConnectSettingsResponse(
      String instanceConnectionName, String publicIp, String privateIp, String databaseVersion) {
    CloudSqlInstanceName cloudSqlInstanceName = new CloudSqlInstanceName(instanceConnectionName);

    ArrayList<IpMapping> ipMappings = new ArrayList<>();
    if (!publicIp.isEmpty()) {
      ipMappings.add(new IpMapping().setIpAddress(publicIp).setType("PRIMARY"));
    }
    if (!privateIp.isEmpty()) {
      ipMappings.add(new IpMapping().setIpAddress(privateIp).setType("PRIVATE"));
    }

    ConnectSettings settings =
        new ConnectSettings()
            .setBackendType("SECOND_GEN")
            .setIpAddresses(ipMappings)
            .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT))
            .setDatabaseVersion(databaseVersion)
            .setRegion(cloudSqlInstanceName.getRegionId());
    settings.setFactory(GsonFactory.getDefaultInstance());

    connectSettingsRequests.add(new ConnectSettingsRequest(cloudSqlInstanceName, settings));
  }

  public void addGenerateEphemeralCertResponse(
      String instanceConnectionName, Duration ephemeralCertExpiration)
      throws GeneralSecurityException, OperatorCreationException {
    CloudSqlInstanceName cloudSqlInstanceName = new CloudSqlInstanceName(instanceConnectionName);

    SslCert ephemeralCert = new SslCert().setCert(createEphemeralCert(ephemeralCertExpiration));

    GenerateEphemeralCertResponse generateEphemeralCertResponse =
        new GenerateEphemeralCertResponse().setEphemeralCert(ephemeralCert);
    generateEphemeralCertResponse.setFactory(GsonFactory.getDefaultInstance());

    generateEphemeralCertRequests.add(
        new GenerateEphemeralCertRequest(cloudSqlInstanceName, generateEphemeralCertResponse));
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
                  connectSettingsMatcher, connectSettingsRequest.getCloudSqlInstanceName())) {
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
                  generateEphemeralCertRequest.getCloudSqlInstanceName())) {
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

  private String createEphemeralCert(Duration timeUntilExpiration)
      throws GeneralSecurityException, OperatorCreationException {
    ZonedDateTime notBefore = ZonedDateTime.now(ZoneId.of("UTC"));
    ZonedDateTime notAfter = notBefore.plus(timeUntilExpiration);

    final ContentSigner signer =
        new JcaContentSignerBuilder("SHA256withRSA").build(proxyServerPrivateKey);

    X500Principal issuer =
        new X500Principal("C = US, O = Google\\, Inc, CN=Google Cloud SQL Signing CA foo:baz");
    X500Principal subject = new X500Principal("C = US, O = Google\\, Inc, CN=temporary-subject");

    JcaX509v3CertificateBuilder certificateBuilder =
        new JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.ONE,
            Date.from(notBefore.toInstant()),
            Date.from(notAfter.toInstant()),
            subject,
            clientKeyPair.getPublic());

    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);
    Certificate cert = new JcaX509CertificateConverter().getCertificate(certificateHolder);
    return "-----BEGIN CERTIFICATE-----\n"
        + Base64.getEncoder().encodeToString(cert.getEncoded()).replaceAll("(.{64})", "$1\n")
        + "\n"
        + "-----END CERTIFICATE-----\n";
  }

  private boolean isRequestUnknown(Matcher urlMatcher, CloudSqlInstanceName cloudSqlInstanceName) {
    return !urlMatcher.group("project").equals(cloudSqlInstanceName.getProjectId())
        || !urlMatcher.group("instance").equals(cloudSqlInstanceName.getInstanceId());
  }

  private static class ConnectSettingsRequest {

    private final CloudSqlInstanceName cloudSqlInstanceName;
    private final ConnectSettings settings;

    public ConnectSettingsRequest(
        CloudSqlInstanceName cloudSqlInstanceName, ConnectSettings settings) {
      this.cloudSqlInstanceName = cloudSqlInstanceName;
      this.settings = settings;
    }

    public ConnectSettings getSettings() {
      return settings;
    }

    public CloudSqlInstanceName getCloudSqlInstanceName() {
      return cloudSqlInstanceName;
    }
  }

  private static class GenerateEphemeralCertRequest {

    private final CloudSqlInstanceName cloudSqlInstanceName;
    private final GenerateEphemeralCertResponse generateEphemeralCertResponse;

    public GenerateEphemeralCertRequest(
        CloudSqlInstanceName instanceConnectionName,
        GenerateEphemeralCertResponse generateEphemeralCertResponse) {
      this.cloudSqlInstanceName = instanceConnectionName;
      this.generateEphemeralCertResponse = generateEphemeralCertResponse;
    }

    public CloudSqlInstanceName getCloudSqlInstanceName() {
      return cloudSqlInstanceName;
    }

    public GenerateEphemeralCertResponse getGenerateEphemeralCertResponse() {
      return generateEphemeralCertResponse;
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
