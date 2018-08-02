package com.google.cloud.sql.mysql;

import com.google.cloud.sql.core.BaseGrpcSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

public class GrpcSocketFactory implements com.mysql.cj.api.io.SocketFactory {


  private Socket socket;


  @Override
  public Socket connect(String host, int portNumber, Properties props, int loginTimeout)
      throws IOException {
    return socket = new BaseGrpcSocketFactory().connect(props);
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
