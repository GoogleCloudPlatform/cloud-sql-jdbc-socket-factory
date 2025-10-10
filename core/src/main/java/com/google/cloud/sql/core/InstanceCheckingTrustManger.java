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

import com.google.common.base.Strings;
import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * InstanceCheckingTrustManger implements custom TLS verification logic to gracefully and securely
 * handle deviations from standard TLS hostname verification in existing Cloud SQL instance server
 * certificates.
 *
 * <p>This is the verification algorithm:
 *
 * <ol>
 *   <li>Verify the server cert CA, using the CA certs from the instance metadata. Reject the
 *       certificate if the CA is invalid. This is delegated to the default JSSE TLS provider.
 *   <li>Check that the server cert contains a SubjectAlternativeName matching the DNS name in the
 *       connector configuration OR the DNS Name from the instance metadata
 *   <li>If the SubjectAlternativeName does not match, and if the server cert Subject.CN field is
 *       not empty, check that the Subject.CN field contains the instance name. Reject the
 *       certificate if both the #2 SAN check and #3 CN checks fail.
 * </ol>
 *
 * <p>To summarize the deviations from standard TLS hostname verification:
 *
 * <p>Historically, Cloud SQL creates server certificates with the instance name in the Subject.CN
 * field in the format "my-project:my-instance". The connector is expected to check that the
 * instance name that the connector was configured to dial matches the server certificate Subject.CN
 * field. Thus, the Subject.CN field for most Cloud SQL instances does not contain a well-formed DNS
 * Name. This breaks standard TLS hostname verification.
 *
 * <p>Also, there are times when the instance metadata reports that an instance has a DNS name, but
 * that DNS name does not yet appear in the SAN records of the server certificate. The client should
 * fall back to validating the hostname using the instance name in the Subject.CN field.
 */
class InstanceCheckingTrustManger extends X509ExtendedTrustManager {
  private final X509ExtendedTrustManager tm;
  private final InstanceMetadata instanceMetadata;

  public InstanceCheckingTrustManger(
      InstanceMetadata instanceMetadata, X509ExtendedTrustManager tm) {
    this.instanceMetadata = instanceMetadata;
    this.tm = tm;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    tm.checkClientTrusted(chain, authType, socket);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    tm.checkClientTrusted(chain, authType, engine);
  }

  @Override
  public void checkClientTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    tm.checkClientTrusted(chain, authType);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
      throws CertificateException {
    tm.checkServerTrusted(chain, authType, socket);
    checkCertificateChain(chain);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
      throws CertificateException {
    tm.checkServerTrusted(chain, authType, engine);
    checkCertificateChain(chain);
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    tm.checkServerTrusted(chain, authType);
    checkCertificateChain(chain);
  }

  private void checkCertificateChain(X509Certificate[] chain) throws CertificateException {
    if (chain.length == 0) {
      throw new CertificateException("No server certificates in chain");
    }
    if (chain[0].getSubjectX500Principal() == null) {
      throw new CertificateException("Subject is missing");
    }

    final String dns;
    if (!Strings.isNullOrEmpty(instanceMetadata.getInstanceName().getDomainName())) {
      // If the connector is configured using a DNS name, validate the DNS name from the connector
      // config.
      dns = instanceMetadata.getInstanceName().getDomainName();
    } else if (!Strings.isNullOrEmpty(instanceMetadata.getDnsName())) {
      // If the connector is configured with an instance name, validate the DNS name from
      // the instance metadata.
      dns = instanceMetadata.getDnsName();
    } else {
      dns = null;
    }

    // If the instance metadata does not contain a domain name, and the connector was not
    // configured with a domain name, use legacy CN validation.
    if (dns == null) {
      checkCn(chain);
    } else {
      // If there is a DNS name, check the Subject Alternative Names.
      checkSan(dns, chain);
    }
  }

  private void checkSan(String dns, X509Certificate[] chain) throws CertificateException {

    if (Strings.isNullOrEmpty(dns)) {
      throw new CertificateException(
          "Instance metadata for " + instanceMetadata.getInstanceName() + " has an empty dnsName");
    }

    List<String> sans = getSans(chain[0]);
    for (String san : sans) {
      if (san.equalsIgnoreCase(dns)) {
        return;
      }
    }
    try {
      checkCn(chain);
    } catch (CertificateException e) {
      throw new CertificateException(
          "Server certificate does not contain expected name '"
              + dns
              + "' for Cloud SQL instance "
              + instanceMetadata.getInstanceName());
    }
  }

  private List<String> getSans(X509Certificate cert) throws CertificateException {
    ArrayList<String> names = new ArrayList<>();

    Collection<List<?>> sanAsn1Field = cert.getSubjectAlternativeNames();
    if (sanAsn1Field == null) {
      return names;
    }

    for (List<?> item : sanAsn1Field) {
      Integer type = (Integer) item.get(0);
      // RFC 5280 section 4.2.1.6.  "Subject Alternative Name"
      // describes the structure of subjectAlternativeName record.
      //   type == 0 means this contains an "otherName"
      //   type == 2 means this contains a "dNSName"
      if (type == 0 || type == 2) {
        Object value = item.get(1);
        if (value instanceof byte[]) {
          // This would only happen if the customer provided a non-standard JSSE encryption
          // provider. The standard JSSE providers all return a list of Strings for the SAN.
          // To handle this case, the project would need to add the BouncyCastle crypto library
          // as a dependency, and follow the example to decode an ASN1 SAN data structure:
          // https://stackoverflow.com/questions/30993879/retrieve-subject-alternative-names-of-x-509-certificate-in-java
          throw new UnsupportedOperationException(
              "Server certificate SAN field cannot be decoded.");
        } else if (value instanceof String) {
          names.add((String) value);
        }
      }
    }
    return names;
  }

  private void checkCn(X509Certificate[] chain) throws CertificateException {

    String cn = null;

    try {
      String subject = chain[0].getSubjectX500Principal().getName();
      LdapName subjectName = new LdapName(subject);
      for (Rdn rdn : subjectName.getRdns()) {
        if ("CN".equals(rdn.getType())) {
          cn = (String) rdn.getValue();
        }
      }
    } catch (InvalidNameException e) {
      throw new CertificateException("Exception parsing the server certificate subject field", e);
    }

    if (cn == null) {
      throw new CertificateException("Server certificate subject does not contain a value for CN");
    }

    // parse CN from subject. CN always comes last in the list.
    String instName =
        this.instanceMetadata.getInstanceName().getProjectId()
            + ":"
            + this.instanceMetadata.getInstanceName().getInstanceId();
    if (!instName.equals(cn)) {
      throw new CertificateException(
          "Server certificate CN does not match instance name. Server certificate CN="
              + cn
              + " Expected instance name: "
              + instName);
    }
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return tm.getAcceptedIssuers();
  }
}
