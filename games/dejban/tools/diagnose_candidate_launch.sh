#!/usr/bin/env bash
# Compare one already-built candidate to a known older APK on a disposable emulator.
set -euo pipefail
if [[ $# -ne 4 ]]; then
    echo "Usage: $0 CANDIDATE_APK OLD_APK EXPECTED_PAGE_SIZE ABI_OR_EMPTY" >&2
    exit 2
fi
candidate=$1
old=$2
expected_page_size=$3
abi=$4
package=com.gf2227.dezhban
serial=$(adb get-serialno | tr -d '\r')
qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
actual_page_size=$(adb shell getconf PAGESIZE | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$qemu" != 1 || "$actual_page_size" != "$expected_page_size" ]]; then
    echo "Refusing non-disposable or wrong-page-size device: serial=$serial qemu=$qemu page=$actual_page_size" >&2
    exit 2
fi
for file in "$old" "$candidate"; do test -s "$file"; done

print_debug() {
    echo '--- Last relevant Android logs ---'
    adb logcat -d -v threadtime 2>&1 | \
        grep -Ei 'godot|com\.gf2227|androidruntime|fatal signal|fatal exception|abort message|tombstone|linker.*(fail|error)|lmkd.*kill|lowmemorykiller.*kill|out of memory' | tail -80 || true
    echo '--- Android crash buffer ---'
    adb logcat -b crash -d -v threadtime 2>&1 | head -100 || true
    echo '--- Emulator connection ---'
    adb get-state 2>&1 || true
}
run_apk() {
    local label=$1 apk=$2
    echo "--- $label APK on ${actual_page_size}-byte pages, ABI ${abi:-native} ---"
    adb logcat -c || true
    adb logcat -b crash -c || true
    local install_args=(install)
    if [[ -n "$abi" ]]; then install_args+=(--abi "$abi"); fi
    if ! adb "${install_args[@]}" "$apk"; then
        echo "$label install failed"
        print_debug
        return 1
    fi
    local result status
    set +e
    result=$(timeout 120s adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$package" 2>&1)
    status=$?
    set -e
    echo "am start exit: $status"
    echo "$result"
    if [[ "$status" -ne 0 ]]; then
        print_debug
        return 1
    fi
    sleep 15
    if ! adb shell pidof "$package"; then
        echo "$label process did not survive launch"
        print_debug
        return 1
    fi
    if ! grep -q 'Status: ok' <<< "$result"; then
        echo "$label launch did not report Status: ok"
        print_debug
        return 1
    fi
    echo "$label installation and process survival passed."
}

echo '--- Initial emulator memory ---'
adb shell cat /proc/meminfo 2>&1 | head -12 || true
if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
    echo 'Expected a fresh emulator.' >&2
    exit 1
fi
# The old APK is a 4 KiB ELF and is intentionally not launched on 16 KiB.
if [[ "$expected_page_size" == 4096 ]]; then
    run_apk baseline "$old"
    adb uninstall "$package"
fi
run_apk candidate "$candidate"
