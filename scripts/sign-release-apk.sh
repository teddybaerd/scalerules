#!/usr/bin/env bash
# Copyright (c) 2026 ScaleRules contributors
# SPDX-License-Identifier: BSD-3-Clause
# Password is read by apksigner from the environment, never from command arguments.
set -euo pipefail
if [[ $# != 3 ]]; then
  echo 'Usage: sign-release-apk.sh UNSIGNED_APK OUTPUT_APK PKCS12_KEYSTORE' >&2
  exit 2
fi
input=$1
output=$2
keystore=$3
[[ -f "$input" && -f "$keystore" ]] || { echo 'APK or keystore missing' >&2; exit 1; }
[[ ! -e "$output" ]] || { echo 'Refusing to replace an existing APK' >&2; exit 1; }
: "${ANDROID_KEYSTORE_PASSWORD:?Signing password missing}"
: "${ANDROID_SIGNING_CERT_SHA256:?Expected certificate fingerprint missing}"
: "${SCALERULES_VERSION:?Expected app version missing}"
: "${ANDROID_HOME:?Android SDK location missing}"
tools_version=$(sed -n 's/^androidBuildToolsVersion=//p' android/gradle.properties)
build_tools="$ANDROID_HOME/build-tools/$tools_version"
analyzer="$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
working=$(mktemp -d "${RUNNER_TEMP:-/tmp}/scalerules-apksign.XXXXXX")
trap 'rm -rf "$working"' EXIT
"$build_tools/zipalign" -P 16 -f 4 "$input" "$working/aligned.apk"
"$build_tools/apksigner" sign --ks "$keystore" --ks-type PKCS12 \
  --ks-pass env:ANDROID_KEYSTORE_PASSWORD --key-pass env:ANDROID_KEYSTORE_PASSWORD \
  --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
  --v4-signing-enabled false --out "$working/signed.apk" "$working/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$working/signed.apk" > "$working/verification.txt"
python3 - "$working/verification.txt" <<'PY'
import os, re, sys
from pathlib import Path
expected = re.sub(r'[:\s]', '', os.environ['ANDROID_SIGNING_CERT_SHA256']).lower()
assert re.fullmatch('[0-9a-f]{64}', expected), 'Invalid expected certificate fingerprint'
actual = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', Path(sys.argv[1]).read_text(), re.M)
assert len(actual) == 1 and actual[0].lower() == expected, 'Signing certificate does not match the pinned fingerprint'
print('Signing certificate matches the pinned SHA-256 fingerprint.')
PY
[[ $("$analyzer" manifest application-id "$working/signed.apk") == 'io.github.teddybaerd.scalerules' ]]
[[ $("$analyzer" manifest version-name "$working/signed.apk") == "$SCALERULES_VERSION" ]]
[[ $("$analyzer" manifest debuggable "$working/signed.apk") == 'false' ]]
"$build_tools/zipalign" -c -P 16 4 "$working/signed.apk"
mkdir -p "$(dirname "$output")"
install -m 644 "$working/signed.apk" "$output"
echo 'Release APK signature, package, version, debuggable flag and alignment verified.'
