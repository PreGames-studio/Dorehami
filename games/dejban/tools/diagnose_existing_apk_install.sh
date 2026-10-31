#!/usr/bin/env bash
# Compare an existing APK update and a fresh UI install on a disposable emulator.
# This script does not produce, sign, modify, or publish an APK.
set -euo pipefail
old_apk="${1:?previous APK required}"
new_apk="${2:?candidate APK required}"
expected_page_size="${3:-}"
package=com.gf2227.dezhban
serial=$(adb get-serialno | tr -d '\r')
is_emulator=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$is_emulator" != 1 ]]; then
    echo 'Refusing installation diagnostics outside a disposable emulator.' >&2
    exit 1
fi
test -s "$old_apk"
test -s "$new_apk"
if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
    echo 'Expected an emulator without an existing Dezhban installation.' >&2
    exit 1
fi
printf '%s\n' 'Installing the previous APK on the emulator.'
adb install "$old_apk"
printf '%s\n' 'Attempting to update it with the exact candidate APK.'
if result=$(adb install -r "$new_apk" 2>&1); then
    printf '%s\n' "$result" 'Update accepted on the emulator.'
else
    printf '%s\n' "$result" 'Update rejected on the emulator; the precise reason is shown above.'
fi
printf '%s\n' 'Removing the emulator-only installation before a fresh visible install.'
adb uninstall "$package"
script_dir=$(cd "$(dirname "$0")" && pwd)
bash "$script_dir/diagnose_apk_installer_ui.sh" "$new_apk" "$expected_page_size"
