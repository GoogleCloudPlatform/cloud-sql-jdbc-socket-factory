/*
 * Copyright 2023 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;

public class GcpConnectionFactoryProviderTest {

  static final String PUBLIC_IP = "127.0.0.1";
  static final String PRIVATE_IP = "10.0.0.1";
  private final CredentialFactory credentialFactory = new StubCredentialFactory();
  ListeningScheduledExecutorService defaultExecutor;
  ListenableFuture<KeyPair> clientKeyPair;

  String fakeInstanceName = "myProject:myRegion:myInstance";

  private static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
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

  private HttpTransport fakeSuccessHttpTransport(Duration certDuration) {
    final JsonFactory jsonFactory = new GsonFactory();
    return new MockHttpTransport() {
      @Override
      public LowLevelHttpRequest buildRequest(String method, String url) {
        return new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            if (method.equals("GET") && url.contains("connectSettings")) {
              ConnectSettings settings =
                  new ConnectSettings()
                      .setBackendType("SECOND_GEN")
                      .setIpAddresses(
                          ImmutableList.of(
                              new IpMapping().setIpAddress(PUBLIC_IP).setType("PRIMARY"),
                              new IpMapping().setIpAddress(PRIVATE_IP).setType("PRIVATE")))
                      .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT))
                      .setDatabaseVersion("POSTGRES14")
                      .setRegion("myRegion");
              settings.setFactory(jsonFactory);
              response
                  .setContent(settings.toPrettyString())
                  .setContentType(Json.MEDIA_TYPE)
                  .setStatusCode(HttpStatusCodes.STATUS_CODE_OK);
            } else if (method.equals("POST") && url.contains("generateEphemeralCert")) {
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
            }
            return response;
          }
        };
      }
    };
  }

  @Before
  public void setup() throws GeneralSecurityException, InterruptedException, ExecutionException {

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec =
        new PKCS8EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PRIVATE_KEY));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    X509EncodedKeySpec publicKeySpec =
        new X509EncodedKeySpec(decodeBase64StripWhitespace(TestKeys.CLIENT_PUBLIC_KEY));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    clientKeyPair = Futures.immediateFuture(new KeyPair(publicKey, privateKey));

    ScheduledThreadPoolExecutor executor =
        (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(2);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    defaultExecutor =
        MoreExecutors.listeningDecorator(
            MoreExecutors.getExitingScheduledExecutorService(executor));

    SqlAdminApiFetcher fetcher =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)))
            .create(credentialFactory.create());
    StubApiFetcherFactory apiClientFactory =
        new StubApiFetcherFactory(fakeSuccessHttpTransport(Duration.ofSeconds(0)));

    CloudSqlConnector coreSocketFactory =
        new CloudSqlConnector(
            defaultExecutor, clientKeyPair.get(), apiClientFactory, new StubCredentialFactory());
  }
}
