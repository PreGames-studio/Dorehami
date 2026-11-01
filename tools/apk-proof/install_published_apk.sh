#!/usr/bin/env bash
# Install and launch an already-published Dorehami APK on the running emulator.
#
# The Android emulator runner executes its `script:` block with `sh`, so every
# multi-line bash block there is a syntax hazard (exit code 2 before anything
# runs). Keeping the work in this file means the workflow only has to call
# `bash tools/apk-proof/install_published_apk.sh <apk> <package>`.
#
# Usage: install_published_apk.sh <apk path> <package id> [log path]
set -euo pipefail

APK="${1:?usage: install_published_apk.sh <apk> <package id> [log]}"
PACKAGE_ID="${2:?usage: install_published_apk.sh <apk> <package id> [log]}"
LOG="${3:-proof.log}"

: > "$LOG"
log() { printf '%s\n' "$*" | tee -a "$LOG"; }

log "APK: $APK ($(du -h "$APK" | cut -f1))"
log "package: $PACKAGE_ID"
log "ABI list: $(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
log "sdk: $(adb shell getprop ro.build.version.sdk | tr -d '\r')"

# A stalled verifier is a common cause of "install timeout" on CI emulators.
adb shell settings put global package_verifier_enable 0 >/dev/null 2>&1 || true

log "--- adb install ---"
adb install -r -t "$APK" 2>&1 | tee -a "$LOG"

installed=$(adb shell pm list packages "$PACKAGE_ID" | tr -d '\r' | grep -Fx "package:$PACKAGE_ID" || true)
if [ "$installed" != "package:$PACKAGE_ID" ]; then
    log "FAIL: $PACKAGE_ID is not listed after install"
    exit 1
fi
log "installed: $installed"

adb logcat -c || true
log "--- launch ---"
adb shell monkey -p "$PACKAGE_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
sleep 20

pid=$(adb shell pidof "$PACKAGE_ID" | tr -d '\r' || true)
log "pid after launch: ${pid:-<not running>}"
adb logcat -d > logcat.txt 2>/dev/null || true

if grep -qE "FATAL EXCEPTION|INSTALL_FAILED|Failure \\[" logcat.txt; then
    log "FAIL: crash or installer error while launching the published APK"
    grep -E "FATAL EXCEPTION|INSTALL_FAILED|Failure \\[" -A 15 logcat.txt | head -n 80 | tee -a "$LOG"
    exit 1
fi

if [ -z "$pid" ]; then
    log "FAIL: the app process is not running after launch"
    exit 1
fi

log "PASS: the published APK installed and launched on the emulator."
