/*
 * Copyright 2024 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.cloud.sql.ConnectorConfig;
import com.google.cloud.sql.CredentialFactory;
import com.google.common.collect.ImmutableList;
import java.util.concurrent.TimeUnit;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultConnectionInfoRepositoryIntegrationTests {
  private static final String QUOTA_PROJECT = System.getenv("QUOTA_PROJECT");
  private static final String CONNECTION_NAME = System.getenv("MYSQL_CONNECTION_NAME");

  private static final ImmutableList<String> requiredEnvVars =
      ImmutableList.of("QUOTA_PROJECT", "MYSQL_CONNECTION_NAME");
  @Rule public Timeout globalTimeout = new Timeout(80, TimeUnit.SECONDS);

  @BeforeClass
  public static void checkEnvVars() {
    // Check that required env vars are set
    requiredEnvVars.forEach(
        (varName) ->
            assertWithMessage(
                    String.format(
                        "Environment variable '%s' must be set to perform these tests.", varName))
                .that(System.getenv(varName))
                .isNotEmpty());
  }

  @Test
  public void testQuotaProjectIsSetOnAdminApiRequest() {
    ConnectorConfig config =
        new ConnectorConfig.Builder().withAdminQuotaProject(QUOTA_PROJECT).build();

    CredentialFactoryProvider credentialFactoryProvider = new CredentialFactoryProvider();
    CredentialFactory instanceCredentialFactory =
        credentialFactoryProvider.getInstanceCredentialFactory(config);
    DefaultConnectionInfoRepository repo =
        new DefaultConnectionInfoRepositoryFactory("cloud-sql-connector-connector-core")
            .create(instanceCredentialFactory.create(), config);

    assertThat(repo.getQuotaProject(CONNECTION_NAME)).isEqualTo(QUOTA_PROJECT);
  }
}
