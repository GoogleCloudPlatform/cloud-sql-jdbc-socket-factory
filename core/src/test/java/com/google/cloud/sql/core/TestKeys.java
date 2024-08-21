/*
 * Copyright 2016 Google Inc.
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

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Test keys for TLS tests. These keys are checked in to core/src/main/resources/certs.
 *
 * <p>!!! Do not manually update the certificate files directly !!!
 *
 * <p>core/src/main/resources/update_test_certs.sh generates the certificates used in the tests. To
 * maintain repeatable, maintainable certificate tests, please edit this script if you need to
 * change the configuration of these test certificates.
 */
public class TestKeys {
  private static final TestCertificateGenerator certs;

  public static X509Certificate getSigningCaCert() {
    return certs.getSigningCaCert();
  }

  public static KeyPair getClientKeyPair() {
    return certs.getClientKeyPair();
  }

  public static X509Certificate getServerCert() {
    return certs.getServerCertificate();
  }

  public static String getServerCertPem() {
    return certs.getPemForCert(certs.getServerCertificate());
  }

  public static KeyPair getServerKeyPair() {
    return certs.getServerKeyPair();
  }

  static {
    certs = new TestCertificateGenerator();
  }

  public static String createEphemeralCert(Duration certDuration) {
    ZonedDateTime notBefore = ZonedDateTime.now(ZoneId.of("UTC")).minus(certDuration);
    ZonedDateTime notAfter = notBefore.plus(Duration.ofHours(1));

    return certs.getPemForCert(
        certs.getEphemeralCertificate(
            "temporary-cert", certs.getClientKey().getPublic(), notAfter.toInstant()));
  }

  public static KeyPair getDomainServerKeyPair() {
    return certs.getDomainServerKeyPair();
  }

  public static X509Certificate getDomainServerCert() {
    return certs.getDomainServerCertificate();
  }

  public static String getDomainServerCertPem() {
    return certs.getPemForCert(certs.getDomainServerCertificate());
  }

  public static X509Certificate[] getCasServerCertChain() {
    return certs.getCasServerCertificateChain();
  }

  public static String getCasServerCertChainPem() {
    StringBuilder s = new StringBuilder();
    for (X509Certificate c : certs.getCasServerCertificateChain()) {
      if (s.length() > 0) {
        s.append("\n");
      }
      s.append(certs.getPemForCert(c));
    }
    return s.toString();
  }
}
