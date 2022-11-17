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

function setJava() {
  export JAVA_HOME=$1
  export PATH=${JAVA_HOME}/bin:$PATH
}

if [[ $OSTYPE == 'darwin'* ]]; then
  # Add alias for 127.0.0.2 to be used as a loopback address
  # https://superuser.com/questions/458875/how-do-you-get-loopback-addresses-other-than-127-0-0-1-to-work-on-os-x
  sudo ifconfig lo0 alias 127.0.0.2 up
fi

echo -e "******************** Running tests... ********************\n"
# unit-e2e-java8 uses both JDK 11 and JDK 8. GraalVM dependencies require JDK 11 to
# compile the classes touching GraalVM classes.
if [ ! -z "${JAVA11_HOME}" ]; then
  setJava "${JAVA11_HOME}"
fi

echo "Compiling using Java:"
java -version
echo

mvn clean install -e -B  -ntp -DskipTests -Dcheckstyle.skip

# We ensure the generated class files are compatible with Java 8
if [ ! -z "${JAVA8_HOME}" ]; then
  setJava "${JAVA8_HOME}"
fi

if [ "${GITHUB_JOB}" == "units-java8" ]; then
  java -version 2>&1 | grep -q 'openjdk version "1.8.'
  MATCH=$? # 0 if the output has the match
  if [ "$MATCH" != "0" ]; then
    echo "Please specify JDK 8 for Java 8 tests"
    exit 1
  fi
fi

echo "Running tests using Java:"
java -version

echo "Maven version: $(mvn --version)"
mvn -e -B  -ntp  verify -P e2e -Dcheckstyle.skip
echo -e "******************** Tests complete.  ********************\n"
