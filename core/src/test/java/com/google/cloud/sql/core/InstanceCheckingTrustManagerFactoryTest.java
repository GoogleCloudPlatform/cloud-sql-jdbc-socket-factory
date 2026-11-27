/*
 * Copyright 2025 Google LLC
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

import static org.junit.Assert.assertThrows;

import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstanceCheckingTrustManagerFactoryTest {

  private static TestCertificateGenerator generator;
  private final TestCase tc;

  @BeforeClass
  public static void beforeClass() {
    generator = new TestCertificateGenerator();
  }

  public InstanceCheckingTrustManagerFactoryTest(TestCase tc) {
    this.tc = tc;
  }

  @Test
  public void testValidateCertificate() throws Exception {

    List<Certificate> caCerts;
    X509Certificate[] serverCert;
    if (tc.cas) {
      caCerts = Arrays.asList(generator.getCasServerCertificateChain());
      caCerts = caCerts.subList(1, caCerts.size());
      serverCert = generator.createServerCertificate(tc.cn, tc.san, true);
    } else {
      caCerts = Collections.singletonList(generator.getServerCaCert());
      serverCert = generator.createServerCertificate(tc.cn, tc.san, false);
    }

    InstanceMetadata instanceMetadata =
        new InstanceMetadata(
            new CloudSqlInstanceName(tc.icn, tc.serverName),
            Collections.emptyMap(),
            caCerts,
            false,
            tc.metadataDnsName,
            false,
            null);

    InstanceCheckingTrustManagerFactory f =
        InstanceCheckingTrustManagerFactory.newInstance(instanceMetadata, false);
    InstanceCheckingTrustManger tm = (InstanceCheckingTrustManger) f.getTrustManagers()[0];

    if (tc.valid) {
      tm.checkServerTrusted(serverCert, "UNKNOWN");
    } else {
      assertThrows(
          CertificateException.class,
          () -> {
            tm.checkServerTrusted(serverCert, "UNKNOWN");
          });
    }
  }

  @Parameters(name = "{index}: {0}")
  public static List<TestCase> testCases() {
    List<TestCase> cases =
        Arrays.asList(
            new TestCase(
                "cn match",
                null,
                "myProject:myRegion:myInstance",
                "myProject:myInstance",
                null,
                true),
            new TestCase(
                "cn no match",
                null,
                "myProject:myRegion:badInstance",
                "myProject:myInstance",
                null,
                false),
            new TestCase(
                "cn empty", null, "myProject:myRegion:myInstance", "db.example.com", null, false),
            new TestCase(
                "san match",
                "db.example.com",
                "myProject:myRegion:myInstance",
                null,
                "db.example.com",
                true),
            new TestCase(
                "san no match",
                "bad.example.com",
                "myProject:myRegion:myInstance",
                null,
                "db.example.com",
                false),
            new TestCase(
                "san empty match",
                "empty.example.com",
                "myProject:myRegion:myInstance",
                "",
                null,
                false),
            new TestCase(
                "san match with cn present",
                "db.example.com",
                "myProject:myRegion:myInstance",
                "myProject:myInstance",
                "db.example.com",
                true),
            new TestCase(
                "san no match fallback to cn",
                "db.example.com",
                "myProject:myRegion:myInstance",
                "myProject:myInstance",
                "other.example.com",
                true),
            new TestCase(
                "san empty match fallback to cn",
                "db.example.com",
                "myProject:myRegion:myInstance",
                "myProject:myInstance",
                null,
                true),
            new TestCase(
                "san no match fallback to cn and fail",
                "db.example.com",
                "myProject:myRegion:badInstance",
                "other.example.com",
                "myProject:myInstance",
                false),
            new TestCase(
                "dotless config domain matches trailing-dot san without cn fallback",
                "abc123.us-central1.sql.goog",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "abc123.us-central1.sql.goog.",
                true),
            new TestCase(
                "trailing-dot config domain matches dotless san without cn fallback",
                "abc123.us-central1.sql.goog.",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "abc123.us-central1.sql.goog",
                true),
            new TestCase(
                "case-insensitive dotless config domain matches trailing-dot san",
                "abc123.us-central1.sql.goog",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "ABC123.US-CENTRAL1.SQL.GOOG.",
                true),
            new TestCase(
                "dotless metadata dnsName matches trailing-dot san without cn fallback",
                null,
                "abc123.us-central1.sql.goog",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "abc123.us-central1.sql.goog.",
                true,
                false),
            new TestCase(
                "trailing-dot metadata dnsName matches dotless san without cn fallback",
                null,
                "abc123.us-central1.sql.goog.",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "abc123.us-central1.sql.goog",
                true,
                false),
            new TestCase(
                "double trailing-dot san does not match dotless config domain",
                "abc123.us-central1.sql.goog",
                "myProject:myRegion:myInstance",
                "wrongProject:wrongInstance",
                "abc123.us-central1.sql.goog..",
                false),
            new TestCase(
                "single-dot metadata dnsName fails closed without cn fallback",
                null,
                ".",
                "myProject:myRegion:myInstance",
                "myProject:myInstance",
                ".",
                false,
                false));
    List<TestCase> casesWithCas = new ArrayList<>(cases);
    for (TestCase tc : cases) {
      casesWithCas.add(tc.withCas(true));
    }
    return casesWithCas;
  }

  private static class TestCase {
    /** Testcase description. */
    private final String desc;

    /** connector configuration domain name. */
    private final String serverName;

    /** instance metadata dnsName. */
    private final String metadataDnsName;

    /** connector configuration instance name. */
    private final String icn;

    /** server cert CN field value. */
    private final String cn;

    /** server cert SAN field value. */
    private final String san;

    /** wants validation to succeed. */
    private final boolean valid;

    private final boolean cas;

    private TestCase(
        String desc, String serverName, String icn, String cn, String san, boolean valid) {
      this(desc, serverName, null, icn, cn, san, valid, false);
    }

    private TestCase(
        String desc,
        String serverName,
        String metadataDnsName,
        String icn,
        String cn,
        String san,
        boolean valid,
        boolean cas) {
      this.desc = desc;
      this.serverName = serverName;
      this.metadataDnsName = metadataDnsName;
      this.icn = icn;
      this.cn = cn;
      this.san = san;
      this.valid = valid;
      this.cas = cas;
    }

    @Override
    public String toString() {
      return desc;
    }

    private TestCase withCas(boolean cas) {
      return new TestCase(
          this.desc,
          this.serverName,
          this.metadataDnsName,
          this.icn,
          this.cn,
          this.san,
          this.valid,
          cas);
    }
  }
}
