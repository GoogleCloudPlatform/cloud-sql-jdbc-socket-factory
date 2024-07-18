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

/** Resolves the Cloud SQL Instance from the configuration name. */
interface InstanceConnectionNameResolver {

  /**
   * Resolves the CloudSqlInstanceName from a configuration string value.
   *
   * @param name the configuration string
   * @return the CloudSqlInstanceName
   * @throws IllegalArgumentException if the name cannot be resolved.
   */
  CloudSqlInstanceName resolve(String name);
}
