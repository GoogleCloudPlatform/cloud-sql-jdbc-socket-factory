#! /bin/bash
# Copyright 2020 Google Inc.
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

# `-e` enables the script to automatically fail when a command fails
set -e

# Kokoro setup
if [ -n "$KOKORO_GFILE_DIR" ]; then
  # Move into project directory
  cd github/cloud-sql-jdbc-socket-factory
  # source secrets
  source "${KOKORO_GFILE_DIR}/TEST_SECRETS.sh"
  export GOOGLE_APPLICATION_CREDENTIALS="${KOKORO_GFILE_DIR}/testing-service-account.json"
fi

echo -e "******************** Installing modules... ********************\n"
mvn -e -B install -DskipTests
echo -e "******************** Installation complete.  ********************\n"
echo "JAVA_HOME: $JAVA_HOME"
echo "Java version:"
java -version

# Why change directories to run the tests? Because GraalVM test execution
# requires at least one matching test per Maven module. Not all modules in this
# repository have "*IntegrationTests.java".
# https://github.com/graalvm/native-build-tools/issues/188
set +e
declare -i return_code=0

# Currently, jdbc/postgres works with GraalVM native image.
# TODO(#824): Provide GraalVM configuration and enable native image tests below:
# jdbc/mysql-j-5 jdbc/mysql-j-8  jdbc/sqlserver r2dbc/sqlserver r2dbc/sqlserver
# r2dbc/mysql
# https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factory/issues/824
for test_directory in jdbc/postgres; do
  pushd ${test_directory}
  echo -e "******************** Running tests in ${test_directory} ********************\n"
  # Dependency convergence enforcer rule would fail with the junit dependencies
  # specified in "native" profile. The test-scope dependencies do not have any
  # effect to library users' class path.
  mvn -e -B clean verify -P e2e,native
  result=$?
  return_code=$((return_code || result))
  echo -e "******************** Tests complete in ${test_directory}, result: $result ********************\n"
  popd
done
exit ${return_code}