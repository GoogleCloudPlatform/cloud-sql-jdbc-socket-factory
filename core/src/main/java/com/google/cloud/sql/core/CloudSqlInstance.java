package com.google.cloud.sql.core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.SslCertsCreateEphemeralRequest;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.RateLimiter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

/**
 * This class manages information on and creates connections to a Cloud SQL instance using the Cloud
 * SQL Admin API. The operations to retrieve information with the API are largely done
 * asynchronously, and this class should be considered threadsafe.
 */
class CloudSqlInstance {
  private final ListeningScheduledExecutorService executor;
  private final SQLAdmin apiClient;

  private final String connectionName;
  private final String projectId;
  private final String regionId;
  private final String instanceId;
  private final KeyPair keyPair;

  private final Object metadataGuard = new Object();
  private ListenableFuture<Metadata> currentMetadata;
  private ListenableFuture<Metadata> nextMetadata;

  private final Object ephemeralCertGuard = new Object();
  private ListenableFuture<Certificate> currentEphemeralCert;
  private ListenableFuture<Certificate> nextEphemeralCert;

  private final Object sslContextGuard = new Object();
  private ListenableFuture<SSLContext> currentSslContext;
  private ListenableFuture<SSLContext> nextSslContext;
  private RateLimiter forcedRenewRateLimiter = RateLimiter.create(1.0 / 60.0);

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param connectionName Instance connection name; in the format
   *     "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param apiClient Cloud SQL Admin API client for interacting with the Cloud SQL instance.
   * @param executor Executor used to schedule asynchronous tasks.
   * @param keyPair Public/Private key pair used to authenticate connections.
   */
  // TODO(kvg): ListeningScheduledExecutorService is currently Beta
  CloudSqlInstance(
      String connectionName,
      SQLAdmin apiClient,
      ListeningScheduledExecutorService executor,
      KeyPair keyPair) {
    String[] connFields = connectionName.split(":");
    if (connFields.length != 3) {
      throw new IllegalArgumentException(
          "[%s] Cloud SQL connection name is invalid, expected string in the form of "
              + "\"<PROJECT_ID>:<REGION_ID>:<INSTANCE_ID>\".");
    }
    this.connectionName = connectionName;
    this.projectId = connFields[0];
    this.regionId = connFields[1];
    this.instanceId = connFields[2];

    this.apiClient = apiClient;
    this.executor = executor;
    this.keyPair = keyPair;

    // Kick off initial async jobs
    synchronized (metadataGuard) {
      this.currentMetadata = this.scheduleMetadataUpdate();
      synchronized (ephemeralCertGuard) {
        this.currentEphemeralCert = this.scheduleEphemeralCertificateUpdate();
        synchronized (sslContextGuard) {
          this.currentSslContext =
              this.scheduleSslContextUpdate(this.currentMetadata, this.currentEphemeralCert);
        }
      }
    }
  }

  /**
   * Returns an unconnected {@Link SSLSocket} using the SSLContext associated with the instance. May
   * block until an SSLContext is successfully available.
   */
  SSLSocket createSslSocket() throws IOException {
    try {
      return (SSLSocket) this.currentSslContext.get().getSocketFactory().createSocket();
    } catch (InterruptedException | ExecutionException ex) {
      // TODO(kvg): Clean error handling up
      throw new RuntimeException(ex);
    }
  }

  /** Returns a String that represents first IP address that matches the given preferredTypes. */
  String getPreferredIp(List<String> preferredTypes)
      throws ExecutionException, InterruptedException {
    String preferredIp = null;
    Map<String, String> ipAddrs = this.currentMetadata.get().getIpAddrs();
    for (String ipType : preferredTypes) {
      preferredIp = ipAddrs.get(ipType);
      if (preferredIp != null) {
        break;
      }
    }
    if (preferredIp == null) {
      throw new IllegalArgumentException(
          String.format(
              "[%s] Cloud SQL instance  does not have any IP addresses matching preference: [ %s ]",
              connectionName, String.join(", ", preferredTypes)));
    }
    return preferredIp;
  }

