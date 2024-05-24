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
 * This is a workaround for a known bug in Conscrypt crypto in how it handles X509 auth type.
 * OpenJDK interpres the X509 certificate auth type as "UNKNOWN" while Conscrypt interpret the same
 * certificate as auth type "GENERIC". This incompatibility causes problems in the JDK.
 *
 * <p>This adapter works around the issue by creating wrappers around all TrustManager instances. It
 * replaces "GENERIC" auth type with "UNKNOWN" auth type before delegating calls.
 *
 * <p>See https://github.com/google/conscrypt/issues/1033#issuecomment-982701272
 */
class ConscryptWorkaroundTrustManagerFactorySpi extends TrustManagerFactorySpi {
  private final TrustManagerFactory delegate;

  ConscryptWorkaroundTrustManagerFactorySpi(TrustManagerFactory delegate) {
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
        delegates[i] =
            new ConscryptWorkaroundDelegatingTrustManger((X509ExtendedTrustManager) tms[i]);
      } else {

        delegates[i] = tms[i];
      }
    }
    return delegates;
  }
}
