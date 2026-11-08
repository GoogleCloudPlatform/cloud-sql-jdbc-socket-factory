/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.IpType;
import com.google.cloud.sql.SecurityProviderLibrary;
import java.security.Provider;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * A value object containing configuration needed to set up an mTLS connection to a Cloud SQL
 * instance.
 */
public class ConnectionMetadata {
  private final List<String> preferredIpAddresses;
  private final Map<IpType, List<String>> ipAddrs;
  private final KeyManagerFactory keyManagerFactory;
  private final TrustManagerFactory trustManagerFactory;
  private final SSLContext sslContext;
  private final List<String> mdxProtocolSupport;
  private final String databaseVersion;
  private final SecurityProviderLibrary securityProviderLibrary;
  private final boolean sslContextSupportsPqc;

  /** Construct an immutable ConnectionMetadata. */
  public ConnectionMetadata(
      List<String> preferredIpAddresses,
      Map<IpType, List<String>> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport) {
    this(
        preferredIpAddresses,
        ipAddrs,
        keyManagerFactory,
        trustManagerFactory,
        sslContext,
        mdxProtocolSupport,
        "",
        ConnectorConfig.DEFAULT_SECURITY_PROVIDER_LIBRARY,
        false);
  }

  ConnectionMetadata(
      List<String> preferredIpAddresses,
      Map<IpType, List<String>> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport,
      String databaseVersion) {
    this(
        preferredIpAddresses,
        ipAddrs,
        keyManagerFactory,
        trustManagerFactory,
        sslContext,
        mdxProtocolSupport,
        databaseVersion,
        ConnectorConfig.DEFAULT_SECURITY_PROVIDER_LIBRARY,
        false);
  }

  /** Construct an immutable ConnectionMetadata including connector TLS provider metadata. */
  public ConnectionMetadata(
      List<String> preferredIpAddresses,
      Map<IpType, List<String>> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport,
      SecurityProviderLibrary securityProviderLibrary,
      boolean sslContextSupportsPqc) {
    this(
        preferredIpAddresses,
        ipAddrs,
        keyManagerFactory,
        trustManagerFactory,
        sslContext,
        mdxProtocolSupport,
        "",
        securityProviderLibrary,
        sslContextSupportsPqc);
  }

  /** Construct an immutable ConnectionMetadata including database version and TLS metadata. */
  ConnectionMetadata(
      List<String> preferredIpAddresses,
      Map<IpType, List<String>> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport,
      String databaseVersion,
      SecurityProviderLibrary securityProviderLibrary,
      boolean sslContextSupportsPqc) {
    this.preferredIpAddresses = preferredIpAddresses;
    this.ipAddrs = ipAddrs;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
    this.sslContext = sslContext;
    this.mdxProtocolSupport = mdxProtocolSupport;
    this.databaseVersion = databaseVersion == null ? "" : databaseVersion;
    this.securityProviderLibrary =
        securityProviderLibrary == null
            ? ConnectorConfig.DEFAULT_SECURITY_PROVIDER_LIBRARY
            : securityProviderLibrary;
    this.sslContextSupportsPqc = sslContextSupportsPqc;
  }

  /** Construct an immutable ConnectionMetadata (Deprecated). */
  @Deprecated
  public ConnectionMetadata(
      String preferredIpAddress,
      Map<IpType, String> ipAddrs,
      KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory,
      SSLContext sslContext,
      List<String> mdxProtocolSupport) {
    this(
        Collections.singletonList(preferredIpAddress),
        compatMap(ipAddrs),
        keyManagerFactory,
        trustManagerFactory,
        sslContext,
        mdxProtocolSupport);
  }

  private static Map<IpType, List<String>> compatMap(Map<IpType, String> map) {
    Map<IpType, List<String>> newMap = new HashMap<>();
    if (map != null) {
      map.forEach((k, v) -> newMap.put(k, Collections.singletonList(v)));
    }
    return newMap;
  }

  @Deprecated
  public String getPreferredIpAddress() {
    return preferredIpAddresses == null || preferredIpAddresses.isEmpty()
        ? null
        : preferredIpAddresses.get(0);
  }

  public List<String> getPreferredIpAddresses() {
    return preferredIpAddresses;
  }

  /**
   * Returns the map of IP addresses.
   *
   * @return the map of IP addresses.
   * @deprecated use {@link #getAllIpAddrs()} instead.
   */
  @Deprecated
  public Map<IpType, String> getIpAddrs() {
    if (ipAddrs == null) {
      return null;
    }
    return ipAddrs.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, e -> e.getValue().isEmpty() ? null : e.getValue().get(0)));
  }

  public Map<IpType, List<String>> getAllIpAddrs() {
    return ipAddrs;
  }

  public KeyManagerFactory getKeyManagerFactory() {
    return keyManagerFactory;
  }

  public TrustManagerFactory getTrustManagerFactory() {
    return trustManagerFactory;
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

  /**
   * Returns the JSSE provider of the SSLContext the connector built for this instance, as selected
   * by the {@code cloudSqlTlsProvider} setting. Drivers that build their own SSLContext from {@link
   * #getKeyManagerFactory()} and {@link #getTrustManagerFactory()} should use this provider so that
   * their connections negotiate the same TLS key exchange groups as the connector's.
   *
   * <p>Never null for metadata produced by the connector, which always builds an SSLContext. It is
   * null only for a ConnectionMetadata constructed directly with a null SSLContext.
   *
   * @return the SSLContext provider
   */
  public Provider getSslContextProvider() {
    return sslContext == null ? null : sslContext.getProvider();
  }

  /**
   * Returns the {@link SecurityProviderLibrary} configured on the connector that produced this
   * metadata (for example via {@link
   * ConnectorConfig.Builder#withSecurityProviderLibrary(SecurityProviderLibrary)} on a named
   * connector or via {@code cloudSqlTlsProvider} / {@code TLS_PROVIDER} on an unnamed connector).
   *
   * @return the connector's configured SecurityProviderLibrary
   */
  public SecurityProviderLibrary getSecurityProviderLibrary() {
    return securityProviderLibrary;
  }

  /**
   * Returns {@code true} if {@link #getSslContext()} was probed when refreshed and advertises
   * {@code X25519MLKEM768} ({@code 0x11ec}) in its TLS 1.3 {@code ClientHello} {@code
   * supported_groups} extension.
   *
   * @return whether the connector's SSLContext advertises X25519MLKEM768
   */
  public boolean isSslContextSupportsPqc() {
    return sslContextSupportsPqc;
  }

  public List<String> getMdxProtocolSupport() {
    return mdxProtocolSupport;
  }

  String getDatabaseVersion() {
    return databaseVersion;
  }

  boolean isPostgres() {
    return databaseVersion != null && databaseVersion.startsWith("POSTGRES");
  }

  public boolean isMdxClientProtocolTypeSupport() {
    return mdxProtocolSupport != null && mdxProtocolSupport.contains("CLIENT_PROTOCOL_TYPE");
  }
}
