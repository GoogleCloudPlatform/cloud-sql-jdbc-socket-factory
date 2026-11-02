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
import java.lang.reflect.Method;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.conscrypt.Conscrypt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SecurityProviderFactoryTest {

  /** A provider that offers a TLSv1.3 SSLContext, standing in for Conscrypt. */
  private static final Provider FAKE_CONSCRYPT = new FakeTlsProvider("Conscrypt", true);

  private static final Supplier<Provider> UNAVAILABLE = () -> null;

  @Test
  public void jdk_returnsJreDefaultWithoutLoadingConscrypt() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    assertThat(SecurityProviderFactory.resolve(SecurityProviderLibrary.JDK, conscrypt, 8)).isNull();
    assertThat(SecurityProviderFactory.resolve(null, conscrypt, 8)).isNull();
    assertThat(conscrypt.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_returnsConscryptBeforeJdk27() {
    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, () -> FAKE_CONSCRYPT, 8))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(
            SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, () -> FAKE_CONSCRYPT, 26))
        .isSameInstanceAs(FAKE_CONSCRYPT);
  }

  @Test
  public void auto_returnsJreDefaultOnJdk27WithoutLoadingConscrypt() {
    CountingSupplier conscrypt = new CountingSupplier(FAKE_CONSCRYPT);
    assertThat(SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, conscrypt, 27))
        .isNull();
    assertThat(conscrypt.calls.get()).isEqualTo(0);
  }

  @Test
  public void auto_fallsBackSilentlyWhenConscryptUnavailable() {
    assertThat(SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, UNAVAILABLE, 11))
        .isNull();
  }

  @Test
  public void auto_fallsBackWhenConscryptLacksTls13() {
    Provider incomplete = new FakeTlsProvider("Conscrypt", false);
    assertThat(SecurityProviderFactory.resolve(SecurityProviderLibrary.AUTO, () -> incomplete, 11))
        .isNull();
  }

  @Test
  public void conscrypt_returnsConscryptOnEveryJre() {
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, 8))
        .isSameInstanceAs(FAKE_CONSCRYPT);
    assertThat(
            SecurityProviderFactory.resolve(
                SecurityProviderLibrary.CONSCRYPT, () -> FAKE_CONSCRYPT, 27))
        .isSameInstanceAs(FAKE_CONSCRYPT);
  }

  @Test
  public void conscrypt_throwsTerminalExceptionWhenUnavailable() {
    TerminalException ex =
        assertThrows(
            TerminalException.class,
            () ->
                SecurityProviderFactory.resolve(
                    SecurityProviderLibrary.CONSCRYPT, UNAVAILABLE, 11));
    assertThat(ex).hasMessageThat().contains("cloudSqlTlsProvider is set to CONSCRYPT");
    assertThat(ex)
        .hasMessageThat()
        .contains("org.conscrypt:conscrypt-openjdk-uber dependency is not on the classpath");
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
    assertThat(SecurityProviderFactory.isConscrypt(new FakeTlsProvider("SunJSSE", true))).isFalse();
    assertThat(SecurityProviderFactory.isConscrypt(FAKE_CONSCRYPT)).isTrue();
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
    assertThat(SecurityProviderFactory.parseJreVersion("1.8")).isEqualTo(8);
    assertThat(SecurityProviderFactory.parseJreVersion("11")).isEqualTo(11);
    assertThat(SecurityProviderFactory.parseJreVersion("26")).isEqualTo(26);
    assertThat(SecurityProviderFactory.parseJreVersion("27")).isEqualTo(27);
    assertThat(SecurityProviderFactory.parseJreVersion("")).isEqualTo(0);
    assertThat(SecurityProviderFactory.parseJreVersion("garbage")).isEqualTo(0);
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
