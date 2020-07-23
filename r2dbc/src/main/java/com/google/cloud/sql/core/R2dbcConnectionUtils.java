package com.google.cloud.sql.core;

import static io.r2dbc.spi.ConnectionFactoryOptions.HOST;
import static io.r2dbc.spi.ConnectionFactoryOptions.PORT;

import io.netty.handler.ssl.SslContextBuilder;
import io.r2dbc.spi.ConnectionFactoryOptions.Builder;
import java.util.Properties;
import java.util.function.Function;

public class R2dbcConnectionUtils {
  static SocketOrCustomizer createConnection(Builder optionBuilder, Properties properties) {
    // precompute SSL Data to avoid blocking calls during the connect phase.
    CoreSocketFactory.getSslData(properties);

    String socket = CoreSocketFactory.getUnixSocketArg(properties);

    if (socket != null) {
      return new SocketOrCustomizer(socket);
    } else {
      String hostIp = CoreSocketFactory.getHostIp(properties);
      optionBuilder
          .option(HOST, hostIp)
          .option(PORT, CoreSocketFactory.getDefaultServerProxyPort());

      Function<SslContextBuilder, SslContextBuilder> customizer =
          sslContextBuilder -> {
            SslData sslData = CoreSocketFactory.getSslData(properties);

            sslContextBuilder.keyManager(sslData.getKeyManagerFactory());
            sslContextBuilder.trustManager(sslData.getTrustManagerFactory());
            sslContextBuilder.protocols("TLSv1.2");

            return sslContextBuilder;
          };
      return new SocketOrCustomizer(customizer);
    }
  }

  static class SocketOrCustomizer {
    private String socket;

    private Function<SslContextBuilder, SslContextBuilder> customizer;

    SocketOrCustomizer(String socket) {
      this.socket = socket;
    }

    SocketOrCustomizer(Function<SslContextBuilder, SslContextBuilder> customizer) {
      this.customizer = customizer;
    }

    String getSocket() {
      return socket;
    }

    Function<SslContextBuilder, SslContextBuilder> getCustomizer() {
      return customizer;
    }
  }
}
