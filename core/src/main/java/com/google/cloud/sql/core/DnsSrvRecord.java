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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This represents the value of an SRV DNS Record. */
class DnsSrvRecord {
  private static final Pattern RECORD_FORMAT = Pattern.compile("(\\d+) +(\\d+) +(\\d+) +(.*)");
  private final int priority;
  private final int weight;
  private final int port;
  private final String target;

  DnsSrvRecord(String record) {
    Matcher m = RECORD_FORMAT.matcher(record);
    if (!m.find()) {
      throw new IllegalArgumentException("Malformed SRV record: " + record);
    }

    this.priority = Integer.parseInt(m.group(1));
    this.weight = Integer.parseInt(m.group(2));
    this.port = Integer.parseInt(m.group(3));
    this.target = m.group(4);
  }

  public int getPriority() {
    return priority;
  }

  public int getWeight() {
    return weight;
  }

  public int getPort() {
    return port;
  }

  public String getTarget() {
    return target;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DnsSrvRecord)) {
      return false;
    }
    DnsSrvRecord that = (DnsSrvRecord) o;
    return priority == that.priority
        && weight == that.weight
        && port == that.port
        && target.equals(that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(priority, weight, port, target);
  }
}
