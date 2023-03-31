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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class parses the different parts of a Cloud SQL Connection Name to allow users to easily
 * fetch the projectId, regionId, and instanceId.
 */
class CloudSqlInstanceName {

  // Unique identifier for each Cloud SQL instance in the format "PROJECT:REGION:INSTANCE"
  // Some legacy project ids are domain-scoped (e.g. "example.com:PROJECT:REGION:INSTANCE")
  private static final Pattern CONNECTION_NAME =
      Pattern.compile("([^:]+(:[^:]+)?):([^:]+):([^:]+)");
  private final String projectId;
  private final String regionId;
  private final String instanceId;
  private final String connectionName;

  /**
   * Initializes a new CloudSqlInstanceName class.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   */
  CloudSqlInstanceName(String connectionName) {
    this.connectionName = connectionName;
    Matcher matcher = CONNECTION_NAME.matcher(connectionName);
    checkArgument(
        matcher.matches(),
        String.format(
            "[%s] Cloud SQL connection name is invalid, expected string in the form of"
                + " \"<PROJECT_ID>:<REGION_ID>:<INSTANCE_ID>\".",
            connectionName));
    this.projectId = matcher.group(1);
    this.regionId = matcher.group(3);
    this.instanceId = matcher.group(4);
  }

  String getConnectionName() {
    return connectionName;
  }

  String getProjectId() {
    return projectId;
  }

  String getRegionId() {
    return regionId;
  }

  String getInstanceId() {
    return instanceId;
  }
}
