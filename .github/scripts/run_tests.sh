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

if [[ $OSTYPE == 'darwin'* ]]; then
  # Add alias for 127.0.0.2 to be used as a loopback address
  # https://superuser.com/questions/458875/how-do-you-get-loopback-addresses-other-than-127-0-0-1-to-work-on-os-x
  sudo ifconfig lo0 alias 127.0.0.2 up
  sudo ifconfig lo0 alias 127.0.0.3 up
fi

echo -e "******************** Running tests... ********************\n"
echo "Running tests using Java:"
java -version

echo "Maven version: $(mvn --version)"

echo "Job type: ${JOB_TYPE}"

RETURN_CODE=0
set +e

case ${JOB_TYPE} in
test)
    mvn -e -B clean -ntp test -P coverage -Dcheckstyle.skip
    RETURN_CODE=$?
    ;;
integration)
    mvn -e -B clean -ntp verify -P e2e -P coverage -Dcheckstyle.skip
    RETURN_CODE=$?
    ;;
esac

echo -e "******************** Tests complete.  ********************\n"
echo "exiting with ${RETURN_CODE}"
exit ${RETURN_CODE}
