package com.google.cloud.sql.postgres;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.sql.core.BaseGrpcSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

public class GrpcSocketFactory extends javax.net.SocketFactory{

  private final String instanceName;

  public GrpcSocketFactory(String instanceName) {
    checkArgument(
        instanceName != null,
        "socketFactoryArg property not set. Please specify this property in the JDBC "
            + "URL or the connection Properties with the instance connection name in "
            + "form \"project:region:instance\"");
    this.instanceName = instanceName;
  }




  @Override
  public Socket createSocket() throws IOException {
    Properties props = new Properties();
    props.setProperty("cloudSqlInstance", instanceName);
    return new BaseGrpcSocketFactory().connect(props);
  }

  @Override
  public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
      throws IOException, UnknownHostException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress host, int port) throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
      throws IOException {
    throw new UnsupportedOperationException();
  }
}
