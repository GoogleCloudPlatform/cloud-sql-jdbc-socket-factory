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

  /**
   * The domain name pattern in accordance with RFC 1035, RFC 1123 and RFC 2181.
   *
   * <p>Explanation of the Regex:
   *
   * <p>^: Matches the beginning of the string.<br>
   * (?=.{1,255}$): Positive lookahead assertion to ensure the domain name is between 1 and 255
   * characters long.<br>
   * (?!-): Negative lookahead assertion to prevent hyphens at the beginning.<br>
   * [A-Za-z0-9-]+: Matches one or more alphanumeric characters or hyphens.<br>
   * (\\.[A-Za-z0-9-]+)*: Matches zero or more occurrences of a period followed by one or more
   * alphanumeric characters or hyphens (for subdomains).<br>
   * \\.: Matches a period before the TLD.<br>
   * [A-Za-z]{2,}: Matches two or more letters for the TLD.<br>
   * $: Matches the end of the string.<br>
   */
  private static final Pattern DOMAIN_NAME =
      Pattern.compile("^(?=.{1,255}$)(?!-)[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");

  private final String projectId;
  private final String regionId;
  private final String instanceId;
  private final String connectionName;
  private final String domainName;

  /**
   * Validates that a string is a well-formed domain name.
   *
   * @param domain the domain name to check
   * @return true if domain is a well-formed domain name.
   */
  public static boolean isValidDomain(String domain) {
    Matcher matcher = DOMAIN_NAME.matcher(domain);
    return matcher.matches();
  }

  /**
   * Initializes a new CloudSqlInstanceName class.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   */
  CloudSqlInstanceName(String connectionName) {
    this(connectionName, null);
  }

  /**
   * Initializes a new CloudSqlInstanceName class containing the domain name.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param domainName the domain name used to look up the instance, or null.
   */
  CloudSqlInstanceName(String connectionName, String domainName) {
    Matcher matcher = CONNECTION_NAME.matcher(connectionName);
    checkArgument(
        matcher.matches(),
        String.format(
            "[%s] Cloud SQL connection name is invalid, expected string in the form of"
                + " \"<PROJECT_ID>:<REGION_ID>:<INSTANCE_ID>\".",
            connectionName));
    this.connectionName = connectionName;
    this.projectId = matcher.group(1);
    this.regionId = matcher.group(3);
    this.instanceId = matcher.group(4);

    // Only set this.domainName when it is not empty
    if (domainName != null && !domainName.isEmpty()) {
      Matcher domainMatcher = DOMAIN_NAME.matcher(domainName);
      checkArgument(
          domainMatcher.matches(),
          String.format("[%s] Domain name is invalid, expected a valid domain name", domainName));
      this.domainName = domainName;
    } else {
      this.domainName = null;
    }
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

  String getDomainName() {
    return domainName;
  }

  @Override
  public String toString() {
    return connectionName;
  }
}
