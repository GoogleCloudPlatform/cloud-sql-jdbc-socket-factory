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

if [[ $OSTYPE == 'darwin'* ]]; then
  # Add alias for 127.0.0.2 to be used as a loopback address
  # https://superuser.com/questions/458875/how-do-you-get-loopback-addresses-other-than-127-0-0-1-to-work-on-os-x
  ifconfig lo0 alias 127.0.0.2 up
fi

echo -e "******************** Running tests... ********************\n"
echo "Maven version: $(mvn --version)"
mvn -e -B clean verify -P e2e -Dcheckstyle.skip
echo -e "******************** Tests complete.  ********************\n"
