/*
 * Copyright 2024 Google LLC
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

import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

public class TestCertificateGenerator {
  public static final int DEFAULT_KEY_SIZE = 2048;
  private static final X500Name SERVER_CA_SUBJECT =
      new X500Name("C=US,O=Google\\, Inc,CN=Google Cloud SQL Root CA");
  private static final X500Name SERVER_INTERMEDIATE_CA_SUBJECT =
      new X500Name("C=US,O=Google\\, Inc,CN=Google Cloud SQL Intermediate CA");
  private static final X500Name SIGNING_CA_SUBJECT =
      new X500Name("C=US,O=Google\\, Inc,CN=Google Cloud SQL Signing CA foo:baz");

  private static final X500Name SERVER_CERT_SUBJECT =
      new X500Name("C=US,O=Google\\, Inc,CN=myProject:myInstance");
  private static final X500Name DOMAIN_SERVER_CERT_SUBJECT =
      new X500Name("C=US,O=Google\\, Inc,CN=example.com:myProject:myInstance");

  private final String SHA_256_WITH_RSA = "SHA256WithRSA";
  private final KeyPair signingCaKeyPair;
  private final KeyPair serverCaKeyPair;
  private final KeyPair serverIntermediateCaKeyPair;
  private final KeyPair domainServerKeyPair;
  private final KeyPair serverKeyPair;
  private final KeyPair clientKeyPair;
  private final X509Certificate signingCaCert;
  private final X509Certificate serverCaCert;
  private final X509Certificate serverIntemediateCaCert;
  private final X509Certificate serverCertificate;
  private final X509Certificate casServerCertificate;
  private final X509Certificate[] casServerCertificateChain;
  private final X509Certificate domainServerCertificate;

  private final String PEM_HEADER = "-----BEGIN CERTIFICATE-----";
  private final String PEM_FOOTER = "-----END CERTIFICATE-----";
  private final int PEM_LINE_LENGTH = 64;
  private final Instant ONE_YEAR_FROM_NOW = Instant.now().plus(365, ChronoUnit.DAYS);

  static KeyPair generateKeyPair() {
    KeyPairGenerator generator;
    try {
      generator = java.security.KeyPairGenerator.getInstance("RSA");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Missing RSA generator");
    }
    generator.initialize(DEFAULT_KEY_SIZE);

    return generator.generateKeyPair();
  }

  TestCertificateGenerator() {

    this.serverCaKeyPair = generateKeyPair();
    this.serverIntermediateCaKeyPair = generateKeyPair();
    this.signingCaKeyPair = generateKeyPair();
    this.serverKeyPair = generateKeyPair();
    this.clientKeyPair = generateKeyPair();
    this.domainServerKeyPair = generateKeyPair();

    try {
      this.serverCaCert = buildRootCertificate(SERVER_CA_SUBJECT, this.serverCaKeyPair);
      this.signingCaCert = buildRootCertificate(SIGNING_CA_SUBJECT, this.signingCaKeyPair);

      this.serverCertificate =
          buildSignedCertificate(
              SERVER_CERT_SUBJECT,
              serverKeyPair.getPublic(),
              SERVER_CA_SUBJECT,
              serverCaKeyPair.getPrivate(),
              ONE_YEAR_FROM_NOW,
              null);

      this.serverIntemediateCaCert =
          buildSignedCertificate(
              SERVER_INTERMEDIATE_CA_SUBJECT,
              serverIntermediateCaKeyPair.getPublic(),
              SERVER_CA_SUBJECT,
              serverCaKeyPair.getPrivate(),
              ONE_YEAR_FROM_NOW,
              null);

      this.casServerCertificate =
          buildSignedCertificate(
              SERVER_CERT_SUBJECT,
              serverKeyPair.getPublic(),
              SERVER_INTERMEDIATE_CA_SUBJECT,
              serverIntermediateCaKeyPair.getPrivate(),
              ONE_YEAR_FROM_NOW,
              Collections.singletonList(new GeneralName(GeneralName.dNSName, "db.example.com")));

      this.casServerCertificateChain =
          new X509Certificate[] {
            this.casServerCertificate, this.serverIntemediateCaCert, this.serverCaCert
          };

      this.domainServerCertificate =
          buildSignedCertificate(
              DOMAIN_SERVER_CERT_SUBJECT,
              domainServerKeyPair.getPublic(),
              SERVER_CA_SUBJECT,
              serverCaKeyPair.getPrivate(),
              ONE_YEAR_FROM_NOW,
              null);
    } catch (OperatorCreationException | CertificateException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public X509Certificate getCasServerCertificate() {
    return casServerCertificate;
  }

  public X509Certificate[] getCasServerCertificateChain() {
    return casServerCertificateChain;
  }

  public KeyPair getServerKeyPair() {
    return serverKeyPair;
  }

  public KeyPair getClientKeyPair() {
    return clientKeyPair;
  }

  public X509Certificate getSigningCaCert() {
    return signingCaCert;
  }

  public X509Certificate getServerCaCert() {
    return serverCaCert;
  }

  public KeyPair getDomainServerKeyPair() {
    return domainServerKeyPair;
  }

  public X509Certificate getDomainServerCertificate() {
    return domainServerCertificate;
  }

  public X509Certificate createEphemeralCert(String cn, Duration shiftIntoPast)
      throws GeneralSecurityException, ExecutionException, OperatorCreationException {
    Duration validFor = Duration.ofHours(1);
    ZonedDateTime notBefore = ZonedDateTime.now(ZoneId.of("UTC")).minus(shiftIntoPast);
    ZonedDateTime notAfter = notBefore.plus(validFor);

    final ContentSigner signer =
        new JcaContentSignerBuilder("SHA1withRSA").build(signingCaKeyPair.getPrivate());

    X500Principal issuer = TestKeys.getSigningCaCert().getSubjectX500Principal();
    X500Principal subject = new X500Principal("C = US,O = Google\\, Inc,CN=" + cn);

    JcaX509v3CertificateBuilder certificateBuilder =
        new JcaX509v3CertificateBuilder(
            issuer,
            BigInteger.ONE,
            Date.from(notBefore.toInstant()),
            Date.from(notAfter.toInstant()),
            subject,
            clientKeyPair.getPublic());

    X509CertificateHolder certificateHolder = certificateBuilder.build(signer);

    return new JcaX509CertificateConverter().getCertificate(certificateHolder);
  }

  /** Returns the PEM encoded certificate. */
  public String getPemForCert(X509Certificate certificate) {
    StringBuilder sb = new StringBuilder();
    sb.append(PEM_HEADER).append("\n");
    String base64Key = null;
    try {
      base64Key =
          BaseEncoding.base64()
              .withSeparator("\n", PEM_LINE_LENGTH)
              .encode(certificate.getEncoded());
    } catch (CertificateEncodingException e) {
      throw new RuntimeException("Unable to encode certificate.", e);
    }
    sb.append(base64Key).append("\n");
    sb.append(PEM_FOOTER).append("\n");
    return sb.toString();
  }

  /** Returns the ephemeral client certificate as signed by the intermediate certificate. */
  public X509Certificate getEphemeralCertificate(
      String cn, PublicKey connectorPublicKey, Instant notAfter) {

    List<GeneralName> sans = Collections.emptyList();

    // Example SAN records:
    // List<GeneralName> sans = Arrays.asList(
    //     new GeneralName(GeneralName.dNSName, "dns.example.com"),
    //     new GeneralName(GeneralName.iPAddress, "192.168.1.1")
    // );

    try {
      return buildSignedCertificate(
          new X500Name("CN=" + cn),
          connectorPublicKey,
          SIGNING_CA_SUBJECT,
          this.signingCaKeyPair.getPrivate(),
          notAfter,
          sans);
    } catch (CertIOException | CertificateException | OperatorCreationException e) {
      throw new RuntimeException("Unable to generate ephemeral certificate", e);
    }
  }

  /** Returns the server-side proxy key pair. */
  public KeyPair getServerKey() {
    return serverKeyPair;
  }

  /** Returns the client key pair. */
  public KeyPair getClientKey() {
    return clientKeyPair;
  }

  /** Returns the server-side proxy certificate. */
  public X509Certificate getServerCertificate() {
    return serverCertificate;
  }

  /** Creates a certificate with the given subject and signed by the root CA cert. */
  private X509Certificate buildSignedCertificate(
      X500Name subject,
      PublicKey subjectPublicKey,
      X500Name certificateIssuer,
      PrivateKey issuerPrivateKey,
      Instant notAfter,
      Collection<GeneralName> subjectAlternateNames)
      throws OperatorCreationException, CertIOException, CertificateException {
    PKCS10CertificationRequestBuilder pkcs10CertificationRequestBuilder =
        new JcaPKCS10CertificationRequestBuilder(subject, subjectPublicKey);
    JcaContentSignerBuilder contentSignerBuilder = new JcaContentSignerBuilder(SHA_256_WITH_RSA);
    ContentSigner csrContentSigner = contentSignerBuilder.build(issuerPrivateKey);
    PKCS10CertificationRequest csr = pkcs10CertificationRequestBuilder.build(csrContentSigner);

    X509v3CertificateBuilder certificateBuilder =
        new X509v3CertificateBuilder(
            certificateIssuer,
            new BigInteger(Long.toString(new SecureRandom().nextLong())),
            Date.from(Instant.now()),
            Date.from(notAfter),
            csr.getSubject(),
            csr.getSubjectPublicKeyInfo());

    certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    certificateBuilder.addExtension(
        Extension.keyUsage,
        false,
        new KeyUsage(KeyUsage.cRLSign | KeyUsage.keyCertSign | KeyUsage.digitalSignature));

    if (subjectAlternateNames != null && !subjectAlternateNames.isEmpty()) {
      GeneralName[] gn =
          subjectAlternateNames.toArray(new GeneralName[subjectAlternateNames.size()]);
      GeneralNames subjectAltNames = new GeneralNames(gn);
      certificateBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);
    }

    X509CertificateHolder certificateHolder = certificateBuilder.build(csrContentSigner);
    return new JcaX509CertificateConverter().getCertificate(certificateHolder);
  }

  /** Creates a self-signed certificate to serve as a root CA */
  private X509Certificate buildRootCertificate(X500Name subject, KeyPair rootKeyPair)
      throws OperatorCreationException, CertificateException, IOException {
    JcaX509v3CertificateBuilder certificateBuilder =
        new JcaX509v3CertificateBuilder(
            subject, // issuer is self
            new BigInteger(Long.toString(new SecureRandom().nextLong())),
            Date.from(Instant.now()),
            Date.from(Instant.now().plus(365, ChronoUnit.DAYS)),
            subject,
            rootKeyPair.getPublic());

    certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    certificateBuilder.addExtension(
        Extension.keyUsage, false, new KeyUsage(KeyUsage.cRLSign | KeyUsage.keyCertSign));

    ContentSigner contentSigner =
        new JcaContentSignerBuilder(SHA_256_WITH_RSA).build(rootKeyPair.getPrivate());

    X509CertificateHolder x509CertificateHolder = certificateBuilder.build(contentSigner);
    return new JcaX509CertificateConverter().getCertificate(x509CertificateHolder);
  }
}
