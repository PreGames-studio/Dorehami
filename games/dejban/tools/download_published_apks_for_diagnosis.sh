#!/usr/bin/env bash
# Download existing APK releases only; this script never builds or publishes one.
set -euo pipefail

out_dir="${1:?output directory required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY required}"
: "${CURRENT_APK_SHA256:?CURRENT_APK_SHA256 required}"
: "${PREVIOUS_APK_SHA256:?PREVIOUS_APK_SHA256 required}"
mkdir -p "$out_dir"
base="https://github.com/${GITHUB_REPOSITORY}/releases/download"
curl -fsSL --retry 3 -o "$out_dir/current.apk" "$base/apk-latest/dezhban.apk"
curl -fsSL --retry 3 -o "$out_dir/previous.apk" \
    "$base/preview-dejban-arena-fdff2718-dorehami-9794957/dezhban.apk"
printf '%s  %s\n' "$CURRENT_APK_SHA256" "$out_dir/current.apk" | sha256sum -c -
printf '%s  %s\n' "$PREVIOUS_APK_SHA256" "$out_dir/previous.apk" | sha256sum -c -
