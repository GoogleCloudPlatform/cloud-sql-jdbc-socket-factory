/*
 * Copyright 2023 Google LLC. All Rights Reserved.
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

import static com.google.cloud.sql.core.GcpConnectionFactoryProvider.IP_TYPES;
import static com.google.common.truth.Truth.assertThat;
import static io.r2dbc.spi.ConnectionFactoryOptions.DATABASE;
import static io.r2dbc.spi.ConnectionFactoryOptions.DRIVER;
import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;
import static io.r2dbc.spi.ConnectionFactoryOptions.PROTOCOL;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;

import io.r2dbc.spi.ConnectionFactoryOptions;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.MockedStatic;
import org.mockito.Mockito;


@RunWith(JUnit4.class)
public class GcpConnectionFactoryProviderMssqlTest extends GcpConnectionFactoryProviderTest {

  private static Map<String, String> IP_LABEL = new HashMap<String,String>() {{
    put("PUBLIC", "PRIMARY");
    put("PRIVATE", "PRIVATE");
  }};

  private ConnectionFactoryOptions privateIpOptions;
  private ConnectionFactoryOptions publicIpOptions;

  @Before
  public void setupOptions() {

    // Set up ConnectionFactoryOptions
    privateIpOptions = ConnectionFactoryOptions.builder().option(DRIVER, "gcp")
        .option(PROTOCOL, "mssql").option(USER, "fake_user").option(DATABASE, "fake_db")
        .option(HOST, fakeInstanceName).option(IP_TYPES, "PRIVATE").build();

    publicIpOptions = privateIpOptions.mutate().option(IP_TYPES, "PUBLIC").build();
  }

  public void setsCorrectOptionsForDriverHostAndPort(String ipType,
      ConnectionFactoryOptions options, String expectedIp) {
    try (MockedStatic<CoreSocketFactory> mockSocketFactory = Mockito.mockStatic(
        CoreSocketFactory.class)) {

      mockSocketFactory.when(CoreSocketFactory::getDefaultServerProxyPort).thenReturn(3307);
      mockSocketFactory.when(() -> CoreSocketFactory.getSslData(fakeInstanceName))
          .thenReturn(coreSocketFactoryStub.getCloudSqlInstance(fakeInstanceName).getSslData());

      mockSocketFactory.when(() -> CoreSocketFactory.getHostIp(fakeInstanceName, ipType))
          .thenReturn(coreSocketFactoryStub.getCloudSqlInstance(fakeInstanceName)
              .getPreferredIp(Arrays.asList(IP_LABEL.get(ipType))));

      GcpConnectionFactoryProviderMssql mysqlProvider = new GcpConnectionFactoryProviderMssql();

      // Use the PrivateIP options to make a Cloud SQL Connection Factory
      CloudSqlConnectionFactory csqlConnFactoryPrivate = (CloudSqlConnectionFactory) mysqlProvider.create(
          options);
      csqlConnFactoryPrivate.setBuilderHostAndPort();

      // Check that Driver, Host, and Port are set properly
      ConnectionFactoryOptions mysqlOptions = csqlConnFactoryPrivate.getBuilder().build();
      assertThat(mysqlProvider.supportedProtocol((String) mysqlOptions.getValue(DRIVER))).isTrue();
      assertThat((String) mysqlOptions.getValue(HOST)).isEqualTo(expectedIp);
      assertThat((int) mysqlOptions.getValue(PORT)).isEqualTo(
          CoreSocketFactory.getDefaultServerProxyPort());


    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void setsCorrectOptionsForDriverHostAndPortPrivate() {
    setsCorrectOptionsForDriverHostAndPort("PRIVATE", privateIpOptions, PRIVATE_IP);
  }

  @Test
  public void setsCorrectOptionsForDriverHostAndPortPublic() {
    setsCorrectOptionsForDriverHostAndPort("PUBLIC", publicIpOptions, PUBLIC_IP);
  }
}
