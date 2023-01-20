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


import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.when;

import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.ConnectSettings;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertRequest;
import com.google.api.services.sqladmin.model.GenerateEphemeralCertResponse;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
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
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GcpConnectionFactoryProviderTest {

  static final String PUBLIC_IP = "127.0.0.1";
  static final String PRIVATE_IP = "127.0.0.2";

  ListeningScheduledExecutorService defaultExecutor;
  @Mock
  CredentialFactory credentialFactory;

  @Mock
  SQLAdmin adminApi;
  @Mock
  SQLAdmin.Connect adminApiConnect;
  @Mock
  SQLAdmin.Connect.Get adminApiConnectGet;

  @Mock
  SQLAdmin.Connect.GenerateEphemeralCert adminApiConnectGenerateEphemeralCert;

  @Mock
  GenerateEphemeralCertResponse generateEphemeralCertResponse;

  ListenableFuture<KeyPair> clientKeyPair;

  CoreSocketFactory coreSocketFactoryStub;

  String fakeInstanceName = "myProject:myRegion:myInstance";

  private static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
  }

  private String createEphemeralCert(Duration shiftIntoPast)
      throws GeneralSecurityException, ExecutionException, OperatorCreationException {
    Duration validFor = Duration.ofHours(1);
    ZonedDateTime notBefore = ZonedDateTime.now().minus(shiftIntoPast);
    ZonedDateTime notAfter = notBefore.plus(validFor);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(
        decodeBase64StripWhitespace(TestKeys.SIGNING_CA_PRIVATE_KEY));
    PrivateKey signingKey = keyFactory.generatePrivate(keySpec);

    final ContentSigner signer = new JcaContentSignerBuilder("SHA1withRSA").build(signingKey);

    X500Principal issuer = new X500Principal(
        "C = US, O = Google\\, Inc, CN=Google Cloud SQL Signing CA foo:baz");
    X500Principal subject = new X500Principal("C = US, O = Google\\, Inc, CN=temporary-subject");

    JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(issuer,
        BigInteger.ONE, Date.from(notBefore.toInstant()), Date.from(notAfter.toInstant()), subject,
        Futures.getDone(clientKeyPair).getPublic());

    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

    Certificate cert = new JcaX509CertificateConverter().getCertificate(certificateHolder);

    StringBuilder sb = new StringBuilder();
    sb.append("-----BEGIN CERTIFICATE-----\n");
    sb.append(Base64.getEncoder().encodeToString(cert.getEncoded()).replaceAll("(.{64})", "$1\n"));
    sb.append("\n");
    sb.append("-----END CERTIFICATE-----\n");
    return sb.toString();
  }

  @Before
  public void setup()
      throws IOException, GeneralSecurityException, ExecutionException, OperatorCreationException {
    MockitoAnnotations.openMocks(this);

    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(
        decodeBase64StripWhitespace(TestKeys.CLIENT_PRIVATE_KEY));
    PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(
        decodeBase64StripWhitespace(TestKeys.CLIENT_PUBLIC_KEY));
    PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

    clientKeyPair = Futures.immediateFuture(new KeyPair(publicKey, privateKey));

    defaultExecutor = CoreSocketFactory.getDefaultExecutor();

    // Stub the API client for testing
    when(adminApi.connect()).thenReturn(adminApiConnect);

    // Stub when correct instance
    when(adminApiConnect.get(eq("myProject"), eq("myRegion~myInstance"))).thenReturn(
        adminApiConnectGet);

    when(adminApiConnect.generateEphemeralCert(anyString(), anyString(),
        isA(GenerateEphemeralCertRequest.class))).thenReturn(adminApiConnectGenerateEphemeralCert);

    when(adminApiConnectGet.execute()).thenReturn(new ConnectSettings().setBackendType("SECOND_GEN")
        .setIpAddresses(ImmutableList.of(new IpMapping().setIpAddress(PUBLIC_IP).setType("PRIMARY"),
            new IpMapping().setIpAddress(PRIVATE_IP).setType("PRIVATE")))
        .setServerCaCert(new SslCert().setCert(TestKeys.SERVER_CA_CERT)).setRegion("myRegion"));
    when(adminApiConnectGenerateEphemeralCert.execute()).thenReturn(generateEphemeralCertResponse);
    when(generateEphemeralCertResponse.getEphemeralCert()).thenReturn(
        new SslCert().setCert(createEphemeralCert(Duration.ofSeconds(0))));

    coreSocketFactoryStub = new CoreSocketFactory(clientKeyPair, adminApi, credentialFactory, 3307,
        defaultExecutor);
  }

}
