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

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import javax.net.ssl.TrustManagerFactory;

/**
 * Implement custom server certificate trust checks specific to Cloud SQL.
 *
 * <p>In the JVM, we need to implement 3 classes to make sure that we are capturing all the
 * TrustManager instances created by the Java Crypto provider so that the connector will:
 *
 * <p>class InstanceCheckingTrustManagerFactory extends TrustManagerFactory - has a bunch of final
 * methods that delegate to a TrustManagerFactorySpi.
 *
 * <p>class InstanceCheckingTrustManagerFactorySpi implements TrustManagerFactorySpi - can actually
 * intercept requests for the list of TrustManager instances and wrap them with our replacement
 * TrustManager.
 *
 * <p>class ConscryptWorkaroundTrustManager - the workaround for the Conscrypt bug.
 *
 * <p>class InstanceCheckingTrustManager - delegates TLS checks to the default provider and then
 * checks that the Subject CN field contains the Cloud SQL instance ID.
 */
class InstanceCheckingTrustManagerFactory extends TrustManagerFactory {

  static InstanceCheckingTrustManagerFactory newInstance(
      CloudSqlInstanceName instanceName, InstanceMetadata instanceMetadata)
      throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {

    TrustManagerFactory delegate = TrustManagerFactory.getInstance("X.509");
    KeyStore trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustedKeyStore.load(null, null);
    trustedKeyStore.setCertificateEntry("instance", instanceMetadata.getInstanceCaCertificate());

    InstanceCheckingTrustManagerFactory tmf =
        new InstanceCheckingTrustManagerFactory(instanceName, delegate);

    tmf.init(trustedKeyStore);

    return tmf;
  }

  private InstanceCheckingTrustManagerFactory(
      CloudSqlInstanceName instanceName, TrustManagerFactory delegate) {
    super(
        new InstanceCheckingTrustManagerFactorySpi(instanceName, delegate),
        delegate.getProvider(),
        delegate.getAlgorithm());
  }
}
