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
return_code=0
for test_directory in jdbc/postgres jdbc/mysql-j-5 jdbc/mysql-j-8  jdbc/sqlserver r2dbc/sqlserver r2dbc/sqlserver r2dbc/mysql; do
  pushd ${test_directory}
  echo -e "******************** Running tests in ${test_directory} ********************\n"
  # Why "-Denforcer.skip"? It's because  enforcer complains about the specific
  # version of junit-platform-engine GraalVM requires.
  mvn -e -B clean verify -P e2e,native -Dcheckstyle.skip -Denforcer.skip
    return_code=$((return_code || $?))
  popd
  echo -e "******************** Tests complete in ${test_directory} ********************\n"
done
exit ${return_code}