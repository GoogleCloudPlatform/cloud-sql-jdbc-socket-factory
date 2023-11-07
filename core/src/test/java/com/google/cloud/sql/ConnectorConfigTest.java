/*
 * Copyright 2023 Google LLC
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

import static com.google.common.truth.Truth.assertThat;

import com.google.auth.oauth2.GoogleCredentials;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;

public class ConnectorConfigTest {
  @Test
  public void testConfigFromBuilder() {
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String wantAdminRootUrl = "https://googleapis.example.com/";
    final String wantAdminServicePath = "sqladmin/";
    final String wantGoogleCredentialsPath = "/path/to/credentials";
    final Supplier<GoogleCredentials> wantGoogleCredentialSupplier =
        () -> GoogleCredentials.create(null);
    final GoogleCredentials wantGoogleCredentials = GoogleCredentials.create(null);

    ConnectorConfig cc =
        new ConnectorConfig.Builder()
            .withTargetPrincipal(wantTargetPrincipal)
            .withDelegates(wantDelegates)
            .withAdminRootUrl(wantAdminRootUrl)
            .withAdminServicePath(wantAdminServicePath)
            .withGoogleCredentialsSupplier(wantGoogleCredentialSupplier)
            .withGoogleCredentials(wantGoogleCredentials)
            .withGoogleCredentialsPath(wantGoogleCredentialsPath)
            .build();

    assertThat(cc.getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(cc.getDelegates()).isEqualTo(wantDelegates);
    assertThat(cc.getAdminRootUrl()).isEqualTo(wantAdminRootUrl);
    assertThat(cc.getAdminServicePath()).isEqualTo(wantAdminServicePath);
    assertThat(cc.getGoogleCredentialsSupplier()).isSameInstanceAs(wantGoogleCredentialSupplier);
    assertThat(cc.getGoogleCredentials()).isSameInstanceAs(wantGoogleCredentials);
    assertThat(cc.getGoogleCredentialsPath()).isEqualTo(wantGoogleCredentialsPath);
  }

  @Test
  public void testNotEqual_withAdminUrlNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminRootUrl("http://example.com/1").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminRootUrl("http://example.com/2").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withAdminUrlEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminRootUrl("http://example.com/1").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminRootUrl("http://example.com/1").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withAdminServicePathNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminServicePath("http://example.com/1").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminServicePath("http://example.com/2").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withAdminServicePathEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminServicePath("http://example.com/1").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminServicePath("http://example.com/1").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withTargetPrincipalNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withTargetPrincipal("joe@example.com").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withTargetPrincipal("steve@example.com").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withTargetPrincipalEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withTargetPrincipal("joe@example.com").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withTargetPrincipal("joe@example.com").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withDelegatesNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder()
            .withDelegates(Collections.singletonList("joe@example.com"))
            .build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder()
            .withDelegates(Collections.singletonList("steve@example.com"))
            .build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withDelegatesEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder()
            .withDelegates(Collections.singletonList("joe@example.com"))
            .build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder()
            .withDelegates(Collections.singletonList("joe@example.com"))
            .build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }
}
