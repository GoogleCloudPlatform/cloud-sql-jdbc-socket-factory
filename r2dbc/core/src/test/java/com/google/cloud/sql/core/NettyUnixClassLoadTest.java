package com.google.cloud.sql.core;

import org.junit.Test;

public class NettyUnixClassLoadTest {
  @Test
  public void testLoadNettyUnixSocketClass() throws Exception {
    Class.forName("io.netty.channel.unix.Errors");
  }

}
