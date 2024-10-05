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
import static org.junit.Assert.assertThrows;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.common.base.Objects;
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
    final String wantAdminQuotaProject = "myNewProject";
    ConnectorConfig cc =
        new ConnectorConfig.Builder()
            .withTargetPrincipal(wantTargetPrincipal)
            .withDelegates(wantDelegates)
            .withAdminRootUrl(wantAdminRootUrl)
            .withAdminServicePath(wantAdminServicePath)
            .withAdminQuotaProject(wantAdminQuotaProject)
            .build();

    assertThat(cc.getTargetPrincipal()).isEqualTo(wantTargetPrincipal);
    assertThat(cc.getDelegates()).isEqualTo(wantDelegates);
    assertThat(cc.getAdminRootUrl()).isEqualTo(wantAdminRootUrl);
    assertThat(cc.getAdminServicePath()).isEqualTo(wantAdminServicePath);
    assertThat(cc.getAdminQuotaProject()).isEqualTo(wantAdminQuotaProject);
  }

  @Test
  public void testBuild_withGoogleCredentialsPath() {
    final String wantGoogleCredentialsPath = "/path/to/credentials";
    ConnectorConfig cc =
        new ConnectorConfig.Builder().withGoogleCredentialsPath(wantGoogleCredentialsPath).build();
    assertThat(cc.getGoogleCredentialsPath()).isEqualTo(wantGoogleCredentialsPath);
  }

  @Test
  public void testBuild_withGoogleCredentials() {
    final GoogleCredentials wantGoogleCredentials = GoogleCredentials.create(null);
    ConnectorConfig cc =
        new ConnectorConfig.Builder().withGoogleCredentials(wantGoogleCredentials).build();
    assertThat(cc.getGoogleCredentials()).isSameInstanceAs(wantGoogleCredentials);
  }

  @Test
  public void testBuild_withGoogleCredentialsSupplier() {
    final Supplier<GoogleCredentials> wantGoogleCredentialSupplier =
        () -> GoogleCredentials.create(null);
    ConnectorConfig cc =
        new ConnectorConfig.Builder()
            .withGoogleCredentialsSupplier(wantGoogleCredentialSupplier)
            .build();
    assertThat(cc.getGoogleCredentialsSupplier()).isSameInstanceAs(wantGoogleCredentialSupplier);
  }

  @Test
  public void testBuild_withUniverseDomain() {
    final String wantUniverseDomain = "test-universe.test";
    ConnectorConfig cc =
        new ConnectorConfig.Builder().withUniverseDomain(wantUniverseDomain).build();
    assertThat(cc.getUniverseDomain()).isEqualTo(wantUniverseDomain);
  }

  @Test
  public void testBuild_withRefreshStrategyDefault() {
    ConnectorConfig cc = new ConnectorConfig.Builder().build();
    assertThat(cc.getRefreshStrategy()).isEqualTo(RefreshStrategy.BACKGROUND);
  }

  @Test
  public void testBuild_withRefreshStrategyBackground() {
    ConnectorConfig cc =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.BACKGROUND).build();
    assertThat(cc.getRefreshStrategy()).isEqualTo(RefreshStrategy.BACKGROUND);
  }

  @Test
  public void testBuild_withRefreshStrategyLazy() {
    ConnectorConfig cc =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.LAZY).build();
    assertThat(cc.getRefreshStrategy()).isEqualTo(RefreshStrategy.LAZY);
  }

  @Test
  public void testBuild_failsWhenAdminAPIAndUniverseDomainAreSet() {
    final String wantAdminRootUrl = "https://googleapis.example.com/";
    final String wantUniverseDomain = "test-universe.test";

    assertThrows(
        IllegalStateException.class,
        () ->
            new ConnectorConfig.Builder()
                .withAdminRootUrl(wantAdminRootUrl)
                .withUniverseDomain(wantUniverseDomain)
                .build());
  }

  @Test
  public void testBuild_failsWhenManyGoogleCredentialFieldsSet() {
    final Supplier<GoogleCredentials> wantGoogleCredentialSupplier =
        () -> GoogleCredentials.create(null);
    final GoogleCredentials wantGoogleCredentials = GoogleCredentials.create(null);
    final String wantGoogleCredentialsPath = "/path/to/credentials";

    assertThrows(
        IllegalStateException.class,
        () ->
            new ConnectorConfig.Builder()
                .withGoogleCredentials(wantGoogleCredentials)
                .withGoogleCredentialsSupplier(wantGoogleCredentialSupplier)
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            new ConnectorConfig.Builder()
                .withGoogleCredentialsPath(wantGoogleCredentialsPath)
                .withGoogleCredentialsSupplier(wantGoogleCredentialSupplier)
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            new ConnectorConfig.Builder()
                .withGoogleCredentialsPath(wantGoogleCredentialsPath)
                .withGoogleCredentials(wantGoogleCredentials)
                .build());
    assertThrows(
        IllegalStateException.class,
        () ->
            new ConnectorConfig.Builder()
                .withGoogleCredentialsPath(wantGoogleCredentialsPath)
                .withGoogleCredentials(wantGoogleCredentials)
                .withGoogleCredentialsSupplier(wantGoogleCredentialSupplier)
                .build());
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
  public void testNotEqual_withRefreshStrategyNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.LAZY).build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.BACKGROUND).build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withRefreshStrategyEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.LAZY).build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withRefreshStrategy(RefreshStrategy.LAZY).build();

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

  @Test
  public void testNotEqual_withGoogleCredentialsNotEqual() {
    GoogleCredentials c1 =
        GoogleCredentials.newBuilder()
            .setAccessToken(new AccessToken("c1", null))
            .setQuotaProjectId("project1")
            .build();
    GoogleCredentials c2 =
        GoogleCredentials.newBuilder()
            .setAccessToken(new AccessToken("c2", null))
            .setQuotaProjectId("project2")
            .build();
    ConnectorConfig k1 = new ConnectorConfig.Builder().withGoogleCredentials(c1).build();
    ConnectorConfig k2 = new ConnectorConfig.Builder().withGoogleCredentials(c2).build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withGoogleCredentialsEqual() {
    GoogleCredentials c1 = GoogleCredentials.create(new AccessToken("c1", null));
    GoogleCredentials c2 = GoogleCredentials.create(new AccessToken("c1", null));
    ConnectorConfig k1 = new ConnectorConfig.Builder().withGoogleCredentials(c1).build();
    ConnectorConfig k2 = new ConnectorConfig.Builder().withGoogleCredentials(c2).build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withGoogleCredentialsPathNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withGoogleCredentialsPath("/path/1.json").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withGoogleCredentialsPath("/path/2.json").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withGoogleCredentialsPathEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withGoogleCredentialsPath("/path/1.json").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withGoogleCredentialsPath("/path/1.json").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withGoogleCredentialsSupplierNotEqual() {
    Supplier<GoogleCredentials> c1 = () -> GoogleCredentials.create(new AccessToken("c1", null));
    Supplier<GoogleCredentials> c2 = () -> GoogleCredentials.create(new AccessToken("c2", null));

    ConnectorConfig k1 = new ConnectorConfig.Builder().withGoogleCredentialsSupplier(c1).build();
    ConnectorConfig k2 = new ConnectorConfig.Builder().withGoogleCredentialsSupplier(c2).build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withGoogleCredentialsSupplierEqual() {
    Supplier<GoogleCredentials> c1 = () -> GoogleCredentials.create(new AccessToken("c1", null));
    ConnectorConfig k1 = new ConnectorConfig.Builder().withGoogleCredentialsSupplier(c1).build();
    ConnectorConfig k2 = new ConnectorConfig.Builder().withGoogleCredentialsSupplier(c1).build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withAdminQuotaProjectNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminQuotaProject("myNewProject").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminQuotaProject("anotherProject").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testEqual_withAdminQuotaProjectEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withAdminQuotaProject("myNewProject").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withAdminQuotaProject("myNewProject").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withUniverseDomainNotEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withUniverseDomain("test-universe.test").build();
    ConnectorConfig k2 = new ConnectorConfig.Builder().withUniverseDomain("googleapis.com").build();

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testNotEqual_withUniverseDomainEqual() {
    ConnectorConfig k1 =
        new ConnectorConfig.Builder().withUniverseDomain("test-universe.test").build();
    ConnectorConfig k2 =
        new ConnectorConfig.Builder().withUniverseDomain("test-universe.test").build();

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testHashCode() {
    final String wantTargetPrincipal = "test@example.com";
    final List<String> wantDelegates = Arrays.asList("test1@example.com", "test2@example.com");
    final String wantAdminRootUrl = "https://googleapis.example.com/";
    final String wantAdminServicePath = "sqladmin/";
    final String wantGoogleCredentialsPath = "/path/to/credentials";
    final String wantAdminQuotaProject = "myNewProject";
    final RefreshStrategy wantRefreshStrategy = RefreshStrategy.LAZY;
    ConnectorConfig cc =
        new ConnectorConfig.Builder()
            .withTargetPrincipal(wantTargetPrincipal)
            .withDelegates(wantDelegates)
            .withAdminRootUrl(wantAdminRootUrl)
            .withAdminServicePath(wantAdminServicePath)
            .withGoogleCredentialsPath(wantGoogleCredentialsPath)
            .withAdminQuotaProject(wantAdminQuotaProject)
            .withRefreshStrategy(wantRefreshStrategy)
            .build();

    assertThat(cc.hashCode())
        .isEqualTo(
            Objects.hashCode(
                wantTargetPrincipal,
                wantDelegates,
                wantAdminRootUrl,
                wantAdminServicePath,
                null, // googleCredentialsSupplier
                null, // googleCredentials
                wantGoogleCredentialsPath,
                wantAdminQuotaProject,
                null, // universeDomain
                wantRefreshStrategy, // refreshStrategy
                null // instanceNameResolver
                ));
  }
}
