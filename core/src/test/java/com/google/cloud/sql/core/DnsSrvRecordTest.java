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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class DnsSrvRecordTest {

  @Test
  public void testValidSrvRecord() {
    DnsSrvRecord r = new DnsSrvRecord("0 10 3307 sample-project:us-central1:my-database.");
    assertThat(r.getTarget()).isEqualTo("sample-project:us-central1:my-database.");
    assertThat(r.getPort()).isEqualTo(3307);
    assertThat(r.getWeight()).isEqualTo(10);
    assertThat(r.getPriority()).isEqualTo(0);
  }

  @Test
  public void testInvalidSrvRecordThrows() {
    assertThrows(IllegalArgumentException.class, () -> new DnsSrvRecord("bad record format"));
  }
}
