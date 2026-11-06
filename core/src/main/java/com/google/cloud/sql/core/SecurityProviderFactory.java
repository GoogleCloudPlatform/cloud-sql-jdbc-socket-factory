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
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.conscrypt.Conscrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves a {@link SecurityProviderLibrary} setting to the JSSE {@link Provider} instance that
 * serves the connector's {@link SSLContext}.
 *
 * <p>For {@link SecurityProviderLibrary#JDK}, {@link #resolve(SecurityProviderLibrary)} returns
 * {@code null} so the caller requests {@code SSLContext.getInstance("TLSv1.3")} using the JVM's
 * configured {@link java.security.Security#getProviders()} priority order as-is.
 *
 * <p>For {@link SecurityProviderLibrary#AUTO}, the factory first checks whether the JVM's default
 * {@code TLSv1.3} {@link SSLContext} already advertises the hybrid post-quantum key exchange group
 * {@code X25519MLKEM768} ({@code 0x11ec}) in its TLS {@code ClientHello} {@code supported_groups}
 * extension ({@code 0x000a}). When it does (for example on JDK 27+, JDK 25.0.5+ and other LTS
 * backports of JEP 527, user-registered {@code BCJSSE >= 1.81}, or globally installed Conscrypt),
 * {@link #resolve(SecurityProviderLibrary)} returns {@code null} without loading bundled Conscrypt.
 * Otherwise, it loads a connector-scoped Conscrypt {@link Provider} instance or falls back to
 * {@code null} when Conscrypt cannot load on the current platform.
 *
 * <p>When bundled Conscrypt is used, it is passed directly to {@code
 * SSLContext.getInstance(protocol, provider)} as a connector-owned {@link Provider} instance; the
 * JVM-global {@link java.security.Security} provider list is never modified.
 */
final class SecurityProviderFactory {

  private static final Logger logger = LoggerFactory.getLogger(SecurityProviderFactory.class);

  static final String TLS_1_3 = "TLSv1.3";

  // TLS record & ClientHello constants (RFC 8446 Sections 4.1.2, 4.2.7, and 5.1).
  private static final int TLS_HANDSHAKE_CONTENT_TYPE = 22;
  private static final int TLS_CLIENT_HELLO_HANDSHAKE_TYPE = 1;
  private static final int CLIENT_HELLO_SESSION_ID_OFFSET = 5 + 4 + 2 + 32; // 43
  private static final int EXT_SUPPORTED_GROUPS = 0x000a;
  private static final int NAMED_GROUP_X25519_MLKEM768 = 0x11ec;

  /**
   * Fail-closed TrustManager used solely to initialize a scratch {@link SSLContext} in-memory so
   * {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} can emit a {@code ClientHello} without reading
   * the JRE {@code cacerts} keystore from disk or failing on invalid {@code
   * javax.net.ssl.trustStore} system properties. Any attempt to validate a peer certificate throws
   * {@link CertificateException}.
   */
  private static final TrustManager[] PROBE_TRUST_MANAGERS =
      new TrustManager[] {
        new X509TrustManager() {
          @Override
          public void checkClientTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            throw new CertificateException(
                "In-memory ClientHello capability probe TrustManager must never validate peers");
          }

          @Override
          public void checkServerTrusted(X509Certificate[] chain, String authType)
              throws CertificateException {
            throw new CertificateException(
                "In-memory ClientHello capability probe TrustManager must never validate peers");
          }

          @Override
          public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }
        }
      };

  private static final AtomicBoolean AUTO_FALLBACK_ANNOUNCED = new AtomicBoolean();
  private static final AtomicBoolean DEFAULT_PQC_ANNOUNCED = new AtomicBoolean();
  private static final AtomicBoolean CONSCRYPT_ANNOUNCED = new AtomicBoolean();

  private static volatile CachedProbeResult cachedDefaultProbe;

  private SecurityProviderFactory() {}

  /**
   * Returns the connector-scoped JSSE provider to use for the given setting, or {@code null} to use
   * the JVM's default JSSE provider ({@code SSLContext.getInstance("TLSv1.3")}).
   *
   * @throws TerminalException when {@code securityProviderLibrary} is {@link
   *     SecurityProviderLibrary#CONSCRYPT} and Conscrypt cannot be loaded on this platform. This
   *     fails identically on every attempt, so it is not retried.
   */
  static Provider resolve(SecurityProviderLibrary securityProviderLibrary) {
    return resolve(
        securityProviderLibrary,
        SecurityProviderFactory::conscryptProvider,
        SecurityProviderFactory::defaultTls13SupportsX25519MlKem768);
  }

  /**
   * Resolves the provider decision. Both {@code defaultSupportsPqc} and {@code conscryptSupplier}
   * are evaluated lazily so {@code JDK} connections touch neither, and {@code AUTO} connections on
   * runtimes whose default TLS 1.3 provider already negotiates {@code X25519MLKEM768} never load
   * bundled Conscrypt's native library.
   */
  @VisibleForTesting
  static Provider resolve(
      SecurityProviderLibrary securityProviderLibrary,
      Supplier<Provider> conscryptSupplier,
      BooleanSupplier defaultSupportsPqc) {
    return resolve(securityProviderLibrary, conscryptSupplier, defaultSupportsPqc, logger);
  }

  @VisibleForTesting
  static Provider resolve(
      SecurityProviderLibrary securityProviderLibrary,
      Supplier<Provider> conscryptSupplier,
      BooleanSupplier defaultSupportsPqc,
      Logger log) {
    if (securityProviderLibrary == null || securityProviderLibrary == SecurityProviderLibrary.JDK) {
      return null;
    }

    if (securityProviderLibrary == SecurityProviderLibrary.AUTO
        && defaultSupportsPqc.getAsBoolean()) {
      if (DEFAULT_PQC_ANNOUNCED.compareAndSet(false, true)) {
        log.info(
            "{} is set to {} and the default TLSv1.3 JSSE provider already advertises"
                + " X25519MLKEM768. Using the default JSSE provider; Conscrypt is"
                + " not loaded. Set the {} property to {} to force bundled Conscrypt.",
            ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
            SecurityProviderLibrary.AUTO,
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
                    + " 64-bit Windows. Set %s to %s or %s to use the default JSSE provider.",
                ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
                SecurityProviderLibrary.CONSCRYPT,
                ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
                SecurityProviderLibrary.AUTO,
                SecurityProviderLibrary.JDK));
      }
      if (AUTO_FALLBACK_ANNOUNCED.compareAndSet(false, true)) {
        log.info(
            "{} is set to {}, but the default TLSv1.3 provider does not advertise"
                + " X25519MLKEM768 and Conscrypt is unavailable on this platform or not on the"
                + " classpath. Using the default JSSE provider, so Cloud SQL connections will"
                + " not use post-quantum key exchange.",
            ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY,
            SecurityProviderLibrary.AUTO);
      }
      return null;
    }

    if (CONSCRYPT_ANNOUNCED.compareAndSet(false, true)) {
      log.info(
          "Conscrypt will serve the Cloud SQL connections that request it with {}.",
          ConnectionConfig.CLOUD_SQL_TLS_PROVIDER_PROPERTY);
    }
    return conscrypt;
  }

  /**
   * Returns {@code true} if the JVM's default {@code TLSv1.3} {@link SSLContext} advertises {@code
   * X25519MLKEM768} ({@code 0x11ec}) in its {@code ClientHello} {@code supported_groups} extension.
   *
   * <p>The result is cached keyed on the default {@code TLSv1.3} {@link Provider} instance so
   * repeated checks avoid regenerating a {@code ClientHello} while automatically re-evaluating if
   * {@link java.security.Security#insertProviderAt(Provider, int)} changes the top TLS 1.3
   * provider.
   */
  static boolean defaultTls13SupportsX25519MlKem768() {
    try {
      SSLContext context = SSLContext.getInstance(TLS_1_3);
      Provider provider = context.getProvider();
      CachedProbeResult cached = cachedDefaultProbe;
      if (cached != null && cached.provider == provider) {
        return cached.supportsPqc;
      }
      context.init(null, PROBE_TRUST_MANAGERS, null);
      boolean supports = supportsX25519MlKem768(context);
      cachedDefaultProbe = new CachedProbeResult(provider, supports);
      return supports;
    } catch (Exception | LinkageError e) {
      logger.debug("Could not probe default TLSv1.3 provider for X25519MLKEM768: {}", e.toString());
      return false;
    }
  }

  /**
   * Returns {@code true} if {@code sslContext} can produce a {@code TLSv1.3} {@code ClientHello}
   * whose {@code supported_groups} ({@code 0x000a}) extension contains {@code X25519MLKEM768}
   * ({@code 0x11ec}).
   *
   * <p>Performs zero network or disk I/O and never mutates {@code sslContext}.
   */
  static boolean supportsX25519MlKem768(SSLContext sslContext) {
    if (sslContext == null) {
      return false;
    }
    String protocol = sslContext.getProtocol();
    if (!TLS_1_3.equals(protocol) && !"TLS".equals(protocol) && !"Default".equals(protocol)) {
      return false;
    }
    try {
      SSLEngine engine = createClientEngine(sslContext);
      SSLParameters params = engine.getSSLParameters();
      params.setProtocols(new String[] {TLS_1_3});
      engine.setSSLParameters(params);
      ByteBuffer packetBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
      engine.wrap(ByteBuffer.allocate(0), packetBuffer);
      packetBuffer.flip();
      return clientHelloAdvertisesX25519MlKem768(packetBuffer);
    } catch (Exception | LinkageError e) {
      logger.debug("TLSv1.3 ClientHello X25519MLKEM768 probe returned false: {}", e.toString());
      return false;
    }
  }

  private static SSLEngine createClientEngine(SSLContext sslContext)
      throws GeneralSecurityException {
    SSLEngine engine;
    try {
      engine = sslContext.createSSLEngine();
    } catch (IllegalStateException uninitialized) {
      // Caller passed an uninitialized SSLContext: initialize a scratch context from the same
      // provider so we never mutate the caller's SSLContext instance.
      SSLContext scratch =
          SSLContext.getInstance(sslContext.getProtocol(), sslContext.getProvider());
      scratch.init(null, PROBE_TRUST_MANAGERS, null);
      engine = scratch.createSSLEngine();
    }
    engine.setUseClientMode(true);
    return engine;
  }

  /**
   * Inspects a TLS record containing a {@code ClientHello} message and returns {@code true} if the
   * {@code supported_groups} extension ({@code 0x000a}) advertises {@code X25519MLKEM768} ({@code
   * 0x11ec}).
   */
  @VisibleForTesting
  static boolean clientHelloAdvertisesX25519MlKem768(ByteBuffer buf) {
    if (buf == null || buf.remaining() < CLIENT_HELLO_SESSION_ID_OFFSET + 1) {
      return false;
    }
    ByteBuffer slice = buf.slice();
    if ((slice.get(0) & 0xFF) != TLS_HANDSHAKE_CONTENT_TYPE
        || (slice.get(5) & 0xFF) != TLS_CLIENT_HELLO_HANDSHAKE_TYPE) {
      return false;
    }
    int recordLen = slice.getShort(3) & 0xFFFF;
    int handshakeLen =
        ((slice.get(6) & 0xFF) << 16) | ((slice.get(7) & 0xFF) << 8) | (slice.get(8) & 0xFF);
    int recordEnd = 5 + recordLen;
    int handshakeEnd = 9 + handshakeLen;
    if (handshakeEnd < CLIENT_HELLO_SESSION_ID_OFFSET + 1
        || handshakeEnd > recordEnd
        || recordEnd > slice.limit()) {
      return false;
    }

    slice.limit(handshakeEnd);
    slice.position(CLIENT_HELLO_SESSION_ID_OFFSET);

    int sessionIdLen = slice.get() & 0xFF;
    if (slice.remaining() < sessionIdLen + 2) {
      return false;
    }
    slice.position(slice.position() + sessionIdLen);

    int cipherSuitesLen = slice.getShort() & 0xFFFF;
    if (cipherSuitesLen < 2 || slice.remaining() < cipherSuitesLen + 1) {
      return false;
    }
    slice.position(slice.position() + cipherSuitesLen);

    int compressionMethodsLen = slice.get() & 0xFF;
    if (compressionMethodsLen < 1 || slice.remaining() < compressionMethodsLen + 2) {
      return false;
    }
    slice.position(slice.position() + compressionMethodsLen);

    int extensionsLen = slice.getShort() & 0xFFFF;
    if (slice.remaining() < extensionsLen) {
      return false;
    }
    slice.limit(slice.position() + extensionsLen);

    while (slice.remaining() >= 4) {
      int extType = slice.getShort() & 0xFFFF;
      int extLen = slice.getShort() & 0xFFFF;
      if (slice.remaining() < extLen) {
        return false;
      }
      if (extType == EXT_SUPPORTED_GROUPS) {
        if (extLen < 2) {
          return false;
        }
        int groupsByteLen = slice.getShort() & 0xFFFF;
        if (groupsByteLen > extLen - 2 || (groupsByteLen & 1) != 0) {
          return false;
        }
        for (int i = 0; i < groupsByteLen; i += 2) {
          if ((slice.getShort() & 0xFFFF) == NAMED_GROUP_X25519_MLKEM768) {
            return true;
          }
        }
        return false;
      }
      slice.position(slice.position() + extLen);
    }
    return false;
  }

  /** Returns true if the provider is a Conscrypt provider. */
  static boolean isConscrypt(Provider provider) {
    // Provider name is "Conscrypt", hardcoded string in the library source:
    // https://github.com/google/conscrypt/blob/655ad5069e1cb4d1989b8117eaf090371885af99/openjdk/src/main/java/org/conscrypt/Platform.java#L149
    return provider != null && "Conscrypt".equals(provider.getName());
  }

  /** Returns Conscrypt's provider, or {@code null} when it cannot be used on this platform. */
  @VisibleForTesting
  static Provider conscryptProvider() {
    return ConscryptHolder.PROVIDER;
  }

  private static final class CachedProbeResult {
    final Provider provider;
    final boolean supportsPqc;

    CachedProbeResult(Provider provider, boolean supportsPqc) {
      this.provider = provider;
      this.supportsPqc = supportsPqc;
    }
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
