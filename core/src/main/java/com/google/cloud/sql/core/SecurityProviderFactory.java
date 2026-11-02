/*
 * Copyright 2026 Google LLC
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

import com.google.cloud.sql.SecurityProviderLibrary;
import com.google.common.annotations.VisibleForTesting;
import java.security.Provider;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link SecurityProviderLibrary} setting to the JSSE {@link Provider} instance that
 * serves the connector's {@link javax.net.ssl.SSLContext}.
 *
 * <p>Conscrypt is used as a connector-owned {@link Provider} instance passed directly to {@code
 * SSLContext.getInstance(protocol, provider)}. The JVM-global {@link java.security.Security}
 * provider list is never consulted or modified.
 */
final class SecurityProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(SecurityProviderFactory.class);

  static final String TLS_1_3 = "TLSv1.3";

  // The first JDK whose own JSSE provider negotiates ML-KEM hybrid key exchange (JEP 527).
  private static final int FIRST_PQC_JRE = 27;
  private static final int JRE_VERSION =
      parseJreVersion(System.getProperty("java.specification.version", ""));

  private static final AtomicBoolean AUTO_FALLBACK_ANNOUNCED = new AtomicBoolean();
  private static final AtomicBoolean JRE_PQC_ANNOUNCED = new AtomicBoolean();
  private static final AtomicBoolean CONSCRYPT_ANNOUNCED = new AtomicBoolean();

  private SecurityProviderFactory() {}

  /**
   * Returns the JSSE provider to use for the given setting, or {@code null} for the JRE's default
   * provider.
   *
   * @throws TerminalException when {@code securityProviderLibrary} is {@link
   *     SecurityProviderLibrary#CONSCRYPT} and Conscrypt cannot be loaded on this platform. This
   *     fails identically on every attempt, so it is not retried.
   */
  static Provider resolve(SecurityProviderLibrary securityProviderLibrary) {
    return resolve(
        securityProviderLibrary, SecurityProviderFactory::conscryptProvider, JRE_VERSION);
  }

  /**
   * The provider decision. Conscrypt is supplied lazily because resolving it loads a native
   * library: a JDK connection, or an AUTO one on a JRE that needs no help, never loads it.
   */
  @VisibleForTesting
  static Provider resolve(
      SecurityProviderLibrary securityProviderLibrary,
      Supplier<Provider> conscryptSupplier,
      int jreVersion) {
    if (securityProviderLibrary == null || securityProviderLibrary == SecurityProviderLibrary.JDK) {
      return null;
    }

    if (securityProviderLibrary == SecurityProviderLibrary.AUTO && jreVersion >= FIRST_PQC_JRE) {
      if (JRE_PQC_ANNOUNCED.compareAndSet(false, true)) {
        logger.info(
            "{} is set to {} and this JRE reports Java {}, which negotiates post-quantum key"
                + " exchange natively (JEP 527). Using the default JSSE provider; Conscrypt is"
                + " not loaded. Set the {} property to {} if this runtime does not negotiate"
                + " ML-KEM.",
            ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
            SecurityProviderLibrary.AUTO,
            jreVersion,
            ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
            SecurityProviderLibrary.CONSCRYPT);
      }
      return null;
    }

    Provider conscrypt = conscryptSupplier.get();
    if (conscrypt == null
        || conscrypt.getService("SSLContext", TLS_1_3) == null
        || conscrypt.getService("KeyManagerFactory", "PKIX") == null) {
      if (securityProviderLibrary == SecurityProviderLibrary.CONSCRYPT) {
        throw new TerminalException(
            String.format(
                "%s is set to %s, but Conscrypt is unavailable on this platform or the"
                    + " org.conscrypt:conscrypt-openjdk-uber dependency is not on the classpath"
                    + " (it is optional on JDK 27+; add it explicitly). Conscrypt publishes"
                    + " native libraries for glibc Linux and macOS on x86-64 and ARM64, and for"
                    + " 64-bit Windows. Set %s to %s or %s to use the JRE default provider.",
                ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
                SecurityProviderLibrary.CONSCRYPT,
                ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
                SecurityProviderLibrary.AUTO,
                SecurityProviderLibrary.JDK));
      }
      if (AUTO_FALLBACK_ANNOUNCED.compareAndSet(false, true)) {
        logger.info(
            "{} is set to {}, but Conscrypt is unavailable on this platform or not on the"
                + " classpath. Using the default JSSE provider, so Cloud SQL connections will"
                + " not use post-quantum key exchange.",
            ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
            SecurityProviderLibrary.AUTO);
      }
      return null;
    }

    if (CONSCRYPT_ANNOUNCED.compareAndSet(false, true)) {
      logger.info(
          "Conscrypt will serve the Cloud SQL connections that request it with {}.",
          ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY);
    }
    return conscrypt;
  }

  /** Returns true if the provider is a Conscrypt provider. */
  static boolean isConscrypt(Provider provider) {
    // Provider name is "Conscrypt", hardcoded string in the library source:
    // https://github.com/google/conscrypt/blob/655ad5069e1cb4d1989b8117eaf090371885af99/openjdk/src/main/java/org/conscrypt/Platform.java#L149
    return provider != null && "Conscrypt".equals(provider.getName());
  }

  /**
   * Parses a {@code java.specification.version} value into a major version: 8 for "1.8", and the
   * number itself from Java 9 on. An unparseable value yields 0, which AUTO treats as a JRE without
   * ML-KEM support.
   */
  @VisibleForTesting
  static int parseJreVersion(String version) {
    String major = version.startsWith("1.") ? version.substring(2) : version;
    try {
      return Integer.parseInt(major);
    } catch (NumberFormatException e) {
      logger.debug("Could not parse java.specification.version '{}'.", version);
      return 0;
    }
  }

  /** Returns Conscrypt's provider, or {@code null} when it cannot be used on this platform. */
  @VisibleForTesting
  static Provider conscryptProvider() {
    return ConscryptHolder.PROVIDER;
  }

  /**
   * Holds the connector's Conscrypt provider, resolved once per JVM on first use. Class
   * initialization gives exactly-once resolution and safe publication without locking.
   */
  private static final class ConscryptHolder {
    static final Provider PROVIDER = newConscryptProvider();

    private ConscryptHolder() {}
  }

  private static Provider newConscryptProvider() {
    try {
      if (!Conscrypt.isAvailable()) {
        // Conscrypt's native library is not published for this platform (e.g. musl, 32-bit,
        // s390x, ppc64le) or cannot load (e.g. GraalVM native image).
        logger.debug("Conscrypt's native library is not available on this platform.");
        return null;
      }
      return Conscrypt.newProvider();
    } catch (LinkageError e) {
      // Conscrypt is referenced directly rather than via Class.forName, so when the optional/
      // excluded Conscrypt jar is absent from the classpath (e.g. on JDK 27+) the JVM throws
      // NoClassDefFoundError (a LinkageError, not checked ClassNotFoundException). LinkageError
      // also covers UnsatisfiedLinkError, while Conscrypt.isAvailable() catches Throwable itself.
      logger.debug("Conscrypt could not be loaded: {}", e.toString());
      return null;
    }
  }
}
