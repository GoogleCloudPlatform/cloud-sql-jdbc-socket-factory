#!/bin/bash
# Copyright 2023 Google LLC
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

set -eo pipefail

export CUR_COVER=$(cat core/target/site/jacoco/index.html | grep -o 'Total[^%]*' | sed 's/<.*>//; s/Total//')
echo "Current Coverage is $CUR_COVER%"
if [ "$CUR_COVER" -lt 75  ]; then
  exit 1;
fi