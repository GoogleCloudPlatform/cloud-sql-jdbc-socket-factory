package com.google.cloud.sql.core;

import com.google.cloud.sql.AuthType;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import java.security.KeyPair;
import java.util.concurrent.ExecutionException;

interface InstanceDataSupplier {
  InstanceData getInstanceData(
      CloudSqlInstanceName instanceName,
      AccessTokenSupplier accessTokenSupplier,
      AuthType authType,
      ListeningScheduledExecutorService executor,
      ListenableFuture<KeyPair> keyPair)
      throws ExecutionException, InterruptedException;
}
