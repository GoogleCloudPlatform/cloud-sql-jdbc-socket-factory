/*
 * Copyright 2023 Google LLC
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

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.Test;

public class ConstantCredentialsFactoryTest {

  @Test
  public void testConstantCredentials() {
    GoogleCredentials c = GoogleCredentials.create(null);
    ConstantCredentialFactory f = new ConstantCredentialFactory(c);
    assertThat(f.getCredentials()).isSameInstanceAs(c);
  }
}
