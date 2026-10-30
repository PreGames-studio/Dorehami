#!/usr/bin/env bash
# Reproduce configuration-change handling in an already-built APK.
# This deliberately modifies settings: only run on a verified disposable emulator.
set -euo pipefail
if [[ $# -ne 3 || ( "$3" != locale && "$3" != font ) ]]; then
    echo "Usage: $0 APK EXPECTED_PAGE_SIZE locale|font" >&2
    exit 2
fi
apk=$1
expected_page_size=$2
scenario=$3
package=com.gf2227.dezhban
serial=$(adb get-serialno | tr -d '\r')
qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
page=$(adb shell getconf PAGESIZE | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$qemu" != 1 || "$page" != "$expected_page_size" ]]; then
    echo "Refusing a non-disposable or wrong-page-size device: serial=$serial qemu=$qemu page=$page" >&2
    exit 2
fi
test -s "$apk"
if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
    echo 'Expected a fresh emulator without an existing game installation.' >&2
    exit 1
fi
adb logcat -G 8M || true
adb shell setprop log.tag.Godot VERBOSE || true
adb shell setprop log.tag.GodotActivity VERBOSE || true
adb logcat -c
adb logcat -b crash -c
cleanup() {
    if [[ "$scenario" == font ]]; then
        adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT
report() {
    echo '--- Relevant lifecycle and crash logs ---'
    adb logcat -d -v threadtime 2>&1 | \
        grep -Ei 'GodotActivity|Godot   : On(Create|Pause|Stop|Destroy)|OnInitNativeLayer|com\.gf2227|Fatal signal|Abort message|FORTIFY|lowmemorykiller.*kill|config changes' | tail -100 || true
    echo '--- Crash buffer ---'
    adb logcat -b crash -d -v threadtime 2>&1 | head -45 || true
}
adb install "$apk"
result=$(timeout 120s adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$package" 2>&1) || { echo "$result"; report; exit 1; }
echo "$result"
grep -q 'Status: ok' <<< "$result" || { report; exit 1; }
sleep 15
before=$(adb shell pidof "$package" | tr -d '\r' || true)
if [[ ! "$before" =~ ^[0-9]+$ ]]; then
    echo 'Game did not survive the initial launch.'
    report
    exit 1
fi
echo "Game PID before $scenario change: $before"
case "$scenario" in
    locale)
        echo 'Changing the application locale to Persian.'
        adb shell cmd locale set-app-locales "$package" --locales fa-IR
        ;;
    font)
        echo 'Changing the system font scale on the disposable emulator.'
        adb shell settings put system font_scale 1.2
        ;;
esac
sleep 10
after=$(adb shell pidof "$package" | tr -d '\r' || true)
echo "Game PID after $scenario change: ${after:-none}"
report
if [[ "$before" != "$after" ]]; then
    echo 'Game process did not survive the configuration change.' >&2
    exit 1
fi
echo "The game process survived the $scenario configuration change."
