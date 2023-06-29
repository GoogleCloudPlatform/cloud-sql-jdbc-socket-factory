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

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * DefaultAccessTokenSupplier produces access tokens using credentials produced by this connector's
 * configured HttpRequestInitializer.
 */
class DefaultAccessTokenSupplier implements AccessTokenSupplier {
  private static final Logger logger = Logger.getLogger(DefaultAccessTokenSupplier.class.getName());

  private static final String SQL_LOGIN_SCOPE = "https://www.googleapis.com/auth/sqlservice.login";

  private final Optional<HttpRequestInitializer> tokenSource;
  private final int retryCount;
  private final Duration retryDuration;

  /**
   * Creates an instance with default retry settings.
   *
   * @param tokenSource the token source that produces auth tokens.
   */
  DefaultAccessTokenSupplier(Optional<HttpRequestInitializer> tokenSource) {
    this(tokenSource, 3, Duration.ofSeconds(3));
  }

  /**
   * Creates an instance with configurable retry settings.
   *
   * @param tokenSource the token source
   * @param retryCount the number of attempts to refresh.
   * @param retryDuration the duration to wait between attempts.
   */
  DefaultAccessTokenSupplier(
      Optional<HttpRequestInitializer> tokenSource, int retryCount, Duration retryDuration) {
    this.tokenSource = tokenSource;
    this.retryCount = retryCount;
    this.retryDuration = retryDuration;
  }

  /**
   * Parses credentials out of the HttpRequestInitializer.
   *
   * @return the GoogleCredentials.
   * @throws RuntimeException when the HttpRequestInitializer is unrecognized, or if it can't
   *     produce a GoogleCredentials instance.
   */
  private GoogleCredentials parseCredentials() {
    HttpRequestInitializer source = this.tokenSource.get();

    if (source instanceof HttpCredentialsAdapter) {
      HttpCredentialsAdapter adapter = (HttpCredentialsAdapter) source;
      Credentials c = adapter.getCredentials();
      if (c != null && c instanceof GoogleCredentials) {
        return (GoogleCredentials) c;
      }
      throw new RuntimeException(
          String.format(
              "Unable to connect via automatic IAM authentication: "
                  + "HttpCredentialsAdapter did not create valid credentials. %s, %s",
              source.getClass().getName(), c));
    }

    if (source instanceof Credential) {
      Credential credential = (Credential) source;
      AccessToken accessToken =
          new AccessToken(
              credential.getAccessToken(), getTokenExpirationTime(credential).orElse(null));

      return new GoogleCredentials(accessToken) {
        @Override
        public AccessToken refreshAccessToken() throws IOException {
          credential.refreshToken();

          return new AccessToken(
              credential.getAccessToken(), getTokenExpirationTime(credential).orElse(null));
        }
      };
    }
    throw new RuntimeException(
        String.format(
            "Unable to connect via automatic IAM authentication: "
                + "Unsupported credentials of type %s",
            source.getClass().getName()));
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
    if (tokenSource.isPresent()) {
      RetryingCallable<Optional<AccessToken>> retries =
          new RetryingCallable<>(
              () -> {
                final GoogleCredentials credentials;
                credentials = parseCredentials();
                try {
                  credentials.refreshIfExpired();
                } catch (IllegalStateException e) {
                  throw new IllegalStateException("Error refreshing credentials " + credentials, e);
                }
                if (credentials.getAccessToken() == null
                    || "".equals(credentials.getAccessToken().getTokenValue())) {

                  String errorMessage = "Access Token has length of zero";
                  logger.warning(errorMessage);

                  throw new IllegalStateException(errorMessage);
                }

                validateAccessTokenExpiration(credentials.getAccessToken());

                // Now, attempt to downscope the credentials and refresh again
                GoogleCredentials downscoped = getDownscopedCredentials(credentials);
                if (downscoped.getAccessToken() == null
                    || "".equals(downscoped.getAccessToken().getTokenValue())) {
                  try {
                    downscoped.refreshIfExpired();
                  } catch (Exception e) {
                    throw new IllegalStateException(
                        "Error refreshing downscoped credentials " + credentials, e);
                  }
                  if (downscoped.getAccessToken() == null
                      || "".equals(downscoped.getAccessToken().getTokenValue())) {
                    String errorMessage = "Downscoped access token has length of zero";
                    logger.warning(errorMessage);

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
              },
              this.retryCount,
              this.retryDuration);

      try {
        return retries.call();
      } catch (IOException e) {
        throw e;
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new RuntimeException("Unexpected exception refreshing authentication token", e);
      }
    }
    return Optional.empty();
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
        logger.warning(errorMessage);
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
  static Optional<Date> getTokenExpirationTime(Optional<AccessToken> token) {
    if (token.isPresent()) {
      return Optional.ofNullable(token.get().getExpirationTime());
    } else {
      return Optional.empty();
    }
  }

  /**
   * Extracts the expiration time from a Credential.
   *
   * @param credentials the token
   * @return the expiration time, if set.
   */
  private Optional<Date> getTokenExpirationTime(Credential credentials) {
    return Optional.ofNullable(credentials.getExpirationTimeMilliseconds()).map(Date::new);
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