  /**
   * Invalidates the current {@link SSLContext} and schedules a new one to be created. This will
   * cause other methods on this object to block until the SSLContext is available.
   *
   * <p>This method is rate limited, and may fail if called too often.
   *
   * @return Returns true if successful scheduled, else false.
   */
  boolean immediateSslContextUpdate() {
    if (!forcedRenewRateLimiter.tryAcquire()) {
      return false; // Forced refreshing too often
    }
    synchronized (sslContextGuard) {
      if (nextSslContext != null) { // If a new SSLContext has already been scheduled
        // Attempt to cancel the scheduled task
        if (!nextSslContext.cancel(false)) {
          // If the task is already running, an update is already imminent. Replace current with the
          // next result so that future operations block until it completes.
          this.currentSslContext = this.nextSslContext;
          return true;
        }
        nextSslContext = null;
      }
      // Schedule a new update immediately, and replace with current to block future operations that
      // rely on the SSLContext.
      this.currentSslContext = this.scheduleSslContextUpdate(0, TimeUnit.MINUTES);
      return true;
    }
  }

  /**
   * Schedules {@link this.scheduleSSLContextUpdate} to run asynchronously. A future representing
   * the completion of the SSLContext will be returned. If a new SSLContext has already been
   * scheduled to be created, the existing {@link ListenableFuture} will be returned instead.
   */
  private ListenableFuture<SSLContext> scheduleSslContextUpdate(
      ListenableFuture<Metadata> futureMetadata, ListenableFuture<Certificate> futureCertificate) {
    synchronized (sslContextGuard) {
      if (this.nextSslContext == null) { // If no metadata update is already scheduled
        ListenableFuture<SSLContext> future =
            executor.submit(() -> this.performSSlContextUpdate(futureMetadata, futureCertificate));
        // Once the "next" future is complete, replace "current" and schedule replacement
        future.addListener(
            () -> {
              synchronized (sslContextGuard) {
                this.currentSslContext = this.nextSslContext;
                this.nextSslContext = this.scheduleSslContextUpdate(55, TimeUnit.MINUTES);
              }
            },
            executor);
      }
      return this.nextSslContext;
    }
  }

  /**
   * Schedules {@link this.scheduleSSLContextUpdate} to run asynchronously. A future representing
   * the completion of the SSLContext will be returned. If a new SSLContext has already been
   * scheduled to be created, the existing {@link ListenableFuture} will be returned instead.
   */
  private ListenableFuture<SSLContext> scheduleSslContextUpdate(long delay, TimeUnit timeUnit) {
    synchronized (sslContextGuard) {
      if (this.nextSslContext == null) { // If no metadata update is already scheduled
        ListenableFuture<SSLContext> future =
            executor.schedule(() -> this.performSSlContextUpdate(), delay, timeUnit);
        // Once the "next" future is complete, replace "current" and schedule replacement
        future.addListener(
            () -> {
              synchronized (sslContextGuard) {
                this.currentSslContext = this.nextSslContext;
                this.nextSslContext = this.scheduleSslContextUpdate(55, TimeUnit.MINUTES);
              }
            },
            executor);
      }
      return this.nextSslContext;
    }
  }

  /**
   * Creates a new {@link SSLContext} that can provide authenticated connections to the instance.
   */
  private SSLContext performSSlContextUpdate() {
    ListenableFuture<Metadata> futureMetadata = this.scheduleMetadataUpdate();
    ListenableFuture<Certificate> futureCertificate = this.scheduleEphemeralCertificateUpdate();
    return this.performSSlContextUpdate(futureMetadata, futureCertificate);
  }

  /**
   * Schedules updates for both the instance's metadata and the ephemeral certificate, and uses the
   * results to create a new {@link SSLContext} used to create connections to the server.
   */
  private SSLContext performSSlContextUpdate(
      ListenableFuture<Metadata> futureMetadata, ListenableFuture<Certificate> futureCertificate) {
    // Block until the requirements are ready
    Metadata instanceMetadata;
    Certificate ephemeralCertificate;
    try {
      instanceMetadata = futureMetadata.get();
      ephemeralCertificate = futureCertificate.get();
    } catch (Exception ex) {
      // TODO(kvg): Clean up exception handling
      throw new RuntimeException(ex.getCause());
    }
    return this.createSslContext(instanceMetadata, ephemeralCertificate);
  }

