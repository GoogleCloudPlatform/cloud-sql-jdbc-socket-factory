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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
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
  private final CloudSqlInstanceName instanceName;

  public InstanceCheckingTrustManger(
      CloudSqlInstanceName instanceName, X509ExtendedTrustManager tm) {
    this.instanceName = instanceName;
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
    String instName = this.instanceName.getProjectId() + ":" + this.instanceName.getInstanceId();
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
