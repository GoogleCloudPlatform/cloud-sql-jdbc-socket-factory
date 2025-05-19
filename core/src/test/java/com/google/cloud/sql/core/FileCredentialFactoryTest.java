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
import static org.junit.Assert.*;

import com.google.auth.oauth2.GoogleCredentials;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileCredentialFactoryTest {

  @Test
  public void getCredentials_failsWhenNoFileExists() {
    FileCredentialFactory f = new FileCredentialFactory("nope");
    assertThrows(IllegalStateException.class, f::getCredentials);
  }

  @Test
  public void getCredentials_loadsFromFilePath() {
    String path = FileCredentialFactoryTest.class.getResource("/sample-credentials.json").getFile();
    FileCredentialFactory f = new FileCredentialFactory(path);
    GoogleCredentials c = f.getCredentials();
    assertThat(c.getQuotaProjectId()).isEqualTo("sample");
  }
}
