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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.google.cloud.sql.SecurityProviderLibrary;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.security.KeyManagementException;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLContextSpi;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

@RunWith(JUnit4.class)
public class SecurityProviderFactoryTest {

  /** A provider that offers a TLSv1.3 SSLContext and PKIX KeyManagerFactory. */
  private static final Provider FAKE_CONSCRYPT = new FakeTlsProvider("Conscrypt", true, true);

  private static final Supplier<Provider> UNAVAILABLE = () -> null;

  @Test
  public void jdk_returnsJreDefaultWithoutLoadingConscryptOrProbingClientHello() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    CountingBooleanSupplier defaultProbe = new CountingBooleanSupplier(false);

    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.JDK, conscrypt, defaultProbe))
        .isNull();
    assertThat(SecurityProviderFactory.resolve(null, conscrypt, defaultProbe)).isNull();
    assertThat(conscrypt.calls.get()).isEqualTo(0);
    assertThat(defaultProbe.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_returnsConscryptWhenDefaultLacksPqc() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    CountingBooleanSupplier defaultProbe = new CountingBooleanSupplier(false);

    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, conscrypt, defaultProbe))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(defaultProbe.calls.get()).isEqualTo(1);
    assertThat(conscrypt.calls.get()).isEqualTo(1);
  }

  @Test
  public void auto_returnsDefaultProviderWithoutLoadingConscryptWhenDefaultSupportsPqc() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    CountingBooleanSupplier defaultProbe = new CountingBooleanSupplier(true);

    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, conscrypt, defaultProbe))
        .isNull();
    assertThat(defaultProbe.calls.get()).isEqualTo(1);
    assertThat(conscrypt.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_fallsBackSilentlyWhenConscryptUnavailable() {
    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, UNAVAILABLE, () -> false))
        .isNull();
  }

  @Test
  public void auto_fallsBackWhenConscryptLacksTls13OrPkixKeyManager() {
    Provider noTls13 = new FakeTlsProvider("Conscrypt", false, true);
    Provider noPkix = new FakeTlsProvider("Conscrypt", true, false);
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.AUTO, () -> noTls13, () -> false))
        .isNull();
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.AUTO, () -> noPkix, () -> false))
        .isNull();
  }

  @Test
  public void conscrypt_returnsConscryptRegardlessOfDefaultPqcSupport() {
    CountingBooleanSupplier defaultProbe = new CountingBooleanSupplier(true);
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, defaultProbe))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, () -> false))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(defaultProbe.calls.get()).isEqualTo(0);
  }

  @Test
  public void conscrypt_throwsTerminalExceptionWhenUnavailable() {
    TerminalException ex =
        assertThrows(
            TerminalException.class,
            () ->
                SecurityProviderFactory.resolve(
                    SecurityProviderLibrary.CONSCRYPT, UNAVAILABLE, () -> false));
    assertThat(ex).hasMessageThat().contains("cloudSqlTlsProvider is set to CONSCRYPT");
    assertThat(ex)
        .hasMessageThat()
        .contains("org.conscrypt:conscrypt-openjdk-uber dependency is not on the classpath");
    assertThat(ex)
        .hasMessageThat()
        .contains("included as a transitive dependency unless excluded by the application");
  }

  @Test
  public void conscrypt_terminalExceptionCarriesTheReasonConscryptDidNotLoad() {
    // Simulates CONSCRYPT where Conscrypt's native library cannot be extracted (read-only temp
    // directory): Conscrypt.checkAvailability() throws this UnsatisfiedLinkError.
    UnsatisfiedLinkError loadFailure = new UnsatisfiedLinkError("Failed creating temp file (null)");
    loadFailure.initCause(new IOException("Permission denied"));

    TerminalException ex =
        assertThrows(
            TerminalException.class,
            () ->
                SecurityProviderFactory.resolve(
                    SecurityProviderLibrary.CONSCRYPT,
                    UNAVAILABLE,
                    () -> loadFailure,
                    () -> false,
                    () -> false,
                    null,
                    capturingLogger(new ArrayList<>(), new ArrayList<>())));

    assertThat(ex).hasCauseThat().isSameInstanceAs(loadFailure);
    assertThat(ex).hasMessageThat().contains("cloudSqlTlsProvider is set to CONSCRYPT");
    assertThat(ex).hasMessageThat().contains("Failed creating temp file");
    assertThat(ex).hasMessageThat().contains("java.io.IOException: Permission denied");
  }

  @Test
  public void conscrypt_throwsTerminalExceptionWithCauseWhenConscryptCannotCreateEngine() {
    // Simulates CONSCRYPT with -Djdk.tls.namedGroups=ffdhe2048: Conscrypt loads, but
    // org.conscrypt.NativeSsl fails to initialize, so every engine or socket creation throws.
    LinkageError failure =
        new NoClassDefFoundError("Could not initialize class org.conscrypt.NativeSsl");
    failure.initCause(
        new ExceptionInInitializerError(
            new IllegalArgumentException("No valid known group found in: [ffdhe2048]")));
    CountingBooleanSupplier conscryptProbe = new CountingBooleanSupplier(false);

    TerminalException ex =
        assertThrows(
            TerminalException.class,
            () ->
                SecurityProviderFactory.resolve(
                    SecurityProviderLibrary.CONSCRYPT,
                    () -> FAKE_CONSCRYPT,
                    () -> failure,
                    conscryptProbe,
                    () -> false,
                    "ffdhe2048",
                    capturingLogger(new ArrayList<>(), new ArrayList<>())));

    assertThat(ex).hasCauseThat().isSameInstanceAs(failure);
    assertThat(ex).hasMessageThat().contains("cloudSqlTlsProvider is set to CONSCRYPT");
    assertThat(ex).hasMessageThat().contains("No valid known group found in: [ffdhe2048]");
    assertThat(ex).hasMessageThat().contains("jdk.tls.namedGroups system property");
    assertThat(conscryptProbe.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_fallsBackToDefaultProviderWhenConscryptCannotCreateEngine() throws Exception {
    LinkageError failure =
        new ExceptionInInitializerError(
            new IllegalArgumentException("No valid known group found in: [x25519mlkem768]"));
    List<String> infoMessages = new ArrayList<>();
    List<String> warnMessages = new ArrayList<>();
    AtomicBoolean announced = getFlag("AUTO_CONSCRYPT_UNUSABLE_ANNOUNCED");
    boolean prev = announced.getAndSet(false);
    try {
      for (int i = 0; i < 2; i++) {
        assertThat(
                SecurityProviderFactory.resolve(
                    SecurityProviderLibrary.AUTO,
                    () -> FAKE_CONSCRYPT,
                    () -> failure,
                    () -> false,
                    () -> false,
                    "x25519mlkem768",
                    capturingLogger(infoMessages, warnMessages)))
            .isNull();
      }
      assertThat(warnMessages).isEmpty();
      assertThat(infoMessages).hasSize(1);
      assertThat(infoMessages.get(0)).contains("Conscrypt cannot create a TLS engine");
      assertThat(infoMessages.get(0)).contains("No valid known group found in: [x25519mlkem768]");
    } finally {
      announced.set(prev);
    }
  }

  @Test
  public void engineCreationFailure_reportsLinkageErrorsOnly() throws Exception {
    assertThat(SecurityProviderFactory.engineCreationFailure(null)).isNull();
    assertThat(
            SecurityProviderFactory.engineCreationFailure(
                new FakeTlsProvider("NoTls13", false, true)))
        .isNull();

    byte[] clientHello = buildClientHelloRecord(new int[] {0x11ec, 0x001d});
    assertThat(
            SecurityProviderFactory.engineCreationFailure(
                syntheticClientHelloProvider("Conscrypt", clientHello)))
        .isNull();

    LinkageError linkageError =
        new NoClassDefFoundError("Could not initialize class org.conscrypt.NativeSsl");
    assertThat(
            SecurityProviderFactory.engineCreationFailure(
                syntheticClientHelloProvider("Conscrypt", clientHello, linkageError)))
        .isSameInstanceAs(linkageError);

    // Other failures are left to the X25519MLKEM768 probe, which reports false for them.
    Provider runtimeFailure =
        syntheticClientHelloProvider(
            "Conscrypt", clientHello, new IllegalStateException("simulated engine failure"));
    assertThat(SecurityProviderFactory.engineCreationFailure(runtimeFailure)).isNull();
    assertThat(SecurityProviderFactory.probeProviderSupportsX25519MlKem768(runtimeFailure))
        .isFalse();

    if (Conscrypt.isAvailable()) {
      assertThat(SecurityProviderFactory.engineCreationFailure(Conscrypt.newProvider())).isNull();
      assertThat(SecurityProviderFactory.conscryptFailure()).isNull();
    }
  }

  @Test
  public void auto_skipsConscryptWhenNamedGroupsExcludesX25519MlKem768() throws Exception {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    CountingBooleanSupplier conscryptProbe = new CountingBooleanSupplier(true);
    CountingBooleanSupplier defaultProbe = new CountingBooleanSupplier(false);
    List<String> infoMessages = new ArrayList<>();
    List<String> warnMessages = new ArrayList<>();
    Logger capturingLogger = capturingLogger(infoMessages, warnMessages);

    AtomicBoolean namedGroupsAnnounced = getFlag("AUTO_NAMED_GROUPS_EXCLUDED_ANNOUNCED");
    boolean prev = namedGroupsAnnounced.getAndSet(false);
    try {
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.AUTO,
                  conscrypt,
                  conscryptProbe,
                  defaultProbe,
                  "x25519,secp256r1,secp384r1",
                  capturingLogger))
          .isNull();
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.AUTO,
                  conscrypt,
                  conscryptProbe,
                  defaultProbe,
                  "x25519,secp256r1",
                  capturingLogger))
          .isNull();

      // Never loads Conscrypt JNI or probes Conscrypt when jdk.tls.namedGroups opts out of PQC.
      assertThat(conscrypt.calls.get()).isEqualTo(0);
      assertThat(conscryptProbe.calls.get()).isEqualTo(0);
      assertThat(infoMessages).hasSize(1);
      assertThat(infoMessages.get(0)).contains("jdk.tls.namedGroups excludes X25519MLKEM768");
      assertThat(warnMessages).isEmpty();
    } finally {
      namedGroupsAnnounced.set(prev);
    }

    // Blank, null, or X25519MLKEM768-including properties do not trigger opt-out.
    assertThat(SecurityProviderFactory.namedGroupsExcludesX25519MlKem768(null)).isFalse();
    assertThat(SecurityProviderFactory.namedGroupsExcludesX25519MlKem768("")).isFalse();
    assertThat(SecurityProviderFactory.namedGroupsExcludesX25519MlKem768("   ")).isFalse();
    assertThat(
            SecurityProviderFactory.namedGroupsExcludesX25519MlKem768(
                "x25519, x25519mlkem768, secp256r1"))
        .isFalse();
    assertThat(SecurityProviderFactory.namedGroupsExcludesX25519MlKem768("x25519,secp256r1"))
        .isTrue();
  }

  @Test
  public void auto_fallsBackToDefaultProviderWhenClasspathConscryptLacksPqc() throws Exception {
    // Simulates pre-2.6.0 classpath Conscrypt (e.g. 2.5.2 from grpc-alts): TLSv1.3 and PKIX exist,
    // but Conscrypt's ClientHello does not advertise X25519MLKEM768.
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    CountingBooleanSupplier conscryptProbe = new CountingBooleanSupplier(false);
    List<String> infoMessages = new ArrayList<>();
    List<String> warnMessages = new ArrayList<>();
    Logger capturingLogger = capturingLogger(infoMessages, warnMessages);

    AtomicBoolean noPqcAnnounced = getFlag("AUTO_CONSCRYPT_NO_PQC_ANNOUNCED");
    AtomicBoolean conscryptAnnounced = getFlag("CONSCRYPT_ANNOUNCED");
    boolean prevNoPqc = noPqcAnnounced.getAndSet(false);
    boolean prevConscrypt = conscryptAnnounced.getAndSet(false);
    try {
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.AUTO,
                  conscrypt,
                  conscryptProbe,
                  () -> false,
                  null,
                  capturingLogger))
          .isNull();
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.AUTO,
                  conscrypt,
                  conscryptProbe,
                  () -> false,
                  null,
                  capturingLogger))
          .isNull();

      assertThat(conscrypt.calls.get()).isEqualTo(2);
      assertThat(conscryptProbe.calls.get()).isEqualTo(2);
      assertThat(infoMessages).hasSize(1);
      assertThat(infoMessages.get(0)).contains("requires Conscrypt 2.6.0+");
      assertThat(warnMessages).isEmpty();
      assertThat(conscryptAnnounced.get()).isFalse();
    } finally {
      noPqcAnnounced.set(prevNoPqc);
      conscryptAnnounced.set(prevConscrypt);
    }
  }

  @Test
  public void conscrypt_returnsConscryptWithWarnWhenConscryptLacksPqc() throws Exception {
    // Simulates CONSCRYPT with -Djdk.tls.namedGroups=x25519,secp256r1 (classical opt-out) or
    // Conscrypt < 2.6.0: must still return Conscrypt without throwing TerminalException, and log
    // once at WARN rather than INFO.
    List<String> infoMessages = new ArrayList<>();
    List<String> warnMessages = new ArrayList<>();
    Logger capturingLogger = capturingLogger(infoMessages, warnMessages);

    AtomicBoolean warnAnnounced = getFlag("CONSCRYPT_NO_PQC_WARN_ANNOUNCED");
    AtomicBoolean infoAnnounced = getFlag("CONSCRYPT_ANNOUNCED");
    boolean prevWarn = warnAnnounced.getAndSet(false);
    boolean prevInfo = infoAnnounced.getAndSet(false);
    try {
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.CONSCRYPT,
                  () -> FAKE_CONSCRYPT,
                  () -> false,
                  () -> false,
                  "x25519,secp256r1",
                  capturingLogger))
          .isSameInstanceAs(FAKE_CONSCRYPT);
      assertThat(
              SecurityProviderFactory.resolve(
                  SecurityProviderLibrary.CONSCRYPT,
                  () -> FAKE_CONSCRYPT,
                  () -> false,
                  () -> false,
                  "x25519,secp256r1",
                  capturingLogger))
          .isSameInstanceAs(FAKE_CONSCRYPT);

      assertThat(infoMessages).isEmpty();
      assertThat(warnMessages).hasSize(1);
      assertThat(warnMessages.get(0))
          .contains("loaded Conscrypt provider does not advertise X25519MLKEM768");
    } finally {
      warnAnnounced.set(prevWarn);
      infoAnnounced.set(prevInfo);
    }
  }

  @Test
  public void resolve_logsEachInfoAnnouncementOnlyOnce() throws Exception {
    List<String> infoMessages = new ArrayList<>();
    List<String> warnMessages = new ArrayList<>();
    Logger capturingLogger = capturingLogger(infoMessages, warnMessages);

    AtomicBoolean defaultAnnounced = getFlag("DEFAULT_PQC_ANNOUNCED");
    AtomicBoolean fallbackAnnounced = getFlag("AUTO_FALLBACK_ANNOUNCED");
    AtomicBoolean conscryptAnnounced = getFlag("CONSCRYPT_ANNOUNCED");
    boolean prevDefault = defaultAnnounced.getAndSet(false);
    boolean prevFallback = fallbackAnnounced.getAndSet(false);
    boolean prevConscrypt = conscryptAnnounced.getAndSet(false);
    try {
      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.AUTO, () -> FAKE_CONSCRYPT, () -> true, capturingLogger);
      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.AUTO, () -> FAKE_CONSCRYPT, () -> true, capturingLogger);

      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.AUTO, UNAVAILABLE, () -> false, capturingLogger);
      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.AUTO, UNAVAILABLE, () -> false, capturingLogger);

      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, () -> false, capturingLogger);
      SecurityProviderFactory.resolve(
          SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, () -> false, capturingLogger);

      assertThat(infoMessages).hasSize(3);
      assertThat(infoMessages.get(0))
          .contains("default TLSv1.3 JSSE provider already advertises X25519MLKEM768");
      assertThat(infoMessages.get(1))
          .contains("Using the default JSSE provider, so Cloud SQL connections will not use");
      assertThat(infoMessages.get(1))
          .contains("included as a transitive dependency unless excluded by the application");
      assertThat(infoMessages.get(2)).contains("Conscrypt will serve the Cloud SQL connections");
      assertThat(warnMessages).isEmpty();
    } finally {
      defaultAnnounced.set(prevDefault);
      fallbackAnnounced.set(prevFallback);
      conscryptAnnounced.set(prevConscrypt);
    }
  }

  @Test
  public void supportsX25519MlKem768_conscryptTls13ReturnsTrueWithoutMutatingCallerContext()
      throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    Provider conscrypt = Conscrypt.newProvider();

    // Uninitialized context: probe uses a scratch context and leaves the caller's uninitialized.
    SSLContext uninitialized = SSLContext.getInstance("TLSv1.3", conscrypt);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(uninitialized)).isTrue();
    assertThrows(IllegalStateException.class, uninitialized::createSSLEngine);

    // Initialized context: probe succeeds directly.
    SSLContext initialized = SSLContext.getInstance("TLSv1.3", conscrypt);
    initialized.init(null, null, null);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(initialized)).isTrue();
  }

  @Test
  public void supportsX25519MlKem768_syntheticSslContextsAndFailureModes() throws Exception {
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(null)).isFalse();
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(SSLContext.getInstance("TLSv1.2")))
        .isFalse();

    // Constrained TLSv1.3 SSLContext advertising only classical groups (x25519, secp256r1).
    AtomicBoolean classicalClosed = new AtomicBoolean();
    SSLContext classicalOnly =
        syntheticTls13Context(
            buildClientHelloRecord(new int[] {0x001d, 0x0017}), Status.OK, null, classicalClosed);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(classicalOnly)).isFalse();
    assertThat(classicalClosed.get()).isTrue();

    // TLSv1.3 SSLContext advertising X25519MLKEM768 (0x11ec) after classical groups (like BCJSSE).
    AtomicBoolean pqcClosed = new AtomicBoolean();
    SSLContext pqcCapable =
        syntheticTls13Context(
            buildClientHelloRecord(new int[] {0x001d, 0x0017, 0x11ec}), Status.OK, null, pqcClosed);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(pqcCapable)).isTrue();
    assertThat(pqcClosed.get()).isTrue();

    // Non-OK SSLEngineResult status or 0-byte wrap output fails safe and still closes outbound.
    AtomicBoolean overflowClosed = new AtomicBoolean();
    SSLContext overflowResult =
        syntheticTls13Context(
            buildClientHelloRecord(new int[] {0x11ec}),
            Status.BUFFER_OVERFLOW,
            null,
            overflowClosed);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(overflowResult)).isFalse();
    assertThat(overflowClosed.get()).isTrue();

    AtomicBoolean emptyWrapClosed = new AtomicBoolean();
    SSLContext emptyWrapResult =
        syntheticTls13Context(new byte[0], Status.OK, null, emptyWrapClosed);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(emptyWrapResult)).isFalse();
    assertThat(emptyWrapClosed.get()).isTrue();

    // SSLContext whose SSLEngine throws LinkageError (e.g. broken Multi-Release bctls JAR) or
    // RuntimeException returns false cleanly instead of propagating.
    SSLContext linkageFailure =
        syntheticTls13Context(
            null, Status.OK, new NoSuchMethodError("simulated broken MR-JAR"), null);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(linkageFailure)).isFalse();

    SSLContext runtimeFailure =
        syntheticTls13Context(
            null, Status.OK, new IllegalArgumentException("simulated engine failure"), null);
    assertThat(SecurityProviderFactory.supportsX25519MlKem768(runtimeFailure)).isFalse();
  }

  @Test
  public void probeProviderSupportsX25519MlKem768_distinguishesPqcAndClassicalProviders()
      throws Exception {
    assertThat(SecurityProviderFactory.probeProviderSupportsX25519MlKem768(null)).isFalse();
    assertThat(
            SecurityProviderFactory.probeProviderSupportsX25519MlKem768(
                new FakeTlsProvider("NoTls13", false, true)))
        .isFalse();

    Provider classicalConscrypt =
        syntheticClientHelloProvider(
            "Conscrypt", buildClientHelloRecord(new int[] {0x001d, 0x0017}));
    assertThat(SecurityProviderFactory.probeProviderSupportsX25519MlKem768(classicalConscrypt))
        .isFalse();

    Provider pqcConscrypt =
        syntheticClientHelloProvider(
            "Conscrypt", buildClientHelloRecord(new int[] {0x11ec, 0x001d}));
    assertThat(SecurityProviderFactory.probeProviderSupportsX25519MlKem768(pqcConscrypt)).isTrue();

    if (Conscrypt.isAvailable()) {
      assertThat(
              SecurityProviderFactory.probeProviderSupportsX25519MlKem768(Conscrypt.newProvider()))
          .isTrue();
      assertThat(SecurityProviderFactory.conscryptSupportsX25519MlKem768()).isTrue();
    }
  }

  @Test
  public void defaultTls13SupportsX25519MlKem768_cachesAndUpdatesWhenTopProviderChanges()
      throws Exception {
    assumeTrue(Conscrypt.isAvailable());
    Provider defaultProviderBefore = SSLContext.getInstance("TLSv1.3").getProvider();
    boolean baseline = SecurityProviderFactory.defaultTls13SupportsX25519MlKem768();
    // Repeated lookup hits the per-Provider cache and returns the same answer.
    assertThat(SecurityProviderFactory.defaultTls13SupportsX25519MlKem768()).isEqualTo(baseline);

    // Survives a broken javax.net.ssl.trustStore path when re-probing a newly inserted provider,
    // proving PROBE_TRUST_MANAGERS avoids reading cacerts / trustStore files from disk.
    Provider priorityConscrypt = Conscrypt.newProvider();
    assertThat(Security.getProvider(priorityConscrypt.getName())).isNull();
    String prevTrustStore = System.getProperty("javax.net.ssl.trustStore");
    System.setProperty("javax.net.ssl.trustStore", "/nonexistent/truststore/path.jks");
    Security.insertProviderAt(priorityConscrypt, 1);
    try {
      assertThat(SSLContext.getInstance("TLSv1.3").getProvider())
          .isSameInstanceAs(priorityConscrypt);
      assertThat(SecurityProviderFactory.defaultTls13SupportsX25519MlKem768()).isTrue();
      // With a PQC-capable global top provider, AUTO resolves to null (uses JVM default).
      assertThat(SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO)).isNull();
    } finally {
      Security.removeProvider(priorityConscrypt.getName());
      if (prevTrustStore == null) {
        System.clearProperty("javax.net.ssl.trustStore");
      } else {
        System.setProperty("javax.net.ssl.trustStore", prevTrustStore);
      }
    }

    assertThat(SSLContext.getInstance("TLSv1.3").getProvider())
        .isSameInstanceAs(defaultProviderBefore);
    assertThat(SecurityProviderFactory.defaultTls13SupportsX25519MlKem768()).isEqualTo(baseline);
  }

  @Test
  public void clientHelloAdvertisesX25519MlKem768_handlesValidAndMalformedRecords() {
    // 0x11ec first in supported_groups.
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(buildClientHelloRecord(new int[] {0x11ec, 0x001d}))))
        .isTrue();

    // 0x11ec last in supported_groups after another extension, with non-zero initial buffer offset.
    byte[] pqcPacket = buildClientHelloRecord(new int[] {0x001d, 0x0017, 0x11ec});
    byte[] padded = new byte[7 + pqcPacket.length + 5];
    System.arraycopy(pqcPacket, 0, padded, 7, pqcPacket.length);
    ByteBuffer offsetBuf = ByteBuffer.wrap(padded, 7, pqcPacket.length);
    assertThat(SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(offsetBuf)).isTrue();
    // Caller's buffer position and limit are untouched.
    assertThat(offsetBuf.position()).isEqualTo(7);
    assertThat(offsetBuf.limit()).isEqualTo(7 + pqcPacket.length);

    // Classical groups only -> false.
    byte[] classicalPacket = buildClientHelloRecord(new int[] {0x001d, 0x0017, 0x0018});
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(classicalPacket)))
        .isFalse();

    // Empty extensions block -> false.
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(buildClientHelloWithRawExtensions(new byte[0], true))))
        .isFalse();

    // No extensions field at all (TLS 1.2 minimal ClientHello) -> false.
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(buildClientHelloWithRawExtensions(new byte[0], false))))
        .isFalse();

    // Null, empty, and < 44-byte buffers -> false.
    assertThat(SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(null)).isFalse();
    assertThat(SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(ByteBuffer.allocate(0)))
        .isFalse();
    assertThat(SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(ByteBuffer.allocate(43)))
        .isFalse();

    // Non-Handshake TLS record type (21 = Alert) and non-ClientHello message type (2 =
    // ServerHello).
    byte[] alertRecord = pqcPacket.clone();
    alertRecord[0] = 21;
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(alertRecord)))
        .isFalse();

    byte[] serverHelloRecord = pqcPacket.clone();
    serverHelloRecord[5] = 2;
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(serverHelloRecord)))
        .isFalse();

    // Every prefix truncation of a valid PQC ClientHello must return false without throwing.
    for (int len = 0; len < pqcPacket.length; len++) {
      ByteBuffer truncated = ByteBuffer.wrap(pqcPacket, 0, len);
      assertThat(SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(truncated)).isFalse();
    }

    // Handshake length claiming more bytes than the TLS record length -> false.
    byte[] handshakeExceedsRecord = pqcPacket.clone();
    int overLen = pqcPacket.length; // recordLen is pqcPacket.length - 5, so 9 + overLen > recordEnd
    handshakeExceedsRecord[6] = (byte) ((overLen >>> 16) & 0xFF);
    handshakeExceedsRecord[7] = (byte) ((overLen >>> 8) & 0xFF);
    handshakeExceedsRecord[8] = (byte) (overLen & 0xFF);
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(handshakeExceedsRecord)))
        .isFalse();

    // Malformed supported_groups: odd groupsByteLen (3) and groupsByteLen > extLen - 2.
    byte[] oddGroupBytes = new byte[] {0x00, 0x0a, 0x00, 0x05, 0x00, 0x03, 0x11, (byte) 0xec, 0x00};
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(buildClientHelloWithRawExtensions(oddGroupBytes, true))))
        .isFalse();

    byte[] overflowingGroupList =
        new byte[] {0x00, 0x0a, 0x00, 0x04, 0x00, 0x04, 0x11, (byte) 0xec};
    assertThat(
            SecurityProviderFactory.clientHelloAdvertisesX25519MlKem768(
                ByteBuffer.wrap(buildClientHelloWithRawExtensions(overflowingGroupList, true))))
        .isFalse();
  }

  @Test
  public void conscryptProvider_isConnectorScopedInstance() {
    assumeTrue(Conscrypt.isAvailable());
    Provider provider = SecurityProviderFactory.conscryptProvider();
    assertThat(provider).isNotNull();
    assertThat(Conscrypt.isConscrypt(provider)).isTrue();
    assertThat(SecurityProviderFactory.isConscrypt(provider)).isTrue();
    assertThat(provider.getService("SSLContext", "TLSv1.3")).isNotNull();
    // Resolved once per JVM.
    assertThat(SecurityProviderFactory.conscryptProvider()).isSameInstanceAs(provider);
    // Never installed into the JVM-global provider list.
    assertThat(Security.getProvider("Conscrypt")).isNull();
  }

  @Test
  public void isConscrypt() {
    assertThat(SecurityProviderFactory.isConscrypt(null)).isFalse();
    assertThat(SecurityProviderFactory.isConscrypt(new FakeTlsProvider("SunJSSE", true, true)))
        .isFalse();
    assertThat(SecurityProviderFactory.isConscrypt(FAKE_CONSCRYPT)).isTrue();
    assertThat(ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(FAKE_CONSCRYPT))
        .isTrue();
    assertThat(
            ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(
                new FakeTlsProvider("SunJSSE", true, true)))
        .isFalse();
    assertThat(ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(null)).isFalse();
  }

  @Test
  public void securityProviderLibraryFromString() {
    assertThat(SecurityProviderLibrary.fromString("jdk")).isEqualTo(SecurityProviderLibrary.JDK);
    assertThat(SecurityProviderLibrary.fromString(" Auto "))
        .isEqualTo(SecurityProviderLibrary.AUTO);
    assertThat(SecurityProviderLibrary.fromString("CONSCRYPT"))
        .isEqualTo(SecurityProviderLibrary.CONSCRYPT);
    assertThrows(IllegalArgumentException.class, () -> SecurityProviderLibrary.fromString("bogus"));
    assertThrows(IllegalArgumentException.class, () -> SecurityProviderLibrary.fromString(null));
  }

  @Test
  public void conscryptProvider_returnsNullWhenConscryptAbsentFromClasspath() throws Exception {
    ClassLoader withoutConscrypt =
        new ClassLoader(SecurityProviderFactoryTest.class.getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.conscrypt.")) {
              throw new ClassNotFoundException(name);
            }
            if (name.startsWith("com.google.cloud.sql.core.SecurityProviderFactory")) {
              Class<?> loaded = findLoadedClass(name);
              if (loaded == null) {
                byte[] bytes = readClassBytes(name);
                loaded = defineClass(name, bytes, 0, bytes.length);
              }
              if (resolve) {
                resolveClass(loaded);
              }
              return loaded;
            }
            return super.loadClass(name, resolve);
          }
        };

    Class<?> isolatedFactory =
        Class.forName("com.google.cloud.sql.core.SecurityProviderFactory", true, withoutConscrypt);
    Method conscryptProvider = isolatedFactory.getDeclaredMethod("conscryptProvider");
    conscryptProvider.setAccessible(true);
    assertThat(conscryptProvider.invoke(null)).isNull();
    Method conscryptSupports = isolatedFactory.getDeclaredMethod("conscryptSupportsX25519MlKem768");
    conscryptSupports.setAccessible(true);
    assertThat(conscryptSupports.invoke(null)).isEqualTo(false);
  }

  private static Logger capturingLogger(List<String> infoMessages, List<String> warnMessages) {
    return (Logger)
        Proxy.newProxyInstance(
            Logger.class.getClassLoader(),
            new Class<?>[] {Logger.class},
            (proxy, method, args) -> {
              List<String> target = null;
              if ("info".equals(method.getName())) {
                target = infoMessages;
              } else if ("warn".equals(method.getName())) {
                target = warnMessages;
              }
              if (target != null && args != null && args.length >= 1) {
                String format = (String) args[0];
                if (args.length == 1) {
                  target.add(format);
                } else if (args.length == 2 && args[1] instanceof Object[]) {
                  target.add(MessageFormatter.arrayFormat(format, (Object[]) args[1]).getMessage());
                } else {
                  Object[] params = new Object[args.length - 1];
                  System.arraycopy(args, 1, params, 0, params.length);
                  target.add(MessageFormatter.arrayFormat(format, params).getMessage());
                }
              }
              Class<?> ret = method.getReturnType();
              return ret == boolean.class ? Boolean.FALSE : null;
            });
  }

  private static AtomicBoolean getFlag(String fieldName) throws Exception {
    Field f = SecurityProviderFactory.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    return (AtomicBoolean) f.get(null);
  }

  /**
   * Builds a synthetic TLSPlaintext record containing a ClientHello with a dummy extension followed
   * by {@code supported_groups} ({@code 0x000a}) containing {@code namedGroups}.
   */
  private static byte[] buildClientHelloRecord(int[] namedGroups) {
    ByteArrayOutputStream ext = new ByteArrayOutputStream();
    // Non-matching extension first (0x002b supported_versions = TLS 1.3 [0x0304]) so the parser
    // exercises skipping preceding extensions.
    ext.write(0x00);
    ext.write(0x2b);
    ext.write(0x00);
    ext.write(0x03);
    ext.write(0x02);
    ext.write(0x03);
    ext.write(0x04);

    int groupsBytes = namedGroups.length * 2;
    int extPayloadLen = 2 + groupsBytes;
    ext.write(0x00);
    ext.write(0x0a);
    ext.write((extPayloadLen >>> 8) & 0xFF);
    ext.write(extPayloadLen & 0xFF);
    ext.write((groupsBytes >>> 8) & 0xFF);
    ext.write(groupsBytes & 0xFF);
    for (int group : namedGroups) {
      ext.write((group >>> 8) & 0xFF);
      ext.write(group & 0xFF);
    }
    return buildClientHelloWithRawExtensions(ext.toByteArray(), true);
  }

  private static byte[] buildClientHelloWithRawExtensions(
      byte[] extensionsBytes, boolean includeExtensionsField) {
    ByteArrayOutputStream body = new ByteArrayOutputStream();
    // legacy_version = 0x0303
    body.write(0x03);
    body.write(0x03);
    // 32-byte random
    for (int i = 0; i < 32; i++) {
      body.write(i);
    }
    // 4-byte legacy_session_id
    body.write(4);
    body.write(0xaa);
    body.write(0xbb);
    body.write(0xcc);
    body.write(0xdd);
    // cipher_suites: 2 bytes (TLS_AES_128_GCM_SHA256 = 0x1301)
    body.write(0x00);
    body.write(0x02);
    body.write(0x13);
    body.write(0x01);
    // legacy_compression_methods: 1 byte (null = 0x00)
    body.write(0x01);
    body.write(0x00);
    if (includeExtensionsField) {
      body.write((extensionsBytes.length >>> 8) & 0xFF);
      body.write(extensionsBytes.length & 0xFF);
      body.write(extensionsBytes, 0, extensionsBytes.length);
    }

    byte[] chBody = body.toByteArray();
    int handshakeLen = chBody.length;
    int recordLen = 4 + handshakeLen;
    ByteArrayOutputStream record = new ByteArrayOutputStream(5 + recordLen);
    record.write(22); // ContentType.handshake
    record.write(0x03);
    record.write(0x01);
    record.write((recordLen >>> 8) & 0xFF);
    record.write(recordLen & 0xFF);
    record.write(1); // HandshakeType.client_hello
    record.write((handshakeLen >>> 16) & 0xFF);
    record.write((handshakeLen >>> 8) & 0xFF);
    record.write(handshakeLen & 0xFF);
    record.write(chBody, 0, chBody.length);
    return record.toByteArray();
  }

  private static SSLContext syntheticTls13Context(
      byte[] clientHelloBytes, Status wrapStatus, Throwable failure, AtomicBoolean outboundClosed)
      throws Exception {
    SSLSession defaultSession = SSLContext.getDefault().createSSLEngine().getSession();
    Provider provider = new FakeTlsProvider("SyntheticTls", true, true);
    SSLContextSpi spi =
        new SSLContextSpi() {
          @Override
          protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr)
              throws KeyManagementException {}

          @Override
          protected SSLSocketFactory engineGetSocketFactory() {
            return null;
          }

          @Override
          protected SSLServerSocketFactory engineGetServerSocketFactory() {
            return null;
          }

          @Override
          protected SSLEngine engineCreateSSLEngine() {
            if (failure instanceof Error) {
              throw (Error) failure;
            }
            if (failure instanceof RuntimeException) {
              throw (RuntimeException) failure;
            }
            return new SyntheticClientHelloEngine(
                clientHelloBytes, wrapStatus, defaultSession, outboundClosed);
          }

          @Override
          protected SSLEngine engineCreateSSLEngine(String host, int port) {
            return engineCreateSSLEngine();
          }

          @Override
          protected SSLSessionContext engineGetServerSessionContext() {
            return null;
          }

          @Override
          protected SSLSessionContext engineGetClientSessionContext() {
            return null;
          }
        };
    return new SSLContext(spi, provider, "TLSv1.3") {};
  }

  private static Provider syntheticClientHelloProvider(String name, byte[] clientHelloBytes)
      throws Exception {
    return syntheticClientHelloProvider(name, clientHelloBytes, null);
  }

  /** When engineFailure is non-null, every engine creation throws it. */
  @SuppressWarnings("deprecation") // Provider(String, double, String) for Java 8 compatibility.
  private static Provider syntheticClientHelloProvider(
      String name, byte[] clientHelloBytes, Throwable engineFailure) throws Exception {
    SSLSession defaultSession = SSLContext.getDefault().createSSLEngine().getSession();
    return new Provider(name, 1.0, "synthetic TLSv1.3 provider") {
      private static final long serialVersionUID = 1L;

      {
        putService(
            new Provider.Service(
                this, "SSLContext", "TLSv1.3", SSLContextSpi.class.getName(), null, null) {
              @Override
              public Object newInstance(Object constructorParameter) {
                return new SSLContextSpi() {
                  @Override
                  protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) {}

                  @Override
                  protected SSLSocketFactory engineGetSocketFactory() {
                    return null;
                  }

                  @Override
                  protected SSLServerSocketFactory engineGetServerSocketFactory() {
                    return null;
                  }

                  @Override
                  protected SSLEngine engineCreateSSLEngine() {
                    if (engineFailure instanceof Error) {
                      throw (Error) engineFailure;
                    }
                    if (engineFailure instanceof RuntimeException) {
                      throw (RuntimeException) engineFailure;
                    }
                    return new SyntheticClientHelloEngine(
                        clientHelloBytes, Status.OK, defaultSession, null);
                  }

                  @Override
                  protected SSLEngine engineCreateSSLEngine(String host, int port) {
                    return engineCreateSSLEngine();
                  }

                  @Override
                  protected SSLSessionContext engineGetServerSessionContext() {
                    return null;
                  }

                  @Override
                  protected SSLSessionContext engineGetClientSessionContext() {
                    return null;
                  }
                };
              }
            });
      }
    };
  }

  private static byte[] readClassBytes(String className) throws ClassNotFoundException {
    String resource = className.replace('.', '/') + ".class";
    try (InputStream in =
        SecurityProviderFactoryTest.class.getClassLoader().getResourceAsStream(resource)) {
      if (in == null) {
        throw new ClassNotFoundException(className);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] buf = new byte[4096];
      int n;
      while ((n = in.read(buf)) != -1) {
        out.write(buf, 0, n);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new ClassNotFoundException(className, e);
    }
  }

  private static final class CountingSupplier implements Supplier<Provider> {
    private final Provider provider;
    private final AtomicInteger calls = new AtomicInteger();

    CountingSupplier(Provider provider) {
      this.provider = provider;
    }

    @Override
    public Provider get() {
      calls.incrementAndGet();
      return provider;
    }
  }

  private static final class CountingBooleanSupplier implements BooleanSupplier {
    private final boolean value;
    private final AtomicInteger calls = new AtomicInteger();

    CountingBooleanSupplier(boolean value) {
      this.value = value;
    }

    @Override
    public boolean getAsBoolean() {
      calls.incrementAndGet();
      return value;
    }
  }

  private static final class FakeTlsProvider extends Provider {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("deprecation") // Provider(String, double, String) for Java 8 compatibility.
    FakeTlsProvider(String name, boolean tls13, boolean pkixKmf) {
      super(name, 1.0, "fake provider for tests");
      if (tls13) {
        put("SSLContext.TLSv1.3", "com.example.FakeSslContextSpi");
      }
      if (pkixKmf) {
        put("KeyManagerFactory.PKIX", "com.example.FakeKeyManagerFactorySpi");
      }
    }
  }

  private static final class SyntheticClientHelloEngine extends SSLEngine {
    private final byte[] clientHello;
    private final Status wrapStatus;
    private final SSLSession session;
    private final AtomicBoolean outboundClosed;
    private String[] enabledProtocols = new String[] {"TLSv1.3", "TLSv1.2"};

    SyntheticClientHelloEngine(
        byte[] clientHello, Status wrapStatus, SSLSession session, AtomicBoolean outboundClosed) {
      this.clientHello = clientHello;
      this.wrapStatus = wrapStatus;
      this.session = session;
      this.outboundClosed = outboundClosed;
    }

    @Override
    public SSLEngineResult wrap(ByteBuffer[] srcs, int offset, int length, ByteBuffer dst) {
      if (wrapStatus == Status.OK && clientHello.length > 0) {
        dst.put(clientHello);
      }
      return new SSLEngineResult(wrapStatus, HandshakeStatus.NEED_UNWRAP, 0, clientHello.length);
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts, int offset, int length) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Runnable getDelegatedTask() {
      return null;
    }

    @Override
    public void closeInbound() {}

    @Override
    public boolean isInboundDone() {
      return false;
    }

    @Override
    public void closeOutbound() {
      if (outboundClosed != null) {
        outboundClosed.set(true);
      }
    }

    @Override
    public boolean isOutboundDone() {
      return outboundClosed != null && outboundClosed.get();
    }

    @Override
    public String[] getSupportedCipherSuites() {
      return new String[] {"TLS_AES_128_GCM_SHA256"};
    }

    @Override
    public String[] getEnabledCipherSuites() {
      return new String[] {"TLS_AES_128_GCM_SHA256"};
    }

    @Override
    public void setEnabledCipherSuites(String[] suites) {}

    @Override
    public String[] getSupportedProtocols() {
      return new String[] {"TLSv1.3", "TLSv1.2"};
    }

    @Override
    public String[] getEnabledProtocols() {
      return enabledProtocols.clone();
    }

    @Override
    public void setEnabledProtocols(String[] protocols) {
      this.enabledProtocols = protocols.clone();
    }

    @Override
    public SSLParameters getSSLParameters() {
      SSLParameters params = new SSLParameters();
      params.setProtocols(enabledProtocols.clone());
      return params;
    }

    @Override
    public void setSSLParameters(SSLParameters params) {
      if (params.getProtocols() != null) {
        this.enabledProtocols = params.getProtocols();
      }
    }

    @Override
    public SSLSession getSession() {
      return session;
    }

    @Override
    public void beginHandshake() {}

    @Override
    public HandshakeStatus getHandshakeStatus() {
      return HandshakeStatus.NOT_HANDSHAKING;
    }

    @Override
    public void setUseClientMode(boolean mode) {}

    @Override
    public boolean getUseClientMode() {
      return true;
    }

    @Override
    public void setNeedClientAuth(boolean need) {}

    @Override
    public boolean getNeedClientAuth() {
      return false;
    }

    @Override
    public void setWantClientAuth(boolean want) {}

    @Override
    public boolean getWantClientAuth() {
      return false;
    }

    @Override
    public void setEnableSessionCreation(boolean flag) {}

    @Override
    public boolean getEnableSessionCreation() {
      return true;
    }
  }
}
