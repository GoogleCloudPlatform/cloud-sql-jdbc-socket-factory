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


## e2e_graalvm - Runs end-to-end integration tests using graalvm
function e2e_graalvm() {
  graalvm_tools

  if [[ ! -f .envrc ]] ; then
    write_e2e_env .envrc
  fi
  source .envrc
  export JAVA_HOME=$PWD/.tools/graalvm-ce-jdk-24/Contents/Home
  JOB_TYPE=integration .github/scripts/run_tests_graalvm_native.sh
}

## fix - Fixes java code format.
function fix() {
  mvn com.coveo:fmt-maven-plugin:format
}

## lint - runs the java lint
function lint() {
  mvn -P lint install -DskipTests=true
}

function download_and_expand() {
  url=$1
  tarFile=$2
  directory=$3

    if [[ ! -f "$tarFile" ]] ; then
      curl -L -o "$tarFile" "$url"
    fi
    if [[ ! -d "$directory" ]] ; then
      mkdir -p "$directory"
      tar -xf "$tarFile" -C "$directory" --strip-components=1 || (rm "$tarFile" ; exit 1)
    fi
}

function graalvm_tools() {
  mkdir -p .tools

  # Download the GraalVM and unzip into .tools
  if [[ $(uname) == "Darwin" ]] ; then
    oracle_24_url="https://download.oracle.com/graalvm/24/latest/graalvm-jdk-24_macos-aarch64_bin.tar.gz"
    ce_24_url="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_macos-aarch64_bin.tar.gz"
    ce_21_url="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_macos-aarch64_bin.tar.gz"
  elif [[ $(uname) == "Linux" ]] ; then
    oracle_24_url="https://download.oracle.com/graalvm/24/latest/graalvm-jdk-24_linux-x64_bin.tar.gz"
    ce_24_url="https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-24.0.2/graalvm-community-jdk-24.0.2_linux-x64_bin.tar.gz"
    ce_21_url="https://download.oracle.com/graalvm/21/latest/graalvm-jdk-21_linux-x64_bin.tar.gz"
  fi

  download_and_expand "$oracle_24_url" ".tools/graalvm_oracle_24.gz" ".tools/graalvm-jdk-24"
  download_and_expand "$ce_24_url" ".tools/graalvm_ce_24.gz" ".tools/graalvm-ce-jdk-24"
  download_and_expand "$ce_21_url" ".tools/graalvm_ce_21.gz" ".tools/graalvm-ce-jdk-21"
}

# write_e2e_env - Loads secrets from the gcloud project and writes
#     them to target/e2e.env to run e2e tests.
#

## deps - updates dependencies to the latest version
function deps() {
  mvn versions:use-latest-versions
  find . -name 'pom.xml.versionsBackup' -print0 | xargs -0 rm -f
}

function grant_iam_user_pg() {
  instance_name=$1
  root_user=$2
  root_pass=$3
  new_iam_user=$4
  echo
  echo "*"
  echo "* Grant access to the database to the current user by executing this sql statement: "
  echo "*"
  echo "*  GRANT ALL ON SCHEMA $POSTGRES_DB TO '$new_iam_user';"
  echo "*"
  echo "*    database password: $root_pass"
  echo "*"
  echo
  gcloud alpha sql connect --project="${TEST_PROJECT}" --user="$root_user" "$instance_name" --database "$POSTGRES_DB"
}

function grant_iam_user_mysql() {
  instance_name=$1
  root_user=$2
  root_pass=$3
  new_iam_user=$4

  echo
  echo "*"
  echo "* Grant access to the database to the current user by executing this sql statement: "
  echo "*"
  echo "*  GRANT ALL ON proxy-testing TO $new_iam_user@'%'; FLUSH PRIVILEGES;"
  echo "*"
  echo "*    database password: $root_pass"
  echo "*"
  gcloud alpha sql connect --project="${TEST_PROJECT}" --user="$root_user" "$instance_name" --database "$MYSQL_DB"
}

## grant_local_iam_user - Logs into each test database and prints out instructions on how to grant
##    the currently authenticated gcloud user access to the database schema.
function grant_local_iam_user() {
  if [[ ! -f .envrc ]] ; then
    write_e2e_env .envrc
  fi
  source "$SCRIPT_DIR/.envrc"

  local_user=$(gcloud auth list --format 'value(account)' | tr -d '\n')
  set -x
  grant_iam_user_mysql "${MYSQL_MCP_CONNECTION_NAME##*:}" "$MYSQL_USER" "$MYSQL_MCP_PASS" "${local_user%%@*}"
  grant_iam_user_mysql "${MYSQL_CONNECTION_NAME##*:}" "$MYSQL_USER" "$MYSQL_PASS" "${local_user%%@*}"
  grant_iam_user_pg "${POSTGRES_CONNECTION_NAME##*:}" "$POSTGRES_USER" "$POSTGRES_PASS" "$local_user"
  grant_iam_user_pg "${POSTGRES_CAS_CONNECTION_NAME##*:}" "$POSTGRES_CAS_USER" "$POSTGRES_PASS" "$local_user"
  grant_iam_user_pg "${POSTGRES_MCP_CONNECTION_NAME##*:}" "$POSTGRES_MCP_USER" "$POSTGRES_PASS" "$local_user"
}

