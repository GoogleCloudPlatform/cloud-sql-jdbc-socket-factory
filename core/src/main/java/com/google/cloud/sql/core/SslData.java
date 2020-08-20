package com.google.cloud.sql.core;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * This class stores data that can be used to establish Cloud SQL SSL connection.
 */
public class SslData {

  private SSLContext sslContext;
  private KeyManagerFactory keyManagerFactory;
  private TrustManagerFactory trustManagerFactory;

  SslData(SSLContext sslContext, KeyManagerFactory keyManagerFactory,
      TrustManagerFactory trustManagerFactory) {
    this.sslContext = sslContext;
    this.keyManagerFactory = keyManagerFactory;
    this.trustManagerFactory = trustManagerFactory;
  }

  public SSLContext getSslContext() {
    return sslContext;
  }

  public void setSslContext(SSLContext sslContext) {
    this.sslContext = sslContext;
  }

  public KeyManagerFactory getKeyManagerFactory() {
    return keyManagerFactory;
  }

  public void setKeyManagerFactory(KeyManagerFactory keyManagerFactory) {
    this.keyManagerFactory = keyManagerFactory;
  }

  public TrustManagerFactory getTrustManagerFactory() {
    return trustManagerFactory;
  }

  public void setTrustManagerFactory(TrustManagerFactory trustManagerFactory) {
    this.trustManagerFactory = trustManagerFactory;
  }
}
