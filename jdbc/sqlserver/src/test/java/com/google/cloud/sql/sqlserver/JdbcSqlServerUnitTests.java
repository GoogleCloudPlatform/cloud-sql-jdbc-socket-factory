/*
 * Copyright 2022 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.sqlserver;

import static com.google.common.truth.Truth.assertThat;

import com.google.cloud.sql.core.CoreSocketFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdbcSqlServerUnitTests {

  private static final String CONNECTION_NAME = "my-project:my-region:my-instance";

  @Test
  public void checkConnectionStringNoQueryParams() {
    String socketFactoryConstructorArg = CONNECTION_NAME;
    SocketFactory socketFactory = new SocketFactory(socketFactoryConstructorArg);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY)).isEqualTo(
        CONNECTION_NAME);
  }

  @Test
  public void checkConnectionStringWithQueryParam() {
    String socketFactoryConstructorArg = String.format("%s?%s=%s", CONNECTION_NAME, "ipTypes", "PRIVATE");
    SocketFactory socketFactory = new SocketFactory(socketFactoryConstructorArg);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY)).isEqualTo(
        CONNECTION_NAME);
    assertThat(socketFactory.props.get("ipTypes")).isEqualTo(
        "PRIVATE");
  }

}
