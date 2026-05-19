/*
 * Copyright 2025 Google LLC
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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private static final Pattern PSC_DNS_PATTERN =
      Pattern.compile(
          "^([a-f0-9]{12})\\.([^.]+)\\.([a-z0-9]+-[a-z0-9]+)\\.(sql|sql-psa|sql-psc)\\.goog\\.?$");

  private final DnsResolver dnsResolver;
  private com.google.api.services.sqladmin.SQLAdmin sqlAdmin;

  public DnsInstanceConnectionNameResolver(DnsResolver dnsResolver) {
    this.dnsResolver = dnsResolver;
  }

  void setSqlAdmin(com.google.api.services.sqladmin.SQLAdmin sqlAdmin) {
    this.sqlAdmin = sqlAdmin;
  }

  @Override
  public CloudSqlInstanceName resolve(final String name) {
    if (CloudSqlInstanceName.isValidInstanceName(name)) {
      // name contains a well-formed instance name.
      return new CloudSqlInstanceName(name);
    }

    String cleanName = name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    Matcher pscDnsMatcher = PSC_DNS_PATTERN.matcher(cleanName.toLowerCase());
    if (pscDnsMatcher.matches()) {
      String region = pscDnsMatcher.group(3);
      if (sqlAdmin == null) {
        throw new IllegalArgumentException("SQLAdmin client is not configured in the resolver.");
      }
      String dnsNameWithDot = cleanName.endsWith(".") ? cleanName : cleanName + ".";
      try {
        com.google.api.services.sqladmin.model.ConnectSettings metadata =
            new ApiClientRetryingCallable<>(
                    () -> sqlAdmin.connect().resolve(region, dnsNameWithDot).execute())
                .call();
        return new CloudSqlInstanceName(metadata.getConnectionName(), cleanName);
      } catch (Exception ex) {
        throw new IllegalArgumentException("Failed to resolve PSC DNS name: " + cleanName, ex);
      }
    }

    if (CloudSqlInstanceName.isValidDomain(name)) {
      // name contains a well-formed domain name.
      return resolveDomainName(name);
    }

    // name is not well-formed, and therefore cannot be resolved.
    throw new IllegalArgumentException(
        String.format(
            "Unable to resolve database instance for \"%s\". It should be a "
                + "well-formed instance name or domain name.",
            name));
  }

  private CloudSqlInstanceName resolveDomainName(String name) {
    String current = name;
    IllegalArgumentException txtException = null;
    Set<String> visited = new HashSet<>();

    for (int depth = 0; depth < 10; depth++) {
      if (!visited.add(current.toLowerCase())) {
        throw new IllegalArgumentException(
            String.format("CNAME loop detected for \"%s\"", current));
      }

      if (CloudSqlInstanceName.isValidInstanceName(current)) {
        return new CloudSqlInstanceName(current, name);
      }

      String cleanCurrent =
          current.endsWith(".") ? current.substring(0, current.length() - 1) : current;
      Matcher pscDnsMatcher = PSC_DNS_PATTERN.matcher(cleanCurrent.toLowerCase());
      if (pscDnsMatcher.matches()) {
        String region = pscDnsMatcher.group(3);
        if (sqlAdmin == null) {
          throw new IllegalArgumentException("SQLAdmin client is not configured in the resolver.");
        }
        String dnsNameWithDot = cleanCurrent.endsWith(".") ? cleanCurrent : cleanCurrent + ".";
        try {
          com.google.api.services.sqladmin.model.ConnectSettings metadata =
              new ApiClientRetryingCallable<>(
                      () -> sqlAdmin.connect().resolve(region, dnsNameWithDot).execute())
                  .call();
          return new CloudSqlInstanceName(metadata.getConnectionName(), name);
        } catch (Exception ex) {
          throw new IllegalArgumentException("Failed to resolve PSC DNS name: " + cleanCurrent, ex);
        }
      }

      if (!CloudSqlInstanceName.isValidDomain(current)) {
        throw new IllegalArgumentException(
            String.format("[%s] CNAME target is not a valid domain name", current));
      }

      final String finalCurrent = current; // Effectively final copy for lambda
      Collection<String> instanceNames;
      try {
        instanceNames = this.dnsResolver.resolveTxt(finalCurrent);
        if (!instanceNames.isEmpty()) {
          // Use the first valid instance name from the list
          return instanceNames.stream()
              .map(
                  target -> {
                    try {
                      return new CloudSqlInstanceName(target, finalCurrent);
                    } catch (IllegalArgumentException e) {
                      logger.info(
                          "Unable to parse instance name in TXT record for "
                              + "domain name \"{}\" with target \"{}\"",
                          finalCurrent,
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
                          String.format(
                              "Unable to parse values of TXT record for \"%s\".", finalCurrent)));
        }
      } catch (NameNotFoundException ne) {
        txtException =
            new IllegalArgumentException(
                String.format(
                    "Unable to resolve TXT record containing the instance name for "
                        + "domain name \"%s\".",
                    current),
                ne);
      }

      // If TXT lookup failed or returned no valid records, check CNAME record
      String cname;
      try {
        cname = this.dnsResolver.resolveCname(current);
      } catch (NameNotFoundException ne) {
        // If CNAME lookup also fails, throw the original TXT exception
        if (txtException != null) {
          throw txtException;
        }
        throw new IllegalArgumentException(
            String.format("Unable to resolve CNAME record for domain name \"%s\".", current), ne);
      }

      cname = cname.endsWith(".") ? cname.substring(0, cname.length() - 1) : cname;
      current = cname;
    }

    throw new IllegalArgumentException(
        String.format("CNAME lookup limit exceeded (max 10) for \"%s\"", name));
  }
}
