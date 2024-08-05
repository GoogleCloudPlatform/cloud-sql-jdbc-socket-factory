package com.google.cloud.sql;

public interface InstanceNameResolver {
  String resolve(String name);
}
