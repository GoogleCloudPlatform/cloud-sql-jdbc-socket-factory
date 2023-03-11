/*
 * Copyright 2022 Google LLC. All Rights Reserved.
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CloudSqlInstanceTest {
  
  @Test
  public void timeUntilRefreshImmediate() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(3)));
    assertThat(CloudSqlInstance.secondsUntilRefresh(expiration)).isEqualTo(0L);
  }

  @Test
  public void timeUntilRefresh1Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofMinutes(59)));
    long expected = Duration.ofMinutes(59).minus(Duration.ofMinutes(4)).getSeconds();
    Assert.assertEquals(CloudSqlInstance.secondsUntilRefresh(expiration), expected, 1);
  }

  @Test
  public void timeUntilRefresh24Hr() {
    Date expiration = Date.from(Instant.now().plus(Duration.ofHours(23)));
    long expected = Duration.ofHours(23).dividedBy(2).getSeconds();
    Assert.assertEquals(CloudSqlInstance.secondsUntilRefresh(expiration), expected, 1);
  }
}
