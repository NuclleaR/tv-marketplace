#!/usr/bin/env bash
# Writes the release keystore from the SIGNING_KEYSTORE secret (base64) to signing/signing.keystore.
set -euo pipefail

if [[ -z "${SIGNING_KEYSTORE:-}" ]]; then
  echo "Error: SIGNING_KEYSTORE is not set or empty." >&2
  exit 1
fi
mkdir -p signing
echo "$SIGNING_KEYSTORE" | base64 --decode >signing/signing.keystore
echo "Written signing/signing.keystore"
