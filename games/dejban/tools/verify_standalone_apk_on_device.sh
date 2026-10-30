#!/usr/bin/env bash
# Exercise the exact signed standalone APK on a fresh emulator.
set -euo pipefail

apk="${1:-apk/dezhban.apk}"
abi="${2:-}"
expected_page_size="${3:-}"
rounds="${4:-1}"
package="com.gf2227.dezhban"
if [[ ! "$rounds" =~ ^[1-3]$ ]]; then
    echo 'Rounds must be between 1 and 3.' >&2
    exit 2
fi

dump_errors() {
    # Preserve the complete buffers: tail-only output can hide the actual
    # app crash behind a noisy system-service backtrace.
    adb logcat -d -v threadtime > dezhban-device-logcat.txt 2>&1 || true
    adb logcat -b crash -d -v threadtime > dezhban-device-crash.txt 2>&1 || true
    adb shell dumpsys activity exit-info "$package" > dezhban-device-exit-info.txt 2>&1 || true
    echo '--- Relevant Android logs ---'
    grep -Ei 'godot|com\.gf2227|androidruntime|fatal|crash|linker|lowmemorykiller|lmkd|system_server|activitymanager|zygote|killed|out of memory' \
        dezhban-device-logcat.txt | tail -100 || true
    echo '--- Android crash buffer (first lines) ---'
    head -80 dezhban-device-crash.txt || true
    echo '--- Android application exit information ---'
    head -100 dezhban-device-exit-info.txt || true
    echo '--- Emulator connection ---'
    adb get-state 2>&1 || true
}

test -s "$apk"
page_size=$(adb shell getconf PAGESIZE | tr -d '\r')
echo "Runtime page size: $page_size bytes."
if [[ -n "$expected_page_size" && "$page_size" != "$expected_page_size" ]]; then
    echo "Expected page size $expected_page_size, but emulator reported $page_size." >&2
    exit 1
fi
if (( rounds > 1 )); then
    # Repeated fresh installs remove the package between rounds: never do that
    # on a physical device or any serial not proven to be an emulator.
    serial=$(adb get-serialno | tr -d '\r')
    qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
    if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$qemu" != 1 ]]; then
        echo 'Refusing repeated install/uninstall outside a disposable emulator.' >&2
        exit 2
    fi
fi
if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
    echo 'Expected a fresh emulator without an existing Dezhban installation.' >&2
    exit 1
fi
if (( rounds > 1 )); then
    # Safe on the disposable emulators already checked above; avoid changing
    # logging properties on physical devices.
    adb logcat -G 8M || true
    adb shell setprop log.tag.Godot VERBOSE || true
    adb shell setprop log.tag.GodotActivity VERBOSE || true
fi
install_args=(install)
if [[ -n "$abi" ]]; then
    install_args+=(--abi "$abi")
fi
for ((round = 1; round <= rounds; round++)); do
    if (( round > 1 )); then
        echo 'Uninstalling from the disposable emulator before the next fresh install.'
        adb uninstall "$package"
    fi
    echo "--- Fresh install and launch round $round/$rounds ---"
    adb logcat -c
    adb logcat -b crash -c
    echo "Installing $apk without the test-only override${abi:+, forcing ABI $abi}."
    adb "${install_args[@]}" "$apk"
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
    echo "Fresh install and launch round $round passed."
done
adb exec-out screencap -p > dezhban-emulator.png
echo "Fresh installation and launch succeeded $rounds time(s) on Android API $(adb shell getprop ro.build.version.sdk | tr -d '\r')${abi:+ with ABI $abi}."
