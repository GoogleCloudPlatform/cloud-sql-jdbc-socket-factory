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

import java.util.Collection;
import java.util.Objects;
import javax.naming.NameNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of InstanceConnectionNameResolver that uses DNS TXT records to resolve an
 * instance name from a domain name.
 */
class DnsInstanceConnectionNameResolver implements InstanceConnectionNameResolver {
  private static final Logger logger =
      LoggerFactory.getLogger(DnsInstanceConnectionNameResolver.class);

  private final DnsResolver dnsResolver;

  public DnsInstanceConnectionNameResolver(DnsResolver dnsResolver) {
    this.dnsResolver = dnsResolver;
  }

  @Override
  public CloudSqlInstanceName resolve(final String name) {
    // Attempt to parse the instance name
    try {
      return new CloudSqlInstanceName(name);
    } catch (IllegalArgumentException e) {
      // Not a well-formed instance name.
    }

    // Next, attempt to resolve DNS name.
    Collection<String> instanceNames;
    try {
      instanceNames = this.dnsResolver.resolveTxt(name);
    } catch (NameNotFoundException ne) {
      // No DNS record found. This is not a valid instance name.
      throw new IllegalArgumentException(
          String.format("Unable to resolve TXT record for \"%s\".", name));
    }

    // Use the first valid instance name from the list
    // or throw an IllegalArgumentException if none of the values can be parsed.
    return instanceNames.stream()
        .map(
            target -> {
              try {
                return new CloudSqlInstanceName(target);
              } catch (IllegalArgumentException e) {
                logger.info(
                    "Unable to parse instance name in TXT record for "
                        + "domain name \"{}\" with target \"{}\"",
                    name,
                    target,
                    e);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    String.format("Unable to parse values of TXT record for \"%s\".", name)));
  }
}
