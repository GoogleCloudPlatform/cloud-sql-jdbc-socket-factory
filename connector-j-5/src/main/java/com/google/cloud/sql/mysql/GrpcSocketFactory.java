package com.google.cloud.sql.mysql;

import com.google.cloud.sql.core.BaseGrpcSocketFactory;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import java.util.logging.Logger;

public class GrpcSocketFactory implements com.mysql.jdbc.SocketFactory {


  private Socket socket;
  private static final Logger logger = Logger.getLogger(GrpcSocketFactory.class.getName());


  @Override
  public Socket connect(String host, int portNumber, Properties props)
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