  /**
   * Returns a new SSLContext based on the provided parameters. This SSLContext will be used to
   * provide new SSLSockets that are authorized to connect to a Cloud SQL instance.
   */
  private SSLContext createSslContext(Metadata metadata, Certificate ephemeralCertificate) {
    SSLContext sslContext;
    try {
      KeyStore authKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      authKeyStore.load(null, null);
      KeyStore.PrivateKeyEntry privateKey =
          new PrivateKeyEntry(keyPair.getPrivate(), new Certificate[] {ephemeralCertificate});
      authKeyStore.setEntry("ephemeral", privateKey, null);
      KeyManagerFactory kmf =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(authKeyStore, null);

      KeyStore trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustedKeyStore.load(null, null);
      trustedKeyStore.setCertificateEntry("instance", metadata.getInstanceCaCertificate());
      TrustManagerFactory tmf = TrustManagerFactory.getInstance("X.509");
      tmf.init(trustedKeyStore);

      sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

    } catch (GeneralSecurityException | IOException ex) {
      // TODO(kvg): Clean up exception handling
      throw new RuntimeException(
          String.format(
              "[%s] Unable to create a SSLContext for the Cloud SQL instance.", connectionName),
          ex);
    }
    return sslContext;
  }

  /**
   * Schedules {@link this.fetchMetadata} to run asynchronously, and update the instance's internal
   * state when complete. If an update is already scheduled, it will return a {@link
   * ListenableFuture} representing that result instead.
   */
  private ListenableFuture<Metadata> scheduleMetadataUpdate() {
    synchronized (metadataGuard) {
      if (this.nextMetadata == null) { // If no metadata update is already scheduled
        ListenableFuture<Metadata> future = executor.submit(this::fetchMetadata);
        // Once the "next" future is complete, replace "current" and free up "next"
        future.addListener(
            () -> {
              synchronized (metadataGuard) {
                this.currentMetadata = this.nextMetadata;
                this.nextMetadata = null;
              }
            },
            executor);
        this.nextMetadata = future;
      }
      return this.nextMetadata;
    }
  }

