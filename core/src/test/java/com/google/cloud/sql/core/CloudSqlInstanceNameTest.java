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
import static org.junit.Assert.assertThrows;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloudSqlInstanceNameTest {

  @Test
  public void parseStandardConnectionName() {
    String connectionName = "my-project:my-region:my-instance";

    CloudSqlInstanceName instanceName = new CloudSqlInstanceName(connectionName);

    Assert.assertEquals(connectionName, instanceName.getConnectionName());
    Assert.assertEquals("my-project", instanceName.getProjectId());
    Assert.assertEquals("my-region", instanceName.getRegionId());
    Assert.assertEquals("my-instance", instanceName.getInstanceId());
  }

  @Test
  public void parseLegacyConnectionName() {
    String connectionName = "google.com:my-project:my-region:my-instance";

    CloudSqlInstanceName instanceName = new CloudSqlInstanceName(connectionName);

    Assert.assertEquals(instanceName.getConnectionName(), connectionName);
    Assert.assertEquals("google.com:my-project", instanceName.getProjectId());
    Assert.assertEquals("my-region", instanceName.getRegionId());
    Assert.assertEquals("my-instance", instanceName.getInstanceId());
  }

  @Test
  public void parseBadConnectionName() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> new CloudSqlInstanceName("my-project:my-instance"));

    assertThat(ex).hasMessageThat().contains("Cloud SQL connection name is invalid");
  }

  @Test
  public void testValidDomains() {
    assertThat(CloudSqlInstanceName.isValidDomain("prod-db.mycompany.example.com")).isTrue();
    assertThat(CloudSqlInstanceName.isValidDomain("example.com.")).isTrue();
    assertThat(CloudSqlInstanceName.isValidDomain("-example.com")).isFalse();
    assertThat(CloudSqlInstanceName.isValidDomain("example")).isFalse();
    assertThat(CloudSqlInstanceName.isValidDomain("127.0.0.1")).isFalse();
    assertThat(CloudSqlInstanceName.isValidDomain("0:0:0:0:0:0:0:1")).isFalse();
  }

  @Test
  public void testIsInstanceDnsName() {
    // Valid DNS names
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-central1.sql.goog"))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-central1.sql.goog."))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-central1.sql-psc.goog"))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-central1.sql-psa.goog"))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("g123456789ab.proj.us-central1.sql.goog"))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("a.b.c.sql.goog")).isTrue();
    assertThat(
            CloudSqlInstanceName.isInstanceDnsName("label-with-hyphen.proj.us-central1.sql.goog"))
        .isTrue();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.uscentral.sql.goog"))
        .isTrue();

    // Invalid DNS names
    assertThat(CloudSqlInstanceName.isInstanceDnsName(null)).isFalse();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("")).isFalse();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("invalid-format")).isFalse();
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.global.sql.goog"))
        .isFalse();
    assertThat(
            CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-central1.global.sql.goog"))
        .isFalse();

    // Test with region having multiple hyphens (e.g. us-gov-west1)
    // This might fail with the current regex in CloudSqlInstanceName
    assertThat(CloudSqlInstanceName.isInstanceDnsName("abc123abc123.proj.us-gov-west1.sql.goog"))
        .isTrue();
  }
}
