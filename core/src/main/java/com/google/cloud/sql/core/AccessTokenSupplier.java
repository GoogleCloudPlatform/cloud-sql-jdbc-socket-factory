package com.google.cloud.sql.core;

import com.google.auth.oauth2.AccessToken;
import java.io.IOException;
import java.util.Optional;

@FunctionalInterface
public interface AccessTokenSupplier {
  Optional<AccessToken> get() throws IOException;
}
