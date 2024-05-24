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

import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * This is a workaround for a known bug in Conscrypt crypto in how it handles X509 auth type.
 * OpenJDK interpres the X509 certificate auth type as "UNKNOWN" while Conscrypt interpret the same
 * certificate as auth type "GENERIC". This incompatibility causes problems in the JDK.
 *
 * <p>This adapter works around the issue by creating wrappers around all TrustManager instances. It
 * replaces "GENERIC" auth type with "UNKNOWN" auth type before delegating calls.
 *
 * <p>In the JVM, we need to implement 3 classes to make sure that we are capturing all of the
 * TrustManager instances created when Conscrypt is the Java Crypto provider so that we can wrap
 * them with the ConscryptWorkaroundTrustManager:
 *
 * <p>class ConscryptWorkaroundTrustManagerFactory extends TrustManagerFactory - has a bunch of
 * final methods that delegate to a TrustManagerFactorySpi. class
 * ConscryptWorkaroundTrustManagerFactorySpi implements TrustManagerFactorySpi - can actually
 * intercept and delegate calls related to trust managers and wrap them with
 * ConscryptWorkaroundTrustManager ConscryptWorkaroundTrustManager - the workaround for the
 * Conscrypt bug.
 *
 * <p>See https://github.com/google/conscrypt/issues/1033#issuecomment-982701272
 */
class ConscryptWorkaroundTrustManagerFactory extends TrustManagerFactory {
  private static final boolean CONSCRYPT_TLS;

  static {
    // Provider name is "Conscrypt", hardcoded string in the library source:
    // https://github.com/google/conscrypt/blob/655ad5069e1cb4d1989b8117eaf090371885af99/openjdk/src/main/java/org/conscrypt/Platform.java#L149
    Provider p = Security.getProvider("Conscrypt");
    if (p != null) {
      try {
        SSLContext ctx = SSLContext.getInstance("TLS");
        Provider prov = ctx.getProvider();
        CONSCRYPT_TLS = "Conscrypt".equals(prov.getName());
      } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("Unable to load algorithm TLS", e);
      }
    } else {
      CONSCRYPT_TLS = false;
    }
  }

  /** Returns true if the Conscrypt Java Crypto Extension is installed. */
  static boolean isWorkaroundNeeded() {
    return CONSCRYPT_TLS;
  }

  static ConscryptWorkaroundTrustManagerFactory newInstance() throws NoSuchAlgorithmException {
    TrustManagerFactory delegate = TrustManagerFactory.getInstance("X.509");
    return new ConscryptWorkaroundTrustManagerFactory(delegate);
  }

  private ConscryptWorkaroundTrustManagerFactory(TrustManagerFactory delegate) {
    super(
        new ConscryptWorkaroundTrustManagerFactorySpi(delegate),
        delegate.getProvider(),
        delegate.getAlgorithm());
  }
}
