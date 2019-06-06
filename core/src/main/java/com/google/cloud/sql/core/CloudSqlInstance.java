package com.google.cloud.sql.core;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.SslCert;
import com.google.api.services.sqladmin.model.SslCertsCreateEphemeralRequest;
import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

  private final Object instanceDataGuard = new Object();
  private volatile ListenableFuture<InstanceData> currentInstanceData;
  private volatile ListenableFuture<ListenableFuture<InstanceData>> nextInstanceData;
  // Limit forced refreshes to 1 every minute.
  private RateLimiter forcedRenewRateLimiter = RateLimiter.create(1.0 / 60.0);

  /**
   * Initializes a new Cloud SQL instance based on the given connection name.
   *
   * @param connectionName instance connection name in the format "PROJECT_ID:REGION_ID:INSTANCE_ID"
   * @param apiClient Cloud SQL Admin API client for interacting with the Cloud SQL instance
   * @param executor executor used to schedule asynchronous tasks
   * @param keyPair public/private key pair used to authenticate connections
   */
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
    synchronized (instanceDataGuard) {
      this.currentInstanceData = this.performRefresh();
    }
  }

  /**
   * Returns the current data related to the instance from {@link #performRefresh()}. May block if
   * no valid data is currently available.
   */
  private InstanceData getInstanceData() {
    try {
      // TODO(kvg): Let exceptions up to here before adding context
      return Uninterruptibles.getUninterruptibly(currentInstanceData);
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      Throwables.throwIfUnchecked(cause);
      throw new RuntimeException(cause);
    }
  }

  /**
   * Returns an unconnected {@link SSLSocket} using the SSLContext associated with the instance. May
   * block until required instance data is available.
   */
  SSLSocket createSslSocket() throws IOException {
    return (SSLSocket) getInstanceData().getSslContext().getSocketFactory().createSocket();
  }

  /**
   * Returns the first IP address for the instance, in order of the preference supplied by
   * preferredTypes.
   *
   * @param preferredTypes Preferred instance IP types to use. Valid IP types include "Public" and
   *     "Private".
   * @return returns a string representing the IP address for the instance
   * @throws IllegalArgumentException If the instance has no IP addresses matching the provided
   *     preferences.
   */
  String getPreferredIp(List<String> preferredTypes) {
    Map<String, String> ipAddrs = getInstanceData().getIpAddrs();
    for (String ipType : preferredTypes) {
      String preferredIp = ipAddrs.get(ipType);
      if (preferredIp != null) {
        return preferredIp;
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "[%s] Cloud SQL instance  does not have any IP addresses matching preferences (%s)",
            connectionName, String.join(", ", preferredTypes)));
  }

  /**
   * Attempts to force a new refresh of the instance data. May fail if called too frequently or if a
   * new refresh is already in progress. If successful, other methods may block until refresh has
   * been completed.
   *
   * @return {@code true} if successfully scheduled, or {@code false} if it is called too
   *     frequently.
   */
  boolean forceRefresh() {
    if (!forcedRenewRateLimiter.tryAcquire()) {
      return false; // Forced refreshing too often
    }
    synchronized (instanceDataGuard) {
      // If a scheduled Refresh already exists, cancel if if not immediately imminent
      if (nextInstanceData == null || nextInstanceData.cancel(false)) {
        // If canceled successfully, schedule a replacement immediately
        nextInstanceData = null;
        scheduleRefresh(0, TimeUnit.MINUTES);
      }
      // Force currentInstanceData to block until the next refresh is complete
      SettableFuture blockingCurrent = new SettableFuture<>();
      nextInstanceData.addListener(
          () -> {
            ListenableFuture<InstanceData> scheduledRefresh = extractNestedFuture(nextInstanceData);
            // TODO(kvg); Unchecked call?
            blockingCurrent.setFuture(scheduledRefresh);
          },
          executor);
      currentInstanceData = blockingCurrent;
      return true;
    }
  }

  /**
   * Schedules an asynchronous refresh of instance data unless one is already in progress.
   *
   * @return a future that completes when the scheduled refresh begins, and returns the actual
   *     refresh operation
   */
  private ListenableFuture<ListenableFuture<InstanceData>> scheduleRefresh(
      long delay, TimeUnit timeUnit) {
    synchronized (instanceDataGuard) {
      if (nextInstanceData == null) { // If no refresh is already scheduled
        nextInstanceData = executor.schedule(this::performRefresh, delay, timeUnit);
      }
      return nextInstanceData;
    }
  }

  /**
   * Performs an update for the instance's internal state using the Cloud SQL Admin API. This
   * refreshes information about the Cloud SQL instance, as well as the SSLContext used to
   * authenticate connections.
   */
  private ListenableFuture<InstanceData> performRefresh() {
    ListenableFuture<Metadata> metadataFuture = executor.submit(this::fetchMetadata);
    ListenableFuture<Certificate> ephemeralCertificateFuture =
        executor.submit(this::fetchEphemeralCertificate);

    ListenableFuture<SSLContext> sslContextFuture =
        whenComplete(
            () ->
                createSslContext(
                    Futures.getDone(metadataFuture), Futures.getDone(ephemeralCertificateFuture)),
            metadataFuture,
            ephemeralCertificateFuture);

    ListenableFuture<InstanceData> refreshFuture =
        whenComplete(
            () ->
                new InstanceData(
                    Futures.getDone(metadataFuture), Futures.getDone(sslContextFuture)),
            metadataFuture,
            sslContextFuture);

    refreshFuture.addListener(
        () -> {
          // Once complete, replace current and schedule another before the cert expires.
          synchronized (instanceDataGuard) {
            currentInstanceData = refreshFuture;
            nextInstanceData = null;
            scheduleRefresh(55, TimeUnit.MINUTES);
          }
        },
        executor);

    return refreshFuture;
  }

  /**
   * Creates a new SSLContext based on the provided parameters. This SSLContext will be used to
   * provide new SSLSockets that are authorized to connect to a Cloud SQL instance.
   */
  private SSLContext createSslContext(Metadata metadata, Certificate ephemeralCertificate) {
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

      SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
      sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

      return sslContext;
    } catch (GeneralSecurityException | IOException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to create a SSLContext for the Cloud SQL instance.", connectionName),
          ex);
    }
  }

  /** Fetches the latest version of the instance's metadata using the Cloud SQL Admin API. */
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

    // Verify the instance has at least one IP type assigned that can be used to connect.
    if (instanceMetadata.getIpAddresses().isEmpty()) {
      throw new IllegalStateException(
          String.format(
              "[%s] Unable to connect to Cloud SQL instance: instance does not have an assigned "
                  + "IP address.",
              connectionName));
    }
    // Update the IP addresses and types need to connect with the instance.
    Map<String, String> ipAddrs = new HashMap<>();
    for (IpMapping addr : instanceMetadata.getIpAddresses()) {
      ipAddrs.put(addr.getType(), addr.getIpAddress());
    }

    // Update the Server CA certificate used to create the SSL connection with the instance.
    try {
      Certificate instanceCaCertificate =
          createCertificate(instanceMetadata.getServerCaCert().getCert());
      return new Metadata(ipAddrs, instanceCaCertificate);
    } catch (CertificateException ex) {
      throw new RuntimeException(
          String.format(
              "[%s] Unable to parse the server CA certificate for the Cloud SQL instance.",
              connectionName),
          ex);
    }
  }

  /**
   * Uses the Cloud SQL Admin API to create an ephemeral SSL certificate that is authenticated to
   * connect the Cloud SQL instance for up to 60 minutes.
   */
  private Certificate fetchEphemeralCertificate() {

    // Use the SQL Admin API to create a new ephemeral certificate.
    SslCertsCreateEphemeralRequest request =
        new SslCertsCreateEphemeralRequest().setPublicKey(generatePublicKeyCert());
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
      ephemeralCertificate = createCertificate(response.getCert());
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
   * Generates public key certificate for which the instance has the matching private key.
   *
   * @return PEM encoded public key certificate
   */
  private String generatePublicKeyCert() {
    // Format the public key into a PEM encoded Certificate.
    return "-----BEGIN RSA PUBLIC KEY-----\n"
        + BaseEncoding.base64().withSeparator("\n", 64).encode(keyPair.getPublic().getEncoded())
        + "\n"
        + "-----END RSA PUBLIC KEY-----\n";
  }

  // Schedules task to be executed once the provided futures are complete.
  private <T> ListenableFuture<T> whenComplete(Callable<T> task, ListenableFuture<?>... futures) {
    SettableFuture<T> taskFuture = SettableFuture.create();

    // Create a countDown for all Futures to complete.
    AtomicInteger countDown = new AtomicInteger(futures.length);

    // Trigger the task when all futures are complete.
    Runnable runWhenInputsAreComplete =
        () -> {
          if (countDown.decrementAndGet() == 0) {
            taskFuture.setFuture(executor.submit(task));
          }
        };
    for (ListenableFuture<?> future : futures) {
      future.addListener(runWhenInputsAreComplete, executor);
    }

    return taskFuture;
  }

  // Returns the inner future from a nested future, or else the exception it encountered.
  private static <T> ListenableFuture<T> extractNestedFuture(
      ListenableFuture<ListenableFuture<T>> future) {
    try {
      return Futures.getDone(future);
    } catch (ExecutionException ex) {
      return Futures.immediateFailedFuture(ex.getCause());
    }
  }

  // Creates a Certificate object from a provided string.
  private static Certificate createCertificate(String cert) throws CertificateException {
    byte[] certBytes = cert.getBytes(StandardCharsets.UTF_8);
    ByteArrayInputStream certStream = new ByteArrayInputStream(certBytes);
    return CertificateFactory.getInstance("X.509").generateCertificate(certStream);
  }

  /**
   * Checks for common errors that can occur when interacting with the Cloud SQL Admin API, and adds
   * additional context to help the user troubleshoot them.
   *
   * @param ex exception thrown by the Admin API request
   * @param fallbackDesc generic description used as a fallback if no additional information can be
   *     provided to the user
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
              connectionName, projectId, apiLink),
          ex);
    } else if ("notAuthorized".equals(reason)) {
      // This error occurs if the instance doesn't exist or the account isn't authorized
      // TODO(kvg): Add credential account name to error string.
      return new RuntimeException(
          String.format(
              "[%s] The Cloud SQL Instance does not exist or your account is not authorized to "
                  + "access it. Please verify the instance connection name and check the IAM "
                  + "permissions for project \"%s\" ",
              connectionName, projectId),
          ex);
    }
    // Fallback to the generic description
    return new RuntimeException(fallbackDesc, ex);
  }

  /** Represents the results of {@link #performRefresh()}. */
  private static class InstanceData {
    private final Metadata metadata;
    private final SSLContext sslContext;

    InstanceData(Metadata metadata, SSLContext sslContext) {
      this.metadata = metadata;
      this.sslContext = sslContext;
    }

    SSLContext getSslContext() {
      return sslContext;
    }

    Map<String, String> getIpAddrs() {
      return metadata.getIpAddrs();
    }
  }

  /** Represents the results of @link #fetchMetadata(). */
  private static class Metadata {
    private final Map<String, String> ipAddrs;
    private final Certificate instanceCaCertificate;

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
