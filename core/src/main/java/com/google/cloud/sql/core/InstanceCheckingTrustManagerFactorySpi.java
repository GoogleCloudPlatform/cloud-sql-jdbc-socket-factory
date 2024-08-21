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

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Part of the InstanceCheckingTrustManagerFactory that implements custom CloudSQL server
 * certificate checks.
 */
class InstanceCheckingTrustManagerFactorySpi extends TrustManagerFactorySpi {
  private final TrustManagerFactory delegate;
  private final InstanceMetadata instanceMetadata;

  InstanceCheckingTrustManagerFactorySpi(
      InstanceMetadata instanceMetadata, TrustManagerFactory delegate) {
    this.instanceMetadata = instanceMetadata;
    this.delegate = delegate;
  }

  @Override
  protected void engineInit(KeyStore ks) throws KeyStoreException {
    delegate.init(ks);
  }

  @Override
  protected void engineInit(ManagerFactoryParameters spec)
      throws InvalidAlgorithmParameterException {
    delegate.init(spec);
  }

  @Override
  protected TrustManager[] engineGetTrustManagers() {
    TrustManager[] tms = delegate.getTrustManagers();
    TrustManager[] delegates = new TrustManager[tms.length];
    for (int i = 0; i < tms.length; i++) {
      if (tms[i] instanceof X509ExtendedTrustManager) {
        X509ExtendedTrustManager tm = (X509ExtendedTrustManager) tms[i];

        // Note: This is a workaround for Conscrypt bug #1033
        // Conscrypt is the JCE provider on some Google Cloud runtimes like DataProc.
        // https://github.com/google/conscrypt/issues/1033
        if (ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded()) {
          tm = new ConscryptWorkaroundDelegatingTrustManger(tm);
        }

        delegates[i] = new InstanceCheckingTrustManger(instanceMetadata, tm);
      } else {
        delegates[i] = tms[i];
      }
    }
    return delegates;
  }
}
