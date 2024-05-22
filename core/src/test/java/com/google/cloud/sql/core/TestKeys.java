/*
 * Copyright 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * Test keys for TLS tests. These keys are checked in to core/src/main/resources/certs.
 *
 * <p>!!! Do not manually update the certificate files directly !!!
 *
 * <p>core/src/main/resources/update_test_certs.sh generates the certificates used in the tests. To
 * maintain repeatable, maintainable certificate tests, please edit this script if you need to
 * change the configuration of these test certificates.
 */
public class TestKeys {

  static final byte[] SIGNING_CA_PRIVATE_KEY;
  static final byte[] CLIENT_PUBLIC_KEY;
  static final byte[] CLIENT_PRIVATE_KEY;

  static final String SIGNING_CA_CERT;
  static final String SERVER_CA_CERT;
  static final String SERVER_CERT;
  static final byte[] SERVER_CERT_PRIVATE_KEY;

  private static final KeyFactory KEY_FACTORY;
  private static final CertificateFactory CERTIFICATE_FACTORY;

  private static String loadResourceAsString(String path) {
    try (InputStream is = TestKeys.class.getResourceAsStream(path)) {
      if (is == null) {
        throw new RuntimeException("Test resource missing: " + path);
      }
      try (InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
          BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
      }
    } catch (IOException e) {
      throw new RuntimeException("Exception loading test resource: " + path, e);
    }
  }

  private static String loadCert(String path) {
    return loadResourceAsString(path);
  }

  private static byte[] loadKey(String path) {
    String value = loadResourceAsString(path);
    // For keys, we need to remove the delimiter text lines that are added by OpenSSL
    // like "-----BEGIN CERTIFICATE-----"
    // and  "-----END CERTIFICATE-----"
    // so that the string is parsed properly by the Java KeyStore.
    return decodeBase64StripWhitespace(
        Arrays.stream(value.split("\n"))
            .filter((l) -> !l.startsWith("-----"))
            .collect(Collectors.joining("\n")));
  }

  static byte[] decodeBase64StripWhitespace(String b64) {
    return Base64.getDecoder().decode(b64.replaceAll("\\s", ""));
  }

  public static PrivateKey getSigningCaKey()
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(TestKeys.SIGNING_CA_PRIVATE_KEY);
    return keyFactory.generatePrivate(keySpec);
  }

  public static X509Certificate getSigningCaCert() throws CertificateException {
    return (X509Certificate)
        CERTIFICATE_FACTORY.generateCertificate(
            new ByteArrayInputStream(TestKeys.SIGNING_CA_CERT.getBytes(UTF_8)));
  }

  public static PrivateKey getClientPrivateKey() throws InvalidKeySpecException {
    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(TestKeys.CLIENT_PRIVATE_KEY);
    return KEY_FACTORY.generatePrivate(privateKeySpec);
  }

  public static PublicKey getClientPublicKey() throws InvalidKeySpecException {

    X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(TestKeys.CLIENT_PUBLIC_KEY);
    return KEY_FACTORY.generatePublic(publicKeySpec);
  }

  public static X509Certificate getServerCert() throws CertificateException {
    return (X509Certificate)
        CERTIFICATE_FACTORY.generateCertificate(
            new ByteArrayInputStream(TestKeys.SERVER_CERT.getBytes(UTF_8)));
  }

  public static PrivateKey getServerPrivateKey() throws InvalidKeySpecException {
    PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(TestKeys.SERVER_CERT_PRIVATE_KEY);
    return KEY_FACTORY.generatePrivate(privateKeySpec);
  }

  static {
    SERVER_CA_CERT = loadCert("/certs/server-ca.cer");

    SERVER_CERT = loadCert("/certs/server.cer");
    SERVER_CERT_PRIVATE_KEY = loadKey("/certs/server.key");

    SIGNING_CA_CERT = loadCert("/certs/signing-ca.cer");
    SIGNING_CA_PRIVATE_KEY = loadKey("/certs/signing-ca.key");
    CLIENT_PRIVATE_KEY = loadKey("/certs/client.key");
    CLIENT_PUBLIC_KEY = loadKey("/certs/client-pub.key");

    try {
      KEY_FACTORY = KeyFactory.getInstance("RSA");
      CERTIFICATE_FACTORY = CertificateFactory.getInstance("X.509");
    } catch (NoSuchAlgorithmException | CertificateException e) {
      throw new RuntimeException("Can't initialize certificate factories: ", e);
    }
  }
}
