/*
 * Copyright 2022 Google LLC
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

package com.google.cloud.sql.nativeimage;

import com.google.api.gax.nativeimage.NativeImageUtils;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/**
 * Registers GraalVM configuration for the Cloud SQL libraries.
 *
 * <p>This class is only used when this library is used in <a
 * href="https://www.graalvm.org/22.0/reference-manual/native-image/">GraalVM native image</a>
 * compilation.
 */
final class CloudSqlFeature implements Feature {

  private static final String CLOUD_SQL_SOCKET_CLASS =
      "com.google.cloud.sql.core.CoreSocketFactory";

  private static final String POSTGRES_SOCKET_CLASS = "com.google.cloud.sql.postgres.SocketFactory";

  private static final String MYSQL_SOCKET_CLASS = "com.google.cloud.sql.mysql.SocketFactory";

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    if (access.findClassByName(CLOUD_SQL_SOCKET_CLASS) == null) {
      return;
    }

    // The Core Cloud SQL Socket
    NativeImageUtils.registerClassForReflection(access, CLOUD_SQL_SOCKET_CLASS);

    // Register Hikari configs if used with Cloud SQL.
    if (access.findClassByName("com.zaxxer.hikari.HikariConfig") != null) {
      NativeImageUtils.registerClassForReflection(access, "com.zaxxer.hikari.HikariConfig");

      RuntimeReflection.register(
          access.findClassByName("[Lcom.zaxxer.hikari.util.ConcurrentBag$IConcurrentBagEntry;"));

      RuntimeReflection.register(access.findClassByName("[Ljava.sql.Statement;"));
    }

    // Register PostgreSQL driver config.
    if (access.findClassByName(POSTGRES_SOCKET_CLASS) != null) {
      NativeImageUtils.registerClassForReflection(
          access, "com.google.cloud.sql.postgres.SocketFactory");
      NativeImageUtils.registerClassForReflection(access, "org.postgresql.PGProperty");
    }

    // Register MySQL driver config.
    if (access.findClassByName(MYSQL_SOCKET_CLASS) != null) {
      NativeImageUtils.registerClassForReflection(access, MYSQL_SOCKET_CLASS);

      NativeImageUtils.registerClassForReflection(access, "com.mysql.jdbc.StandardSocketFactory");

      NativeImageUtils.registerConstructorsForReflection(
          access, "com.mysql.cj.conf.url.SingleConnectionUrl");

      // for mysql-j-5
      NativeImageUtils.registerConstructorsForReflection(
          access, "com.mysql.jdbc.log.StandardLogger");
      // for mysql-j-8
      NativeImageUtils.registerConstructorsForReflection(access, "com.mysql.cj.log.StandardLogger");

      Class<?> cjExceptionClass = access.findClassByName("com.mysql.cj.exceptions.CJException");
      if (cjExceptionClass != null) {
        // The CJException exists only jdbc/mysql-j-8 module's dependency
        access.registerSubtypeReachabilityHandler(
            (duringAccess, exceptionClass) ->
                NativeImageUtils.registerClassForReflection(duringAccess, exceptionClass.getName()),
            cjExceptionClass);
      }

      Class<?> mySqlNonTransientConnectionException =
          access.findClassByName(
              "com.mysql.jdbc.exceptions.jdbc4.MySQLNonTransientConnectionException");
      if (mySqlNonTransientConnectionException != null) {
        NativeImageUtils.registerConstructorsForReflection(
            access, mySqlNonTransientConnectionException.getName());
      }

      Class<?> mySqlNonTransientException =
          access.findClassByName("com.mysql.jdbc.exceptions.MySQLNonTransientException");
      if (mySqlNonTransientException != null) {
        NativeImageUtils.registerConstructorsForReflection(
            access, mySqlNonTransientException.getName());
      }

      // JDBC classes create socket connections which must be initialized at run time.
      RuntimeClassInitialization.initializeAtRunTime("com.mysql.cj.jdbc");
    }
    // This Netty class should be initialized at runtime
    // https://github.com/netty/netty/issues/11638
    Class<?> bouncyCastleAlpnSslUtils =
        access.findClassByName("io.netty.handler.ssl.BouncyCastleAlpnSslUtils");
    if (bouncyCastleAlpnSslUtils != null) {
      RuntimeClassInitialization.initializeAtRunTime(bouncyCastleAlpnSslUtils);
    }
    if (access.findClassByName("jnr.ffi.provider.FFIProvider") != null) {

      // Disabling this as ASM (runtime code generation library) can sometimes cause issues during
      // native image build.
      String asmEnabledPropertyKey = "jnr.ffi.asm.enabled";
      if (System.getProperty(asmEnabledPropertyKey) == null) {
        System.setProperty(asmEnabledPropertyKey, String.valueOf(false));
      }

      NativeImageUtils.registerForReflectiveInstantiation(access, "jnr.ffi.provider.jffi.Provider");
      RuntimeClassInitialization.initializeAtBuildTime("jnr.ffi.provider.jffi.NativeLibraryLoader");

      // StubLoader loads the native stub library and is only intended to be called reflectively.
      // Note that this configuration only covers linux x86_64 platform at the moment.
      NativeImageUtils.registerClassForReflection(access, "com.kenai.jffi.internal.StubLoader");
      NativeImageUtils.registerClassForReflection(access, "com.kenai.jffi.Version");

      NativeImageUtils.registerClassForReflection(
          access, "jnr.ffi.provider.jffi.platform.x86_64.linux.TypeAliases");

      // Scan the jnr.constants.* packages and register all for reflection
      registerPackageForReflection(access, "jnr.constants");
    }
  }

  /** Registers all the classes under the specified package for reflection. */
  public static void registerPackageForReflection(FeatureAccess access, String packageName) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

    try {
      String path = packageName.replace('.', '/');

      Enumeration<URL> resources = classLoader.getResources(path);
      while (resources.hasMoreElements()) {
        URL url = resources.nextElement();

        URLConnection connection = url.openConnection();
        if (connection instanceof JarURLConnection) {
          List<String> classes = findClassesInJar((JarURLConnection) connection, packageName);
          for (String className : classes) {
            NativeImageUtils.registerClassHierarchyForReflection(access, className);
          }
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to load classes under package name.", e);
    }
  }

  private static List<String> findClassesInJar(JarURLConnection urlConnection, String packageName)
      throws IOException {

    List<String> result = new ArrayList<>();

    final JarFile jarFile = urlConnection.getJarFile();
    final Enumeration<JarEntry> entries = jarFile.entries();

    while (entries.hasMoreElements()) {
      JarEntry entry = entries.nextElement();
      String entryName = entry.getName();

      if (entryName.endsWith(".class")) {
        String javaClassName = entryName.replace(".class", "").replace('/', '.');

        if (javaClassName.startsWith(packageName)) {
          result.add(javaClassName);
        }
      }
    }

    return result;
  }
}
