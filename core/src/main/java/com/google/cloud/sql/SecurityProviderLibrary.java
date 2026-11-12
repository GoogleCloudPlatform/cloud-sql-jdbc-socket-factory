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

package com.google.cloud.sql;

import java.util.Locale;

/**
 * Selects the JSSE provider library that the connector uses for the mTLS connection to a Cloud SQL
 * instance.
 *
 * <p>JDBC applications choose a value with the {@code cloudSqlTlsProvider} connection property, and
 * R2DBC applications use the {@code TLS_PROVIDER} option. Applications that configure a connector
 * programmatically use {@link
 * ConnectorConfig.Builder#withSecurityProviderLibrary(SecurityProviderLibrary)}.
 *
 * <p>The connector never modifies the JVM-wide {@link java.security.Security} provider list. When
 * bundled Conscrypt is selected, the connector uses its own connector-scoped Conscrypt {@link
 * java.security.Provider} instance for its {@link javax.net.ssl.SSLContext} only.
 *
 * <p>Hybrid post-quantum TLS 1.3 key exchange ({@code X25519MLKEM768}) is negotiated by default by
 * Conscrypt 2.6.0+, by the JDK's built-in {@code SunJSSE} provider on JDK 27+ (JEP 527) as well as
 * JDK LTS releases that backport JEP 527 (such as JDK 25.0.5+), and by user-registered JSSE
 * providers such as BouncyCastle {@code BCJSSE} 1.81+.
 */
public enum SecurityProviderLibrary {
  /**
   * Use the JVM's configured {@link java.security.Security#getProviders()} priority chain as-is
   * ({@code SSLContext.getInstance("TLSv1.3")}) without loading bundled Conscrypt. This is the
   * default.
   *
   * <p>Despite the {@code JDK} name, this mode does not force {@code SunJSSE}: it honors any higher
   * priority JSSE provider registered by the application (such as BouncyCastle {@code BCJSSE} or a
   * FIPS provider). For JDBC connections, it negotiates {@code X25519MLKEM768} natively whenever
   * the JVM's default TLS 1.3 provider supports it (for example JDK 27+, JDK 25.0.5+, or LTS
   * backports).
   */
  JDK,

  /**
   * Negotiate post-quantum key exchange ({@code X25519MLKEM768}) wherever it is available.
   *
   * <p>The connector first inspects an in-memory TLS 1.3 {@code ClientHello} from the JVM's default
   * {@code TLSv1.3} {@link javax.net.ssl.SSLContext} for {@code X25519MLKEM768} ({@code 0x11ec}) in
   * the {@code supported_groups} extension ({@code 0x000a}). When the default provider already
   * advertises {@code X25519MLKEM768} (covering JDK 27+, JDK 25.0.5+ and other LTS backports,
   * user-registered {@code BCJSSE >= 1.81}, and globally installed Conscrypt), the connector uses
   * the JVM's default provider directly without loading bundled Conscrypt.
   *
   * <p>Otherwise, the connector uses a connector-scoped Conscrypt provider when Conscrypt 2.6.0+ is
   * on the classpath and its native library loads ({@code org.conscrypt:conscrypt-openjdk-uber} is
   * included as a transitive dependency of the connector). Where Conscrypt was excluded by the
   * application, is older than 2.6.0, or cannot load on the current platform (for example musl
   * libc, an unsupported CPU architecture, or a GraalVM native image), or when {@code
   * jdk.tls.namedGroups} excludes {@code X25519MLKEM768}, the connector silently falls back to the
   * JVM's default provider with a classical handshake and logs that once at {@code INFO}.
   */
  AUTO,

  /**
   * Require connector-scoped Conscrypt on every runtime. Connections fail with a non-retryable
   * error rather than falling back to the JVM default when {@code
   * org.conscrypt:conscrypt-openjdk-uber} is not on the classpath (it is included as a transitive
   * dependency of the connector unless excluded by the application) or Conscrypt's native library
   * cannot be loaded on this platform.
   */
  CONSCRYPT;

  /**
   * Parses a {@code cloudSqlTlsProvider} property value, case-insensitively.
   *
   * @param value the property value
   * @return the matching SecurityProviderLibrary
   * @throws IllegalArgumentException if the value does not name a SecurityProviderLibrary
   */
  public static SecurityProviderLibrary fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Security provider library must not be null");
    }
    try {
      return SecurityProviderLibrary.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid security provider library \"%s\". Valid values are JDK, AUTO and CONSCRYPT.",
              value),
          e);
    }
  }
}
