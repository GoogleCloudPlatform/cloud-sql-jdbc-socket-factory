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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.naming.NameNotFoundException;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/** DnsJavaResolver is a DnsResolver that uses the dnsjava library to perform DNS lookups. */
public class DnsJavaResolver implements DnsResolver {

  private final SimpleResolver resolver;

  /** Creates a resolver using the system's default DNS settings. */
  public DnsJavaResolver() {
    this.resolver = null; // dnsjava's Lookup uses default resolver if null.
  }

  /**
   * Creates a DNS resolver that uses a specific DNS server.
   *
   * @param dnsServer the DNS server hostname
   * @param port the DNS server port (DNS servers usually use port 53)
   */
  public DnsJavaResolver(String dnsServer, int port) {
    try {
      this.resolver = new SimpleResolver(dnsServer);
      this.resolver.setPort(port);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Unknown DNS server host: " + dnsServer, e);
    }
  }

  /**
   * Returns DNS TXT records for a domain name, sorted alphabetically.
   *
   * @param domainName the domain name to lookup
   * @return the list of records
   * @throws NameNotFoundException when the domain name does not resolve.
   */
  @Override
  public Collection<String> resolveTxt(String domainName) throws NameNotFoundException {
    try {
      // 1. Create a Lookup object for the TXT record type.
      Lookup lookup = new Lookup(domainName, Type.TXT);

      // 2. Set the custom resolver if one was provided in the constructor.
      if (this.resolver != null) {
        lookup.setResolver(this.resolver);
      }

      // 3. Execute the DNS query.
      lookup.run();

      // 4. Check the result of the lookup.
      int resultCode = lookup.getResult();
      if (resultCode == Lookup.HOST_NOT_FOUND) {
        throw new NameNotFoundException("DNS record not found for " + domainName);
      }
      if (resultCode == Lookup.TYPE_NOT_FOUND) {
        throw new NameNotFoundException("DNS record type TXT not found for " + domainName);
      }
      if (resultCode != Lookup.SUCCESSFUL) {
        throw new RuntimeException(
            "DNS lookup failed for " + domainName + ": " + lookup.getErrorString());
      }

      // 5. Process the records, sort them, and return.
      Record[] records = lookup.getAnswers();
      if (records == null || records.length == 0) {
        throw new NameNotFoundException("DNS record type TXT not found for " + domainName);
      }

      // A single TXT record can contain multiple strings, so we use flatMap.
      return Arrays.stream(records)
          .map(r -> (TXTRecord) r)
          .flatMap(txtRecord -> txtRecord.getStrings().stream())
          .sorted() // sort multiple records alphabetically
          .collect(Collectors.toList());

    } catch (TextParseException e) {
      // This happens if the domainName is not a valid format.
      throw new RuntimeException("Invalid domain name format: " + domainName, e);
    }
  }

  /**
   * Resolve an A record.
   *
   * @param hostName the hostname to look up
   * @return the resolved IP addresses
   * @throws UnknownHostException if no records are found.
   */
  @Override
  public List<InetAddress> resolveHost(String hostName) throws UnknownHostException {
    try {
      Lookup lookup = new Lookup(hostName, Type.A);
      if (this.resolver != null) {
        lookup.setResolver(this.resolver);
      }
      lookup.run();

      int resultCode = lookup.getResult();
      if (resultCode == Lookup.HOST_NOT_FOUND) {
        throw new UnknownHostException("DNS record not found for " + hostName);
      }
      if (resultCode != Lookup.SUCCESSFUL) {
        throw new UnknownHostException(
            "DNS lookup failed for " + hostName + ": " + lookup.getErrorString());
      }

      Record[] records = lookup.getAnswers();
      if (records == null || records.length == 0) {
        return Collections.emptyList();
      }

      return Arrays.stream(records)
          .map(r -> (ARecord) r)
          .map(ARecord::getAddress)
          .collect(Collectors.toList());

    } catch (TextParseException e) {
      throw new UnknownHostException("Invalid domain name format: " + hostName);
    }
  }
}
