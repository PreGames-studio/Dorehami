#!/usr/bin/env bash
# Exercise the exact signed standalone APK on a fresh emulator.
set -euo pipefail

apk="${1:-apk/dezhban.apk}"
package="com.gf2227.dezhban"

dump_errors() {
    adb logcat -d -s AndroidRuntime:E Godot:V | tail -80 || true
}

test -s "$apk"
adb logcat -c
echo "Installing $apk without the test-only override."
adb install "$apk"
echo 'Resolving launcher activity.'
adb shell cmd package resolve-activity --brief "$package" || true
echo 'Starting launcher activity.'
if ! result=$(adb shell am start -W -a android.intent.action.MAIN \
    -c android.intent.category.LAUNCHER -p "$package" 2>&1); then
    echo "$result"
    dump_errors
    exit 1
fi
echo "$result"
if ! grep -q 'Status: ok' <<< "$result"; then
    dump_errors
    exit 1
fi
sleep 15
if ! adb shell pidof "$package"; then
    echo 'Game process exited after launch.'
    dump_errors
    exit 1
fi
adb exec-out screencap -p > dezhban-emulator.png
echo 'Fresh installation and launch succeeded on Android API 34.'
