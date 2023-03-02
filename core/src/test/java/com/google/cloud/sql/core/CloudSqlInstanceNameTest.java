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

    Assert.assertEquals(instanceName.getConnectionName(), connectionName);
    Assert.assertEquals(instanceName.getProjectId(), "my-project");
    Assert.assertEquals(instanceName.getRegionId(), "my-region");
    Assert.assertEquals(instanceName.getInstanceId(), "my-instance");
  }

  @Test
  public void parseLegacyConnectionName() {
    String connectionName = "google.com:my-project:my-region:my-instance";

    CloudSqlInstanceName instanceName = new CloudSqlInstanceName(connectionName);

    Assert.assertEquals(instanceName.getConnectionName(), connectionName);
    Assert.assertEquals(instanceName.getProjectId(), "my-project");
    Assert.assertEquals(instanceName.getRegionId(), "my-region");
    Assert.assertEquals(instanceName.getInstanceId(), "my-instance");
  }

  @Test
  public void parseBadConnectionName() {
    String connectionName = "my-project:my-instance";

    try {
      new CloudSqlInstanceName(connectionName);
    } catch (IllegalArgumentException ex) {
      assertThat(ex)
          .hasMessageThat()
          .contains("Cloud SQL connection name is invalid");
    }
  }

}
