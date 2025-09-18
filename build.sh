#!/usr/bin/env bash

# Copyright 2025 Google LLC.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Set SCRIPT_DIR to the current directory of this file.
SCRIPT_DIR=$(cd -P "$(dirname "$0")" >/dev/null 2>&1 && pwd)
SCRIPT_FILE="${SCRIPT_DIR}/$(basename "$0")"

##
## Local Development
##
## These functions should be used to run the local development process
##

## clean - Cleans the build output
function clean() {
  mvn clean
}

## build - Builds the project without running tests.
function build() {
   mvn install -DskipTests=true
}

## test - Runs local unit tests.
function test() {
  if [[ "$(uname -s)" == "Darwin" ]]; then
    echo "macOS detected. Setting up IP aliases for tests."
    echo "You may be prompted for your password to run sudo."
    sudo ifconfig lo0 alias 127.0.0.2 up
    sudo ifconfig lo0 alias 127.0.0.3 up
  fi
  mvn -P coverage test
}

## e2e - Runs end-to-end integration tests.
function e2e() {
  if [[ ! -f .envrc ]] ; then
    write_e2e_env .envrc
  fi
  source .envrc
  JOB_TYPE=integration .github/scripts/run_tests.sh
}


## e2e - Runs end-to-end integration tests.
function e2e_graalvm() {
  if [[ ! -f .envrc ]] ; then
    write_e2e_env .envrc
  fi
  source .envrc
  .github/scripts/run_tests_graalvm_native.sh
}

## fix - Fixes java code format.
function fix() {
  mvn com.coveo:fmt-maven-plugin:format
}

## lint - runs the java lint
function lint() {
  mvn -P lint install -DskipTests=true
}


## deps - updates dependencies to the latest version
function deps() {
  mvn versions:use-latest-versions
  find . -name 'pom.xml.versionsBackup' -print0 | xargs -0 rm -f
}


# write_e2e_env - Loads secrets from the gcloud project and writes
#     them to target/e2e.env to run e2e tests.
function write_e2e_env(){
  # Set the default to .envrc file if no argument is passed
  outfile="${1:-.envrc}"
  secret_vars=(
    MYSQL_CONNECTION_NAME=MYSQL_CONNECTION_NAME
    MYSQL_USER=MYSQL_USER
    MYSQL_USER_IAM=MYSQL_USER_IAM_JAVA
    MYSQL_PASS=MYSQL_PASS
    MYSQL_DB=MYSQL_DB
    MYSQL_MCP_CONNECTION_NAME=MYSQL_MCP_CONNECTION_NAME
    MYSQL_MCP_PASS=MYSQL_MCP_PASS
    POSTGRES_CONNECTION_NAME=POSTGRES_CONNECTION_NAME
    POSTGRES_IAM_CONNECTION_NAME=POSTGRES_IAM_CONNECTION_NAME
    POSTGRES_USER=POSTGRES_USER
    POSTGRES_USER_IAM=POSTGRES_USER_IAM_JAVA
    POSTGRES_PASS=POSTGRES_PASS
    POSTGRES_DB=POSTGRES_DB
    POSTGRES_CAS_CONNECTION_NAME=POSTGRES_CAS_CONNECTION_NAME
    POSTGRES_CAS_PASS=POSTGRES_CAS_PASS
    POSTGRES_CUSTOMER_CAS_CONNECTION_NAME=POSTGRES_CUSTOMER_CAS_CONNECTION_NAME
    POSTGRES_CUSTOMER_CAS_PASS=POSTGRES_CUSTOMER_CAS_PASS
    POSTGRES_CUSTOMER_CAS_DOMAIN_NAME=POSTGRES_CUSTOMER_CAS_DOMAIN_NAME
    POSTGRES_CUSTOMER_CAS_INVALID_DOMAIN_NAME=POSTGRES_CUSTOMER_CAS_INVALID_DOMAIN_NAME
    POSTGRES_MCP_CONNECTION_NAME=POSTGRES_MCP_CONNECTION_NAME
    POSTGRES_MCP_PASS=POSTGRES_MCP_PASS
    SQLSERVER_CONNECTION_NAME=SQLSERVER_CONNECTION_NAME
    SQLSERVER_USER=SQLSERVER_USER
    SQLSERVER_PASS=SQLSERVER_PASS
    SQLSERVER_DB=SQLSERVER_DB
    IMPERSONATED_USER=IMPERSONATED_USER
    QUOTA_PROJECT=QUOTA_PROJECT
  )

  if [[ -z "$TEST_PROJECT" ]] ; then
    echo "Set TEST_PROJECT environment variable to the project containing"
    echo "the e2e test suite secrets."
    exit 1
  fi

  echo "Getting test secrets from $TEST_PROJECT into $outfile"
  {
  for env_name in "${secret_vars[@]}" ; do
    env_var_name="${env_name%%=*}"
    secret_name="${env_name##*=}"
    set -x
    val=$(gcloud secrets versions access latest --project "$TEST_PROJECT" --secret="$secret_name")
    echo "export $env_var_name='$val'"
  done
  } > "$outfile"

}

## help - prints the help details
##
function help() {
   # Note: This will print the comments beginning with ## above each function
   # in this file.

   echo "build.sh <command> <arguments>"
   echo
   echo "Commands to assist with local development and CI builds."
   echo
   echo "Commands:"
   echo
   grep -e '^##' "$SCRIPT_FILE" | sed -e 's/##/ /'
}

set -euo pipefail

# Check CLI Arguments
if [[ "$#" -lt 1 ]] ; then
  help
  exit 1
fi

cd "$SCRIPT_DIR"

"$@"

