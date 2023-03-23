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
import java.io.UnsupportedEncodingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class JdbcSqlServerUnitTests {

  private static final String CONNECTION_NAME = "my-project:my-region:my-instance";

  @Test
  public void checkConnectionStringNoQueryParams() throws UnsupportedEncodingException {
    SocketFactory socketFactory = new SocketFactory(CONNECTION_NAME);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY))
        .isEqualTo(CONNECTION_NAME);
  }

  @Test
  public void checkConnectionStringWithQueryParam() throws UnsupportedEncodingException {
    String socketFactoryConstructorArg =
        String.format("%s?%s=%s", CONNECTION_NAME, "ipTypes", "PRIVATE");
    SocketFactory socketFactory = new SocketFactory(socketFactoryConstructorArg);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY))
        .isEqualTo(CONNECTION_NAME);
    assertThat(socketFactory.props.get("ipTypes")).isEqualTo("PRIVATE");
  }

  @Test
  public void checkConnectionStringWithEmptyQueryParam() throws UnsupportedEncodingException {
    String socketFactoryConstructorArg = String.format("%s?", CONNECTION_NAME);
    SocketFactory socketFactory = new SocketFactory(socketFactoryConstructorArg);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY))
        .isEqualTo(CONNECTION_NAME);
    assertThat(socketFactory.props.get("ipTypes")).isEqualTo(null);
  }

  @Test
  public void checkConnectionStringWithUrlEncodedParam() throws UnsupportedEncodingException {
    String socketFactoryConstructorArg =
        String.format("%s?token=%s", CONNECTION_NAME, "abc%20def%20xyz%2F%26%3D");
    SocketFactory socketFactory = new SocketFactory(socketFactoryConstructorArg);
    assertThat(socketFactory.props.get(CoreSocketFactory.CLOUD_SQL_INSTANCE_PROPERTY))
        .isEqualTo(CONNECTION_NAME);
    assertThat(socketFactory.props.get("token")).isEqualTo("abc def xyz/&=");
  }

  @Test(expected = IllegalArgumentException.class)
  public void checkConnectionStringWithParamMissingKey() throws UnsupportedEncodingException {
    String socketFactoryConstructorArg = String.format("%s?=%s", CONNECTION_NAME, "PRIVATE");
    new SocketFactory(socketFactoryConstructorArg);
  }

  @Test(expected = IllegalArgumentException.class)
  public void checkConnectionStringWithParamMissingValue() throws UnsupportedEncodingException {
    String socketFactoryConstructorArg =
        String.format("%s?enableIamAuth=true&%s", CONNECTION_NAME, "ipTypes");
    new SocketFactory(socketFactoryConstructorArg);
  }
}
