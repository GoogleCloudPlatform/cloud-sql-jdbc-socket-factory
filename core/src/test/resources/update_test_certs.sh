#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

## Change working directory to $PROJECT_ROOT/core/
cd "$SCRIPT_DIR/../../.."

function new_ca_cert() {
  cert_name=$1
  cn=$2

  # Create new self-signed client signer CA
  openssl req -x509 -nodes -days 36500 -newkey rsa:2048 -sha256 \
    -keyout "$workdir/$cert_name.key" \
    -out "$workdir/$cert_name.cer" \
    -subj "/C=US/O=Google\, Inc/CN=$cn"
  cp "$workdir/$cert_name.key" "src/test/resources/certs/$cert_name.key"
  cp "$workdir/$cert_name.cer" "src/test/resources/certs/$cert_name.cer"

}

function new_server_cert() {
  cert_name=$1
  cn=$2
  ca_name=$3

  # Create a new CSR with the subject
  openssl req -nodes -days 36500 -newkey rsa:2048 -sha256 \
    -keyout "$workdir/$cert_name.key" \
    -subj "/C=US/O=Google\, Inc/CN=$cn" \
    -out "$workdir/$cert_name.csr"

  # issue a cert for the subject from the fake CSR but the real pubkey!
  openssl x509 -req -days 36500 \
    -in "$workdir/$cert_name.csr" \
    -CA "$workdir/$ca_name.cer" \
    -CAkey "$workdir/$ca_name.key" \
    -out "$workdir/$cert_name.cer"

  # Save the public key in its own file
  openssl x509 -pubkey -noout -in "$workdir/$cert_name.cer" \
    > "$workdir/$cert_name-pub.key"

  # Copy server keys into test resources
  cp "$workdir/$cert_name.cer" "src/test/resources/certs/$cert_name.cer"
  cp "$workdir/$cert_name.key" "src/test/resources/certs/$cert_name.key"
  cp "$workdir/$cert_name-pub.key" "src/test/resources/certs/$cert_name-pub.key"
}

set -euxo

# Create empty work directory
workdir=target/update-certs
if [[ -d "$workdir" ]] ; then
  rm -rf "$workdir"
fi
mkdir -p "$workdir"

## Ensure test resource certificate directory exists
mkdir -p "src/test/resources/certs"

new_ca_cert "server-ca" "Google Cloud SQL Server CA"

new_server_cert "server" "myProject:myInstance" "server-ca"

new_ca_cert "signing-ca" "Google Cloud SQL Signing CA foo:baz"
new_server_cert "client" "myProject:myInstance" "signing-ca"
