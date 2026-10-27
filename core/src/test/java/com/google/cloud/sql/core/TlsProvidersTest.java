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

import com.google.cloud.sql.TlsProvider;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TlsProvidersTest {

  /** A provider that offers a TLSv1.3 SSLContext, standing in for Conscrypt. */
  private static final Provider FAKE_CONSCRYPT = new FakeTlsProvider("Conscrypt", true);

  private static final Supplier<Provider> UNAVAILABLE = () -> null;

  @Test
  public void jdk_returnsJreDefaultWithoutLoadingConscrypt() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    assertThat(TlsProviders.resolve(TlsProvider.JDK, conscrypt, 8)).isNull();
    assertThat(TlsProviders.resolve(null, conscrypt, 8)).isNull();
    assertThat(conscrypt.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_returnsConscryptBeforeJdk27() {
    assertThat(TlsProviders.resolve(TlsProvider.AUTO, () -> FAKE_CONSCRYPT, 8))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(TlsProviders.resolve(TlsProvider.AUTO, () -> FAKE_CONSCRYPT, 26))
        .isSameInstanceAs(FAKE_CONSCRYPT);
  }

  @Test
  public void auto_returnsJreDefaultOnJdk27WithoutLoadingConscrypt() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    assertThat(TlsProviders.resolve(TlsProvider.AUTO, conscrypt, 27)).isNull();
    assertThat(conscrypt.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_fallsBackSilentlyWhenConscryptUnavailable() {
    assertThat(TlsProviders.resolve(TlsProvider.AUTO, UNAVAILABLE, 11)).isNull();
  }

  @Test
  public void auto_fallsBackWhenConscryptLacksTls13() {
    Provider incomplete = new FakeTlsProvider("Conscrypt", false);
    assertThat(TlsProviders.resolve(TlsProvider.AUTO, () -> incomplete, 11)).isNull();
  }

  @Test
  public void conscrypt_returnsConscryptOnEveryJre() {
    assertThat(TlsProviders.resolve(TlsProvider.CONSCRYPT, () -> FAKE_CONSCRYPT, 8))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(TlsProviders.resolve(TlsProvider.CONSCRYPT, () -> FAKE_CONSCRYPT, 27))
        .isSameInstanceAs(FAKE_CONSCRYPT);
  }

  @Test
  public void conscrypt_throwsTerminalExceptionWhenUnavailable() {
    TerminalException ex =
        assertThrows(
            TerminalException.class,
            () -> TlsProviders.resolve(TlsProvider.CONSCRYPT, UNAVAILABLE, 11));
    assertThat(ex).hasMessageThat().contains("cloudSqlTlsProvider is set to CONSCRYPT");
  }

  @Test
  public void conscryptProvider_isConnectorScopedInstance() {
    assumeTrue(Conscrypt.isAvailable());
    Provider provider = TlsProviders.conscryptProvider();
    assertThat(provider).isNotNull();
    assertThat(Conscrypt.isConscrypt(provider)).isTrue();
    assertThat(TlsProviders.isConscrypt(provider)).isTrue();
    assertThat(provider.getService("SSLContext", "TLSv1.3")).isNotNull();
    // Resolved once per JVM.
    assertThat(TlsProviders.conscryptProvider()).isSameInstanceAs(provider);
    // Never installed into the JVM-global provider list.
    assertThat(Security.getProvider("Conscrypt")).isNull();
  }

  @Test
  public void isConscrypt() {
    assertThat(TlsProviders.isConscrypt(null)).isFalse();
    assertThat(TlsProviders.isConscrypt(new FakeTlsProvider("SunJSSE", true))).isFalse();
    assertThat(TlsProviders.isConscrypt(FAKE_CONSCRYPT)).isTrue();
    assertThat(ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(FAKE_CONSCRYPT))
        .isTrue();
    assertThat(
            ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(
                new FakeTlsProvider("SunJSSE", true)))
        .isFalse();
    // No connector-scoped provider: the legacy global check applies, and Conscrypt is not
    // installed globally in this test JVM.
    assertThat(ConscryptWorkaroundDelegatingTrustManger.isWorkaroundNeeded(null)).isFalse();
  }

  @Test
  public void parseJreVersion() {
    assertThat(TlsProviders.parseJreVersion("1.8")).isEqualTo(8);
    assertThat(TlsProviders.parseJreVersion("11")).isEqualTo(11);
    assertThat(TlsProviders.parseJreVersion("26")).isEqualTo(26);
    assertThat(TlsProviders.parseJreVersion("27")).isEqualTo(27);
    assertThat(TlsProviders.parseJreVersion("")).isEqualTo(0);
    assertThat(TlsProviders.parseJreVersion("garbage")).isEqualTo(0);
  }

  @Test
  public void tlsProviderFromString() {
    assertThat(TlsProvider.fromString("jdk")).isEqualTo(TlsProvider.JDK);
    assertThat(TlsProvider.fromString(" Auto ")).isEqualTo(TlsProvider.AUTO);
    assertThat(TlsProvider.fromString("CONSCRYPT")).isEqualTo(TlsProvider.CONSCRYPT);
    assertThrows(IllegalArgumentException.class, () -> TlsProvider.fromString("boringssl"));
    assertThrows(IllegalArgumentException.class, () -> TlsProvider.fromString(null));
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

  private static final class FakeTlsProvider extends Provider {
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("deprecation") // Provider(String, double, String) for Java 8 compatibility.
    FakeTlsProvider(String name, boolean tls13) {
      super(name, 1.0, "fake provider for tests");
      if (tls13) {
        put("SSLContext.TLSv1.3", "com.example.FakeSslContextSpi");
        put("KeyManagerFactory.PKIX", "com.example.FakeKeyManagerFactorySpi");
      }
    }
  }
}
