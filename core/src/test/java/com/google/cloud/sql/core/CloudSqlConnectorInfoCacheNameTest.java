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
public class CloudSqlConnectorInfoCacheNameTest {

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
}
