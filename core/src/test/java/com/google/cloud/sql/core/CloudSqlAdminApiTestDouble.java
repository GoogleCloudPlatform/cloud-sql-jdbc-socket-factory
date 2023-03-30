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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
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
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class CloudSqlAdminApiTestDouble {

  static final String PUBLIC_IP = "127.0.0.1";
  static final String PRIVATE_IP = "10.0.0.1";
  static final String SERVER_MESSAGE = "HELLO";
  private final ListenableFuture<KeyPair> clientKeyPair;
  private final CloudSqlInstanceName cloudSqlInstanceName;

  public CloudSqlAdminApiTestDouble(String instanceConnectionName)
      throws InvalidKeySpecException, NoSuchAlgorithmException {
    this.cloudSqlInstanceName = new CloudSqlInstanceName(instanceConnectionName);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PRIVATE_KEY));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    X509EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PUBLIC_KEY));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    this.clientKeyPair = Futures.immediateFuture(new KeyPair(publicKey, privateKey));
  }

  private static HttpTransport fakeGoogleJsonResponseExceptionTransport(
      ErrorInfo errorInfo, String message) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) throws IOException {
        errorInfo.setFactory(jsonFactory);
        GoogleJsonError jsonError = new GoogleJsonError();
        jsonError.setCode(HttpStatusCodes.STATUS_CODE_FORBIDDEN);
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
                    .setStatusCode(HttpStatusCodes.STATUS_CODE_FORBIDDEN));
      }
    };
  }

  private static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
  }

  public ListenableFuture<KeyPair> getClientKeyPair() {
    return clientKeyPair;
  }

  // Builds a fake GoogleJsonResponseException for testing API error handling.
  public HttpTransport fakeGoogleJsonResponseException(String reason, String message) {
    ErrorInfo errorInfo = new ErrorInfo();
    errorInfo.setReason(reason);
    errorInfo.setMessage(message);
    return fakeGoogleJsonResponseExceptionTransport(errorInfo, message);
  }

  // Creates a fake "accessNotConfigured" exception that can be used for testing.
  public HttpTransport fakeNotConfiguredException() {
    return fakeGoogleJsonResponseException(
        "accessNotConfigured",
        "Cloud SQL Admin API has not been used in project 12345 before or it is disabled. Enable"
            + " it by visiting "
            + " https://console.developers.google.com/apis/api/sqladmin.googleapis.com/overview?project=12345"
            + " then retry. If you enabled this API recently, wait a few minutes for the action to"
            + " propagate to our systems and retry.");
  }

  public HttpTransport fakeSuccessHttpTransport(
      Duration certDuration, final String publicIp, final String privateIp) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();

            if (method.equals("GET") && url.contains("connectSettings")) {
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
                      .setDatabaseVersion("POSTGRES14")
                      .setRegion(cloudSqlInstanceName.getRegionId());
              settings.setFactory(jsonFactory);
              response
                  .setContent(settings.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);

              return response;
            }

            if (method.equals("POST") && url.contains("generateEphemeralCert")) {
              GenerateEphemeralCertResponse certResponse = new GenerateEphemeralCertResponse();
              try {
                certResponse.setEphemeralCert(
                    new SslCert().setCert(createEphemeralCert(certDuration)));
                certResponse.setFactory(jsonFactory);
              } catch (GeneralSecurityException
                  | ExecutionException
                  | OperatorCreationException e) {
                throw new RuntimeException(e);
              }
              response
                  .setContent(certResponse.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
              return response;
            }

            throw new RuntimeException("Unknown request");
          }
        };
      }
    };
  }

  private String createEphemeralCert(Duration shiftIntoPast)
      throws GeneralSecurityException, ExecutionException, OperatorCreationException {
    Duration validFor = Duration.ofHours(1);
    ZonedDateTime notBefore = ZonedDateTime.now(ZoneId.of("UTC")).minus(shiftIntoPast);
    ZonedDateTime notAfter = notBefore.plus(validFor);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec =
        new PKCS8EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.SIGNING_CA_PRIVATE_KEY));
    PrivateKey signingKey = keyFactory.generatePrivate(keySpec);

    final ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA").build(signingKey);

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
            Futures.getDone(clientKeyPair).getPublic());

    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

    Certificate cert = new JcaX509CertificateConverter().getCertificate(certificateHolder);

    return "-----BEGIN CERTIFICATE-----\n"
        + Base64.getEncoder().encodeToString(cert.getEncoded()).replaceAll("(.{64})", "$1\n")
        + "\n"
        + "-----END CERTIFICATE-----\n";
  }
}