## add_local_iam_user - Adds the currently authenticated gcloud user to all IAM test databases
function add_local_iam_user() {
  if [[ ! -f .envrc ]] ; then
    write_e2e_env .envrc
  fi
  source "$SCRIPT_DIR/.envrc"

  local_user=$(gcloud auth list --format 'value(account)' | tr -d '\n')

  mysql_instances=( "${MYSQL_CONNECTION_NAME##*:}"
    "${MYSQL_MCP_CONNECTION_NAME##*:}" )

  pg_instances=(
    "${POSTGRES_CAS_CONNECTION_NAME##*:}"
    "${POSTGRES_CONNECTION_NAME##*:}"
    "${POSTGRES_MCP_CONNECTION_NAME##*:}"
    )

  for inst in "${mysql_instances[@]}" ; do
    if gcloud sql users describe "${local_user%%@*}" \
                                  --host=% \
                                  --instance="$inst" \
                                  --project="${TEST_PROJECT}"  > /dev/null  2>&1 ; then
      echo "user %local_user exists in $inst"
    else
      gcloud sql users create "$local_user" \
                         --instance="$inst" \
                         --host=% \
                         --type=cloud_iam_user \
                         --project="${TEST_PROJECT}"
     fi
  done

  for inst in "${pg_instances[@]}" ; do
    if gcloud sql users describe "$local_user" \
                                  --host=% \
                                  --instance="$inst" \
                                  --project="${TEST_PROJECT}" > /dev/null  2>&1  ; then
      echo "user %local_user exists in $inst"
    else
      gcloud sql users create "$local_user" \
                         --instance="$inst" \
                         --host=% \
                         --type=cloud_iam_user \
                         --project="${TEST_PROJECT}"
    fi
    export PGPASSWORD=
    gcloud sql connect -u
  done
}

# write_e2e_env - Loads secrets from the gcloud project and writes
#     them to target/e2e.env to run e2e tests.
function write_e2e_env(){
  # Set the default to .envrc file if no argument is passed
  outfile="${1:-.envrc}"
  secret_vars=(
    MYSQL_CONNECTION_NAME=MYSQL_CONNECTION_NAME
    MYSQL_USER=MYSQL_USER
    MYSQL_PASS=MYSQL_PASS
    MYSQL_DB=MYSQL_DB
    MYSQL_MCP_CONNECTION_NAME=MYSQL_MCP_CONNECTION_NAME
    MYSQL_MCP_PASS=MYSQL_MCP_PASS
    POSTGRES_CONNECTION_NAME=POSTGRES_CONNECTION_NAME
    POSTGRES_USER=POSTGRES_USER
    POSTGRES_PASS=POSTGRES_PASS
    POSTGRES_DB=POSTGRES_DB
    POSTGRES_CAS_CONNECTION_NAME=POSTGRES_CAS_CONNECTION_NAME
    POSTGRES_CAS_PASS=POSTGRES_CAS_PASS
    POSTGRES_CUSTOMER_CAS_CONNECTION_NAME=POSTGRES_CUSTOMER_CAS_CONNECTION_NAME
    POSTGRES_CUSTOMER_CAS_PASS=POSTGRES_CUSTOMER_CAS_PASS
    POSTGRES_CUSTOMER_CAS_VALID_DOMAIN_NAME=POSTGRES_CUSTOMER_CAS_PASS_VALID_DOMAIN_NAME
    POSTGRES_CUSTOMER_CAS_INVALID_DOMAIN_NAME=POSTGRES_CUSTOMER_CAS_PASS_INVALID_DOMAIN_NAME
    POSTGRES_MCP_CONNECTION_NAME=POSTGRES_MCP_CONNECTION_NAME
    POSTGRES_MCP_PASS=POSTGRES_MCP_PASS
    SQLSERVER_CONNECTION_NAME=SQLSERVER_CONNECTION_NAME
    SQLSERVER_USER=SQLSERVER_USER
    SQLSERVER_PASS=SQLSERVER_PASS
    SQLSERVER_DB=SQLSERVER_DB
    IMPERSONATED_USER=IMPERSONATED_USER
    QUOTA_PROJECT=QUOTA_PROJECT
  )

  if [[ -z "${TEST_PROJECT:-}" ]] ; then
    echo "Set TEST_PROJECT environment variable to the project containing"
    echo "the e2e test suite secrets."
    exit 1
  fi

  echo "Getting test secrets from $TEST_PROJECT into $outfile"
  local_user=$(gcloud auth list --format 'value(account)' | tr -d '\n')

  echo "Getting test secrets from $TEST_PROJECT into $1"
  {
  for env_name in "${secret_vars[@]}" ; do
    env_var_name="${env_name%%=*}"
    secret_name="${env_name##*=}"
    set -x
    val=$(gcloud secrets versions access latest --project "$TEST_PROJECT" --secret="$secret_name")
    echo "export $env_var_name='$val'"
  done
  # Set IAM User env vars to the local gcloud user
  echo "export MYSQL_IAM_USER='${local_user%%@*}'"
  echo "export POSTGRES_IAM_USER='$local_user'"
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

