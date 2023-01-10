#! /bin/bash
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

touch release_table.md
types=("mysql-socket-factory" "postgres-socket-factory" "jdbc-sqlserver" "r2dbc-mysql" "r2dbc-postgres" "r2dbc-sqlserver")
for t in "${types[@]}"; do
echo "### $t" >> release_table.md
echo "| filename | sha256 hash |" >> release_table.md
echo "|----------|-------------|" >> release_table.md
for f in $(gsutil ls "gs://$BUCKET_NAME/v$VERSION/*$t*"); do
    file=$(basename "$f")
    sha=$(gsutil cat "$f" | sha256sum --binary | head -c 64)
    echo "| [$file](https://storage.googleapis.com/$BUCKET_NAME/v$VERSION/$file) | $sha |" >> release_table.md
done
echo -e "\n\n" >> release_table.md
done
