/*
 * Copyright 2016 Google Inc. All Rights Reserved.
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

package com.google.cloud.sql.mysql;

import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * A MySQL {@link SocketFactory} that establishes a secure connection to a Cloud SQL instance using
 * ephemeral certificates.
 *
 * <p>The heavy lifting is done by the singleton {@link SslSocketFactory} class.
 */
public class SocketFactory implements com.mysql.cj.api.io.SocketFactory {
  private static final Logger logger = Logger.getLogger(SocketFactory.class.getName());

  private Socket socket;

  @Override
  public Socket connect(String host, int portNumber, Properties props, int loginTimeout) throws IOException {
    String instanceName = props.getProperty("cloudSqlInstance");
    checkArgument(
            instanceName != null,
            "cloudSqlInstance property not set. Please specify this property in the JDBC URL or "
                    + "the connection Properties with value in form \"project:region:instance\"");

    logger.info(String.format("Connecting to Cloud SQL instance [%s].", instanceName));

    this.socket = SslSocketFactory.getInstance().create(instanceName);
    return this.socket;
  }

  @Override
  public Socket beforeHandshake() {
    return socket;
  }

  @Override
  public Socket afterHandshake() {
    return socket;
  }
}
