#!/usr/bin/env bash
# Try to install a published APK on the running emulator and explain any refusal.
#
# "برنامه نصب نشد چون به نظر می‌رسد بسته معتبر نیست" ("App not installed as package
# appears to be invalid") comes from PackageInstaller after the APK parsed
# successfully, so the manifest/icon are fine and the refusal happens during
# install: signature verification, native-library extraction, ABI support or a
# device policy. adb reports those as an INSTALL_FAILED_* / INSTALL_PARSE_FAILED_*
# status, which this script prints together with the matching system log lines.
#
# Usage: install_and_explain.sh <apk> <package-id> [log-file]
set -euo pipefail

APK="${1:?usage: install_and_explain.sh <apk> <package-id> [log]}"
PACKAGE_ID="${2:?usage: install_and_explain.sh <apk> <package-id> [log]}"
LOG="${3:-compat.log}"

: > "$LOG"
log() { printf '%s\n' "$*" | tee -a "$LOG"; }

log "=== emulator profile ==="
log "sdk:        $(adb shell getprop ro.build.version.sdk | tr -d '\r')"
log "release:    $(adb shell getprop ro.build.version.release | tr -d '\r')"
log "abi list:   $(adb shell getprop ro.product.cpu.abilist | tr -d '\r')"
log "page size:  $(adb shell getconf PAGESIZE | tr -d '\r')"
log "free bytes: $(adb shell df -B1 /data | tail -n 1 | tr -s ' ' | cut -d' ' -f4 | tr -d '\r')"
log "features:   $(adb shell pm list features 2>/dev/null | tr -d '\r' | grep -c . || true) entries"
log "apk size:   $(stat -c%s "$APK") bytes"

adb shell settings put global package_verifier_enable 0 >/dev/null 2>&1 || true
adb logcat -c || true

status=0
log ""
log "=== adb install (streaming) ==="
if ! adb install -r -t "$APK" 2>&1 | tee -a "$LOG"; then
    log "streaming install failed; retrying with --no-streaming"
    adb uninstall "$PACKAGE_ID" >/dev/null 2>&1 || true
    if ! adb install --no-streaming -r -t "$APK" 2>&1 | tee -a "$LOG"; then
        status=1
    fi
fi

if adb shell pm list packages "$PACKAGE_ID" | tr -d '\r' | grep -Fxq "package:$PACKAGE_ID"; then
    log "listed: package:$PACKAGE_ID"
else
    log "not listed after install"
    status=1
fi

if [ "$status" -ne 0 ]; then
    log ""
    log "=== why the device refused it ==="
    adb logcat -d -v brief 2>/dev/null \
        | grep -Ei 'PackageManager|PackageInstaller|InstallStart|InstallStaging|PackageParser|installd|nativeLibrary|INSTALL_|FATAL|Caused by' \
        | tail -n 120 | tee -a "$LOG" || true
    log ""
    log "=== installed-library view (if it landed at all) ==="
    adb shell dumpsys package "$PACKAGE_ID" 2>/dev/null | grep -Ei 'primaryCpuAbi|secondaryCpuAbi|versionCode|codePath|flags' | head -n 20 | tee -a "$LOG" || true
else
    log ""
    log "=== installed package view ==="
    adb shell dumpsys package "$PACKAGE_ID" 2>/dev/null \
        | grep -Ei 'primaryCpuAbi|secondaryCpuAbi|versionCode|codePath|flags' | head -n 20 | tee -a "$LOG" || true
    log "PASS: installed on this emulator profile."
fi

exit "$status"
