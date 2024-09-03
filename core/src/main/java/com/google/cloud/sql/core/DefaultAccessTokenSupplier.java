/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.cloud.sql.core;

import static com.google.cloud.sql.core.RefreshCalculator.DEFAULT_REFRESH_BUFFER;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.sql.AuthType;
import com.google.cloud.sql.CredentialFactory;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultAccessTokenSupplier produces access tokens using credentials produced by this connector's
 * configured HttpRequestInitializer.
 */
class DefaultAccessTokenSupplier implements AccessTokenSupplier {

  private static final Logger logger = LoggerFactory.getLogger(DefaultAccessTokenSupplier.class);

  private static final String SQL_LOGIN_SCOPE = "https://www.googleapis.com/auth/sqlservice.login";

  private final CredentialFactory credentialFactory;

  static AccessTokenSupplier newInstance(AuthType authType, CredentialFactory tokenSourceFactory) {
    if (authType == AuthType.IAM) {
      return new DefaultAccessTokenSupplier(tokenSourceFactory);
    } else {
      return Optional::empty;
    }
  }

  /**
   * Creates an instance with configurable retry settings.
   *
   * @param tokenSource the token source
   */
  DefaultAccessTokenSupplier(CredentialFactory tokenSource) {
    this.credentialFactory = tokenSource;
  }

  /**
   * Returns an access token, refreshing if the credentials are expired or nearly expired.
   *
   * @return the AccessToken or Optional.empty() if there is no tokenSource.
   * @throws IOException if there is an error attempting to refresh the token
   * @throws IllegalStateException if the token cannot be used to refresh.
   */
  @Override
  public Optional<AccessToken> get() throws IOException {
    if (credentialFactory == null) {
      return Optional.empty();
    }

    RetryingCallable<Optional<AccessToken>> retries =
        new RetryingCallable<>(
            () -> {
              final GoogleCredentials credentials = credentialFactory.getCredentials();
              try {
                refreshIfRequired(credentials);
              } catch (IllegalStateException e) {
                throw new IllegalStateException("Error refreshing credentials " + credentials, e);
              }

              if (isAccessTokenEmpty(credentials)) {

                String errorMessage = "Access Token has length of zero";
                logger.debug(errorMessage);

                throw new IllegalStateException(errorMessage);
              }

              validateAccessTokenExpiration(credentials.getAccessToken());

              // Now, attempt to down-scope and refresh credentials
              GoogleCredentials downscoped = getDownscopedCredentials(credentials);

              // For some implementations of GoogleCredentials, particularly
              // ImpersonatedCredentials, down-scoped credentials are not
              // initialized with a token and need to be explicitly refreshed.
              if (isAccessTokenEmpty(downscoped)) {
                try {
                  downscoped.refresh();
                } catch (Exception e) {
                  throw new IllegalStateException(
                      "Error refreshing downscoped credentials " + credentials, e);
                }

                // After attempting to refresh once, if the downscoped credentials do not have
                // an access token after attempting to refresh, then throw an IllegalStateException
                if (isAccessTokenEmpty(downscoped)) {
                  String errorMessage = "Downscoped access token has length of zero";
                  logger.debug(errorMessage);

                  throw new IllegalStateException(
                      errorMessage
                          + ": "
                          + downscoped.getClass().getName()
                          + " from "
                          + credentials.getClass().getName());
                }
                validateAccessTokenExpiration(downscoped.getAccessToken());
              }

              return Optional.of(downscoped.getAccessToken());
            });

    try {
      return retries.call();
    } catch (IOException | RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Unexpected exception refreshing authentication token", e);
    }
  }

  private static boolean isAccessTokenEmpty(GoogleCredentials credentials) {
    return credentials.getAccessToken() == null
        || "".equals(credentials.getAccessToken().getTokenValue());
  }

  private void refreshIfRequired(GoogleCredentials credentials) throws IOException {
    // if the token does not exist, or if the token expires in less than 4 minutes, refresh it.
    if (credentials.getAccessToken() == null) {
      logger.debug("Current IAM AuthN Token is not set. Refreshing the token.");
      credentials.refresh();
    } else if (credentials.getAccessToken().getExpirationTime() != null
        && credentials
            .getAccessToken()
            .getExpirationTime()
            .toInstant()
            .minus(DEFAULT_REFRESH_BUFFER)
            .isBefore(Instant.now())) {
      logger.debug("Current IAM AuthN Token expires in less than 4 minutes. Refreshing the token.");
      credentials.refresh();
    }
  }

  private void validateAccessTokenExpiration(AccessToken accessToken) {
    Date expirationTimeDate = accessToken.getExpirationTime();

    if (expirationTimeDate != null) {
      Instant expirationTime = expirationTimeDate.toInstant();
      Instant now = Instant.now();

      // Is the token expired?
      if (expirationTime.isBefore(now) || expirationTime.equals(now)) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));
        String nowFormat = formatter.format(now);
        String expirationFormat = formatter.format(expirationTime);
        String errorMessage =
            "Access Token expiration time is in the past. Now = "
                + nowFormat
                + " Expiration = "
                + expirationFormat;
        logger.debug(errorMessage);
        throw new IllegalStateException(errorMessage);
      }
    }
  }

  /**
   * Extracts the expiration time from an AccessToken.
   *
   * @param token the token
   * @return the expiration time, if set.
   */
  static Optional<Instant> getTokenExpirationTime(Optional<AccessToken> token) {
    return token.flatMap((at) -> Optional.ofNullable(at.getExpirationTime())).map(Date::toInstant);
  }

  /**
   * Produces a credential that only has sqlservice.login scope.
   *
   * @param credentials the credentials
   * @return the downscoped credentials.
   */
  static GoogleCredentials getDownscopedCredentials(GoogleCredentials credentials) {
    return credentials.createScoped(SQL_LOGIN_SCOPE);
  }
}
