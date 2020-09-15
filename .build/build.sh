#! /bin/bash
# Copyright 2020 Google LLC
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

set -e # exit immediatly if any step fails

BUCKET_NAME='cloud-sql-java-connector'
PROJ_ROOT="$( cd "$( dirname "${BASH_SOURCE[0]}" )"/.. >/dev/null 2>&1 && pwd )"
cd "$PROJ_ROOT"

# get the current version
export VERSION=$(grep -oP "(?<=:)[0-9A-Z\.\-]*$" versions.txt)
if [ -z "$VERSION" ]; then
  echo "error: No versions.txt found in $PROJ_ROOT"
  exit 1
fi

read -p "This will release new Cloud SQL Java Connector artifacts for \"$VERSION\", even if they already exist. Are you sure (y/Y)? " -n 1 -r
echo
if [[ ! $REPLY =~ ^[Yy]$ ]]
then
    exit 1
fi

# Build jars and upload to GCS
gcloud builds submit --config .build/build_uberjars.yaml --substitutions _VERSION="$VERSION"
# Cleanup
gsutil rm -f gs://$BUCKET_NAME/v"$VERSION"/*.json 2> /dev/null || true

# Generate sha256 hashes for authentication
echo -e "Add the following table to the release notes on GitHub: \n\n"
echo "| filename | sha256 hash |"
echo "|----------|-------------|"
for f in $(gsutil ls "gs://$BUCKET_NAME/v$VERSION/*"); do
    file=$(basename "$f")
    sha=$(gsutil cat "$f" | sha256sum --binary)
    echo "| [$file](https://storage.googleapis.com/$BUCKET_NAME/v$VERSION/$file) | $sha |"
done
