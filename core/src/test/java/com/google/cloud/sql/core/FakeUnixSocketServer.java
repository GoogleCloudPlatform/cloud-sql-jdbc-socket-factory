/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import jnr.enxio.channels.NativeSelectorProvider;
import jnr.unixsocket.UnixServerSocketChannel;
import jnr.unixsocket.UnixSocketAddress;
import jnr.unixsocket.UnixSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a simple echo unix socket server adapted from the JNR socket project.
 * https://github.com/jnr/jnr-unixsocket/blob/master/src/test/java/jnr/unixsocket/example/UnixServer.java
 */
class FakeUnixSocketServer {
  private static Logger log = LoggerFactory.getLogger(FakeUnixSocketServer.class);
  private final String path;
  private final UnixSocketAddress address;
  private UnixServerSocketChannel channel;
  private final AtomicBoolean closed = new AtomicBoolean();

  FakeUnixSocketServer(String path) {
    this.path = path;
    this.address = new UnixSocketAddress(path);
  }

  public synchronized void close() throws IOException {
    if (!this.closed.get()) {
      this.closed.set(true);
    }
    if (this.channel != null) {
      channel.close();
      this.channel = null;
    }
  }

  public synchronized void start() throws IOException {
    java.io.File path = new java.io.File(this.path);
    path.deleteOnExit();

    this.channel = UnixServerSocketChannel.open();
    channel.configureBlocking(false);
    channel.socket().bind(address);

    Thread t = new Thread(this::run);
    t.start();
  }

  public void run() {
    log.info("Starting fake unix socket server at path " + this.path);
    try {
      Selector sel = NativeSelectorProvider.getInstance().openSelector();
      channel.register(sel, SelectionKey.OP_ACCEPT, new ServerActor(channel));

      log.info("Waiting for connections path " + this.path);
      while (!this.closed.get()) {
        if (sel.select() > 0) {
          Set<SelectionKey> keys = sel.selectedKeys();
          Iterator<SelectionKey> iterator = keys.iterator();
          boolean running = false;
          boolean cancelled = false;
          while (iterator.hasNext()) {
            SelectionKey k = iterator.next();
            Actor a = (Actor) k.attachment();
            if (a.rxready()) {
              running = true;
            } else {
              k.cancel();
              cancelled = true;
            }
            iterator.remove();
          }
          if (!running && cancelled) {
            log.info("No Actors Running any more");
            break;
          }
        }
      }
    } catch (IOException ex) {
      log.info("IOException: waiting for sockets", ex);
    }
    log.info("UnixServer EXIT");
  }

  static interface Actor {
    public boolean rxready();
  }

  static final class ServerActor implements Actor {
    private final UnixServerSocketChannel channel;

    public ServerActor(UnixServerSocketChannel channel) {
      this.channel = channel;
    }

    @Override
    public final boolean rxready() {
      log.info("Handling unix socket connect.");
      try {
        UnixSocketChannel client = channel.accept();
        client.configureBlocking(false);
        ByteBuffer response = ByteBuffer.wrap("HELLO\n".getBytes("UTF-8"));
        client.write(response);
        log.info("Handling unix socket done.");
        return true;
      } catch (IOException ex) {
        return false;
      }
    }
  }
}
