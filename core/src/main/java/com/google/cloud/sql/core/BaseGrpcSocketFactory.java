package com.google.cloud.sql.core;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.cloud.sql.core.SslSocketFactory.CertificateCaching;
import com.google.cloud.sql.core.SslSocketFactory.InstanceSslInfo;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.handler.ssl.ApplicationProtocolConfig;
import io.netty.handler.ssl.ApplicationProtocolConfig.Protocol;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior;
import io.netty.handler.ssl.ApplicationProtocolNames;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import mygrpc.MyGrpcGrpc;
import mygrpc.MyGrpcGrpc.MyGrpcStub;
import mygrpc.Mygrpc.ClientMessage;
import mygrpc.Mygrpc.ServerMessage;

public class BaseGrpcSocketFactory {


  public Socket connect(Properties props)
      throws IOException {
    String instanceName = props.getProperty("cloudSqlInstance");
    checkArgument(
        instanceName != null,
        "cloudSqlInstance property not set. Please specify this property in the JDBC URL or "
            + "the connection Properties with value in form \"project:region:instance\"");

    SslSocketFactory inst = SslSocketFactory.getInstance();
    PrivateKey pk = inst.localKeyPair.getPrivate();
    InstanceSslInfo info = inst.getInstanceSslInfo(instanceName, CertificateCaching.USE_CACHE);

    String hostname = info.getInstanceIpAddress();
    int port = 3308;

    X509Certificate cert = info.getEphemeralCertificate();
    SslContextBuilder builder = GrpcSslContexts.forClient()
        .trustManager(new CustomCertificateVerifier((X509Certificate) inst.instanceCaCertificate))
        .keyManager(pk, cert)
        .clientAuth(ClientAuth.REQUIRE)
        .applicationProtocolConfig(new ApplicationProtocolConfig(
            Protocol.ALPN,
            // NO_ADVERTISE is currently the only mode supported by both OpenSsl and JDK providers.
            SelectorFailureBehavior.NO_ADVERTISE,
            // ACCEPT is currently the only mode supported by both OpenSsl and JDK providers.
            SelectedListenerFailureBehavior.ACCEPT,
            ApplicationProtocolNames.HTTP_2));

    MyGrpcSocketImpl impl = new MyGrpcSocketImpl(builder.build());
    MyGrpcSocket it = new MyGrpcSocket(impl);
    it.connect(new InetSocketAddress(hostname, port));
    return it;

  }

  private static class MyGrpcSocket extends Socket {

    MyGrpcSocket(MyGrpcSocketImpl impl) throws SocketException {
      super(impl);
    }
  }

  private static class MyGrpcSocketImpl extends SocketImpl {


    private final SslContext ctx;
    StreamObserver<ClientMessage> requestObserver;
    MyGrpcInputStream inputStream = new MyGrpcInputStream();
    MyGrpcOutputStream outputStream;

    MyGrpcSocketImpl(SslContext ctx) {
      this.ctx = ctx;
    }

    @Override
    protected void create(boolean stream) {

    }

    @Override
    protected void connect(String host, int port) {
      connect(NettyChannelBuilder.forAddress(host, port)
          .negotiationType(NegotiationType.TLS)
          .sslContext(ctx)
          .build());

    }

    @Override
    protected void connect(InetAddress address, int port) {
      connect(NettyChannelBuilder.forAddress(new InetSocketAddress(address, port))
          .negotiationType(NegotiationType.TLS)
          .sslContext(ctx)
          .build());
    }

    @Override
    protected void connect(SocketAddress address, int timeout) {
      connect(NettyChannelBuilder.forAddress(address)
          .negotiationType(NegotiationType.TLS)
          .sslContext(ctx)
          .build());
    }

    private void connect(ManagedChannel channel) {
      MyGrpcStub asyncStub =
          MyGrpcGrpc.newStub(channel);

      requestObserver =
          asyncStub.connection(new StreamObserver<ServerMessage>() {
            @Override
            public void onNext(ServerMessage value) {
              byte[] arr = value.getData().toByteArray();
              inputStream.write(arr);
            }

            @Override
            public void onError(Throwable t) {
              t.printStackTrace();
            }

            @Override
            public void onCompleted() {

            }
          });
      outputStream = new MyGrpcOutputStream(requestObserver);

    }

    @Override
    protected void bind(InetAddress host, int port) {
    }

    @Override
    protected void listen(int backlog) {
    }

    @Override
    protected void accept(SocketImpl s) {
    }

    @Override
    protected InputStream getInputStream() {
      return inputStream;
    }

    @Override
    protected OutputStream getOutputStream() {
      return new BufferedOutputStream(outputStream);
    }

    @Override
    protected int available() {
      return inputStream.q.size();
    }

    @Override
    protected void close() {

    }

    @Override
    protected void sendUrgentData(int data) {

    }

    @Override
    public void setOption(int optID, Object value) {

    }

    @Override
    public Object getOption(int optID) {
      return null;
    }
  }

  private static class MyGrpcInputStream extends InputStream {

    BlockingQueue<Byte> q = new LinkedBlockingDeque<>();

    void write(byte[] b) {
      for (byte i : b) {
        q.add(i);
      }
    }

    @Override
    public int read() {
      try {
        return q.take() & 0xff;
      } catch (InterruptedException e) {
        e.printStackTrace();
        return -1;
      }
    }
  }

  private static class MyGrpcOutputStream extends OutputStream {

    StreamObserver<ClientMessage> obs;


    public MyGrpcOutputStream(StreamObserver<ClientMessage> obs) {
      this.obs = obs;
    }

    @Override
    public void write(int b) {
      obs.onNext(
          ClientMessage.newBuilder().setData(ByteString.copyFrom(new byte[]{(byte) b})).build());

    }

    @Override
    public void write(byte[] buf, int start, int len) {
      obs.onNext(
          ClientMessage.newBuilder().setData(ByteString.copyFrom(buf, start, len)).build());
    }


  }


}
