#!/bin/bash
# Copyright 2022 Google LLC
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

function determineMavenOpts() {
  local javaVersion=$(
    # filter down to the version line, then pull out the version between quotes,
    # then trim the version number down to its minimal number (removing any
    # update or suffix number).
    java -version 2>&1 | grep "version" \
      | sed -E 's/^.*"(.*?)".*$/\1/g' \
      | sed -E 's/^(1\.[0-9]\.0).*$/\1/g'
    )

  # Workaround for google-java-format to work on java 17+
  if [[ $javaVersion == 17* ]]
    then
    echo -n " --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED"
    echo -n " --add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"
    echo -n " --add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED"
    echo -n " --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
    echo -n " --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
  fi
}
