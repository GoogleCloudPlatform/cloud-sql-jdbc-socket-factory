package com.google.cloud.sql.mysql;

import com.google.cloud.sql.core.BaseGrpcSocketFactory;
import com.mysql.cj.protocol.ServerSession;
import com.mysql.cj.protocol.SocketConnection;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

public class GrpcSocketFactory implements com.mysql.cj.protocol.SocketFactory {


  private Socket socket;


  @Override
  public Socket connect(String host, int portNumber, Properties props, int loginTimeout)
      throws IOException {
    return socket = new BaseGrpcSocketFactory().connect(props);
  }

  // Cloud SQL sockets always use TLS and the socket returned by connect above is already TLS-ready. It is fine
  // to implement these as no-ops.

  @Override
  public void beforeHandshake() {
  }

  @Override
  public Socket performTlsHandshake(SocketConnection socketConnection,
      ServerSession serverSession) {
    return socket;
  }

  @Override
  public void afterHandshake() {
  }


}
