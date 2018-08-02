package com.google.cloud.sql.core;

import io.netty.handler.ssl.util.SimpleTrustManagerFactory;
import io.netty.util.internal.EmptyArrays;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public final class CustomCertificateVerifier extends SimpleTrustManagerFactory {

  private final TrustManager tm;

  public CustomCertificateVerifier(X509Certificate signingCA) {
    tm = new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String s)
          throws CertificateException {
        throw new CertificateException("This verifier is not made to check client certificates");
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String s)
          throws CertificateException {

        for (X509Certificate cert : chain) {
          try {
            cert.verify(signingCA.getPublicKey());
          } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | NoSuchProviderException e) {
            e.printStackTrace();
            throw new CertificateException("Failed to verify cert", e);
          }
        }
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return EmptyArrays.EMPTY_X509_CERTIFICATES;
      }
    };
  }


  @Override
  protected void engineInit(KeyStore keyStore) {
  }

  @Override
  protected void engineInit(ManagerFactoryParameters managerFactoryParameters) {
  }

  @Override
  protected TrustManager[] engineGetTrustManagers() {
    return new TrustManager[]{tm};
  }
}
