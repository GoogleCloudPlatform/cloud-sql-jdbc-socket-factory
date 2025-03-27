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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import java.io.IOException;
import java.net.Socket;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.WeakHashMap;
import java.util.function.Function;
import javax.net.ssl.SSLSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MonitoredCache periodically checks domain name resolution to ensure, if the domain name resolves
 * to a different instance than when MonitoredCache was created, MonitoredCache will close the cache
 * and any open sockets.
 */
class MonitoredCache implements ConnectionInfoCache {
  private static final Logger logger = LoggerFactory.getLogger(Connector.class);
  private final ConnectionInfoCache cache;
  // Use weak references to hold the open sockets. If a socket is no longer in
  // use by the application, the garabage collector will automatically remove
  // it from this set.
  private final Set<Socket> sockets =
      Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
  private final Function<ConnectionConfig, CloudSqlInstanceName> resolve;
  private final TimerTask task;

  MonitoredCache(
      ConnectionInfoCache cache,
      Timer instanceNameResolverTimer,
      Function<ConnectionConfig, CloudSqlInstanceName> resolve) {
    this.cache = cache;
    this.resolve = resolve;

    // If this was configured with a domain name, start the domain name check
    // and socket cleanup periodic task.
    if (!Strings.isNullOrEmpty(cache.getConfig().getDomainName())) {
      long failoverPeriod = cache.getConfig().getConnectorConfig().getFailoverPeriod().toMillis();
      this.task =
          new TimerTask() {
            @Override
            public void run() {
              checkDomainName();
            }
          };
      instanceNameResolverTimer.schedule(task, failoverPeriod, failoverPeriod);
    } else {
      this.task = null;
    }
  }

  @VisibleForTesting
  int getOpenSocketCount() {
    return sockets.size();
  }

  private void checkDomainName() {
    // Resolve the domain name again. If it changed, close the sockets
    try {
      CloudSqlInstanceName resolved = this.resolve.apply(cache.getConfig());
      if (!resolved.getConnectionName().equals(cache.getConfig().getCloudSqlInstance())) {
        logger.info(
            "Cloud SQL Instance associated with domain name {} changed from {} to {}.",
            cache.getConfig().getDomainName(),
            cache.getConfig().getCloudSqlInstance(),
            resolved.getConnectionName());
        this.close();
        return;
      }
    } catch (RuntimeException e) {
      // The domain name failed to resolve. Log the error and continue. Do not close the
      // connections on a dns error.
      logger.debug(
          "Cloud SQL Instance associated with domain name {} did not resolve {}.",
          cache.getConfig().getDomainName(),
          cache.getConfig().getCloudSqlInstance(),
          e);
    }

    // Iterate through the list of sockets and remove all closed sockets.
    // this will reuse the existing ArrayList.
    synchronized (sockets) {
      for (Iterator<Socket> it = sockets.iterator(); it.hasNext(); ) {
        Socket socket = it.next();
        if (socket.isClosed()) {
          it.remove();
        }
      }
    }
  }

  @Override
  public ConnectionMetadata getConnectionMetadata(long timeoutMs) {
    return cache.getConnectionMetadata(timeoutMs);
  }

  @Override
  public void forceRefresh() {
    cache.forceRefresh();
  }

  @Override
  public void refreshIfExpired() {
    cache.refreshIfExpired();
  }

  @Override
  public synchronized void close() {
    if (cache.isClosed()) {
      return;
    }

    cache.close();
    if (task != null) {
      task.cancel();
    }
    // If this was opened using a domain name, close remaining open sockets.
    synchronized (sockets) {
      for (Socket socket : sockets) {
        if (!socket.isClosed()) {
          try {
            socket.close();
          } catch (IOException e) {
            logger.debug("Exception closing socket after cache closed", e);
          }
        }
      }
    }
  }

  @Override
  public ConnectionConfig getConfig() {
    return cache.getConfig();
  }

  @Override
  public synchronized boolean isClosed() {
    return cache.isClosed();
  }

  synchronized void addSocket(SSLSocket socket) {
    // Only add the socket if this was configured using a domain name,
    // and therefore the background socket cleanup task is running.
    if (!Strings.isNullOrEmpty(cache.getConfig().getDomainName())) {
      sockets.add(socket);
    }
  }
}
