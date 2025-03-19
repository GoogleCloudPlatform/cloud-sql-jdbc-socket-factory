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
 * This is a workaround for a known bug in Conscrypt crypto in how it handles X509 auth type.
 * OpenJDK interpres the X509 certificate auth type as "UNKNOWN" while Conscrypt interpret the same
 * certificate as auth type "GENERIC". This incompatibility causes problems in the JDK.
 *
 * <p>This adapter works around the issue by creating wrappers around all TrustManager instances. It
 * replaces "GENERIC" auth type with "UNKNOWN" auth type before delegating calls.
 *
 * <p>See https://github.com/google/conscrypt/issues/1033#issuecomment-982701272
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

    // If the instance metadata does not contain a domain name, use legacy CN validation
    if (Strings.isNullOrEmpty(instanceMetadata.getDnsName())) {
      checkCn(chain);
    } else {
      // If there is a DNS name, check the Subject Alternative Names.
      checkSan(chain);
    }
  }

  private void checkSan(X509Certificate[] chain) throws CertificateException {
    final String dns;
    if (!Strings.isNullOrEmpty(instanceMetadata.getInstanceName().getDomainName())) {
      // If the connector is configured using a DNS name, validate the DNS name from the connector
      // config.
      dns = instanceMetadata.getInstanceName().getDomainName();
    } else {
      // If the connector is configured with an instance name, validate the DNS name from
      // the instance metadata.
      dns = instanceMetadata.getDnsName();
    }

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
    throw new CertificateException(
        "Server certificate does not contain expected name '"
            + dns
            + "' for Cloud SQL instance "
            + instanceMetadata.getInstanceName());
  }

  private List<String> getSans(X509Certificate cert) throws CertificateException {
    ArrayList<String> names = new ArrayList<>();

    Collection<List<?>> sanAsn1Field = cert.getSubjectAlternativeNames();
    if (sanAsn1Field == null) {
      return names;
    }

    for (List item : sanAsn1Field) {
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
