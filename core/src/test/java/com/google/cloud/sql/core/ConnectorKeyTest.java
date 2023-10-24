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

package com.google.cloud.sql.core;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.ConnectionConfig;
import com.google.cloud.sql.IpType;
import java.util.Arrays;
import org.junit.Test;

public class ConnectorKeyTest {

  @Test
  public void testKeysEqual_withCloudSqlInstanceEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder().withCloudSqlInstance("proj:name:inst").build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder().withCloudSqlInstance("proj:name:inst").build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withCloudSqlInstanceNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder().withCloudSqlInstance("proj:name:inst1").build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder().withCloudSqlInstance("proj:name:inst2").build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withUnixSocketPathNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withUnixSocketPath("/path/1")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withUnixSocketPath("/path/2")
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withIpTypesNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withIpTypes(Arrays.asList(IpType.PUBLIC))
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withIpTypes(Arrays.asList(IpType.PRIVATE))
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withUnixSocketPathSuffixEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withUnixSocketPathSuffix(null)
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withUnixSocketPathSuffix("/.psql.5432")
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysNotEqual_withAdminUrlNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminRootUrl("http://example.com/1")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminRootUrl("http://example.com/2")
                .build());

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withAdminUrlEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminRootUrl("http://example.com/1")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminRootUrl("http://example.com/1")
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysNotEqual_withAdminServicePathNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminServicePath("http://example.com/1")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminServicePath("http://example.com/2")
                .build());

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withAdminServicePathEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminServicePath("http://example.com/1")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAdminServicePath("http://example.com/1")
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysNotEqual_withTargetPrincipalNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withTargetPrincipal("joe@example.com")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withTargetPrincipal("steve@example.com")
                .build());

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withTargetPrincipalEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withTargetPrincipal("joe@example.com")
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withTargetPrincipal("joe@example.com")
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysNotEqual_withDelegatesNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withDelegates(Arrays.asList("joe@example.com"))
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withDelegates(Arrays.asList("steve@example.com"))
                .build());

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withDelegatesEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withDelegates(Arrays.asList("joe@example.com"))
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withDelegates(Arrays.asList("joe@example.com"))
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysNotEqual_withAuthTypeNotEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAuthType(AuthType.PASSWORD)
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAuthType(AuthType.IAM)
                .build());

    assertThat(k1).isNotEqualTo(k2);
    assertThat(k1.hashCode()).isNotEqualTo(k2.hashCode());
  }

  @Test
  public void testKeysEqual_withAuthTypeEqual() {
    ConnectorKey k1 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAuthType(AuthType.IAM)
                .build());
    ConnectorKey k2 =
        new ConnectorKey(
            new ConnectionConfig.Builder()
                .withCloudSqlInstance("proj:name:inst1")
                .withAuthType(AuthType.IAM)
                .build());

    assertThat(k1).isEqualTo(k2);
    assertThat(k1.hashCode()).isEqualTo(k2.hashCode());
  }
}
