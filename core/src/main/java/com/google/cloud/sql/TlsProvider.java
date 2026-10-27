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
 * Selects the JSSE provider that the connector uses for the mTLS connection to a Cloud SQL
 * instance.
 *
 * <p>JDBC and R2DBC applications choose a value with the {@code cloudSqlTlsProvider} connection
 * property. Applications that configure a connector programmatically use {@link
 * ConnectorConfig.Builder#withTlsProvider(TlsProvider)}.
 *
 * <p>The connector never modifies the JVM-wide {@link java.security.Security} provider list. When
 * Conscrypt is selected, the connector uses its own Conscrypt {@link java.security.Provider}
 * instance for its {@link javax.net.ssl.SSLContext} only.
 *
 * <p>Conscrypt 2.6.0 and later negotiate the hybrid post-quantum key exchange group {@code
 * X25519MLKEM768} by default over TLS 1.3. The JDK's own JSSE provider negotiates it from JDK 27
 * (JEP 527).
 */
public enum TlsProvider {
  /**
   * Always use the JRE's default JSSE provider. This is the default. On JDK 27 and later it
   * negotiates post-quantum key exchange natively; before that it does not.
   */
  JDK,

  /**
   * Get post-quantum key exchange wherever it is available.
   *
   * <p>On JDK 27 and later this is the JRE's own provider. On earlier JREs it is Conscrypt, where
   * its native library loads. Where Conscrypt cannot load (for example musl libc, an unsupported
   * CPU architecture, or a GraalVM native image) the connector silently falls back to the JRE's
   * default provider with a classical handshake, and logs that once.
   */
  AUTO,

  /**
   * Require Conscrypt on every JRE. Connections fail rather than falling back to the JRE default
   * when Conscrypt's native library cannot be loaded on this platform.
   */
  CONSCRYPT;

  /**
   * Parses a {@code cloudSqlTlsProvider} property value, case-insensitively.
   *
   * @param value the property value
   * @return the matching TlsProvider
   * @throws IllegalArgumentException if the value does not name a TlsProvider
   */
  public static TlsProvider fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("TLS provider must not be null");
    }
    try {
      return TlsProvider.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          String.format(
              "Invalid TLS provider \"%s\". Valid values are JDK, AUTO and CONSCRYPT.", value),
          e);
    }
  }
}