  private Metadata fetchMetadata() {
    DatabaseInstance instanceMetadata;
    try {
      instanceMetadata = apiClient.instances().get(projectId, instanceId).execute();
    } catch (IOException ex) {
      throw addExceptionContext(
          ex,
          String.format("[%s] Failed to update metadata for Cloud SQL instance.", connectionName));
    }

    // Validate the instance will support the authenticated connection.
    if (!instanceMetadata.getRegion().equals(regionId)) {
      throw new IllegalArgumentException(
          String.format(
              "[%s] The region specified for the Cloud SQL instance is"
                  + " incorrect. Please verify the instance connection name.",
              connectionName));
    }
    if (!instanceMetadata.getBackendType().equals("SECOND_GEN")) {
      throw new IllegalArgumentException(
          String.format(
              "[%s] Connections to Cloud SQL instance not supported - not a Second Generation "
                  + "instance.",
              connectionName));
    }

    // Update the IP addresses and types need to connect with the instance.
    Map<String, String> ipAddrs = new HashMap<>();
    for (IpMapping addr : instanceMetadata.getIpAddresses()) {
      ipAddrs.put(addr.getType(), addr.getIpAddress());
    }
    if (ipAddrs.isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "[%s] Unable to connect to Cloud SQL instance: instance does not have an assigned "
                  + "IP address.",
              connectionName));
    }

    // Update the Server CA certificate used to create the SSL connection with the instance.
    Certificate instanceCaCertificate;
    try {
      byte[] certBytes =
          instanceMetadata.getServerCaCert().getCert().getBytes(StandardCharsets.UTF_8);
      instanceCaCertificate =
          CertificateFactory.getInstance("X.509")
              .generateCertificate(new ByteArrayInputStream(certBytes));
    } catch (CertificateException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to parse the server CA certificate for the Cloud SQL instance.",
              connectionName),
          ex);
    }

    return new Metadata(ipAddrs, instanceCaCertificate);
  }

  /**
   * Schedules {@link this.fetchEphemeralCertificate} to run asynchronously, and update instance's
   * internal state when complete. If an update is already scheduled, it will return a {@link
   * ListenableFuture} representing that result instead.
   */
  private ListenableFuture<Certificate> scheduleEphemeralCertificateUpdate() {
    synchronized (ephemeralCertGuard) {
      if (this.nextEphemeralCert == null) { // If no certificate update is already scheduled
        ListenableFuture<Certificate> future = executor.submit(this::fetchEphemeralCertificate);
        // Once the "next" future is complete, replace "current" and free up "next"
        future.addListener(
            () -> {
              synchronized (ephemeralCertGuard) {
                this.currentEphemeralCert = this.nextEphemeralCert;
                this.nextEphemeralCert = null;
              }
            },
            executor);
        this.nextEphemeralCert = future;
      }
      return this.nextEphemeralCert;
    }
  }

  /**
   * Uses the Cloud SQL Admin API to create an ephemeral SSL certificate that is authenticated to
   * connect to a Cloud SQL instance, and is good for one hour.
   */
  private Certificate fetchEphemeralCertificate() {
    // Format the public key into a PEM encoded Certificate.
    StringBuilder publicKeyPemBuilder = new StringBuilder();
    publicKeyPemBuilder.append("-----BEGIN RSA PUBLIC KEY-----\n");
    publicKeyPemBuilder.append(
        Base64.getEncoder()
            .encodeToString(keyPair.getPublic().getEncoded())
            .replaceAll("(.{64})", "$1\n"));
    publicKeyPemBuilder.append("\n");
    publicKeyPemBuilder.append("-----END RSA PUBLIC KEY-----\n");

    // Use the SQL Admin API to create a new ephemeral certificate.
    SslCertsCreateEphemeralRequest request =
        new SslCertsCreateEphemeralRequest().setPublicKey(publicKeyPemBuilder.toString());
    SslCert response;
    try {
      response = apiClient.sslCerts().createEphemeral(projectId, instanceId, request).execute();
    } catch (IOException ex) {
      throw addExceptionContext(
          ex,
          String.format(
              "[%s] Failed to create ephemeral certificate for the Cloud SQL instance.",
              connectionName));
    }

    // Parse the certificate from the response.
    Certificate ephemeralCertificate;
    try {
      byte[] certBytes = response.getCert().getBytes(StandardCharsets.UTF_8);
      ephemeralCertificate =
          CertificateFactory.getInstance("X.509")
              .generateCertificate(new ByteArrayInputStream(certBytes));
    } catch (CertificateException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to parse the ephemeral certificate for the Cloud SQL instance.",
              connectionName),
          ex);
    }
    return ephemeralCertificate;
  }

  /**
   * Checks for common errors that can occur when interacting with the Cloud SQL Admin API, and adds
   * additional context to help the user troubleshoot them.
   *
   * @param ex The exception thrown by the Admin API request.
   * @param fallbackDesc Generic description uses as a fall back if no additional information can be
   *     provided to the user.
   */
  private RuntimeException addExceptionContext(IOException ex, String fallbackDesc) {
    // Verify we are able to extract a reason from an exception, or fallback to a generic desc
    GoogleJsonResponseException gjrEx =
        ex instanceof GoogleJsonResponseException ? (GoogleJsonResponseException) ex : null;
    if (gjrEx == null
        || gjrEx.getDetails() == null
        || gjrEx.getDetails().getErrors() == null
        || gjrEx.getDetails().getErrors().isEmpty()) {
      return new RuntimeException(fallbackDesc, ex);
    }
    // Check for commonly occurring user errors and add additional context
    String reason = gjrEx.getDetails().getErrors().get(0).getReason();
    if ("accessNotConfigured".equals(reason)) {
      // This error occurs when the project doesn't have the "Cloud SQL Admin API" enabled
      String apiLink =
          "https://console.cloud.google.com/apis/api/sqladmin/overview?project=" + projectId;
      return new RuntimeException(
          String.format(
              "[%s] The Google Cloud SQL Admin API is not enabled for the project \"%s\". Please "
                  + "use the Google Developers Console to enable it: %s",
              connectionName, projectId, apiLink));
    } else if ("notAuthorized".equals(reason)) {
      // This error occurs if the instance doesn't exist or the account isn't authorized
      // TODO(kvg): Add credential account name to error string.
      return new RuntimeException(
          String.format(
              "[%s] The Cloud SQL Instance does not exist or your account is not authorized to "
                  + "access it. Please verify the instance connection name and check the IAM "
                  + "permissions for project \"%s\" ",
              connectionName, projectId));
    }
    // Fallback to the generic description
    return new RuntimeException(fallbackDesc, ex);
  }

  private class Metadata {
    private Map<String, String> ipAddrs;
    private Certificate instanceCaCertificate;

    Metadata(Map<String, String> ipAddrs, Certificate instanceCaCertificate) {
      this.ipAddrs = ipAddrs;
      this.instanceCaCertificate = instanceCaCertificate;
    }

    Map<String, String> getIpAddrs() {
      return ipAddrs;
    }

    Certificate getInstanceCaCertificate() {
      return instanceCaCertificate;
    }
  }
}
