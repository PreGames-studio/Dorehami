#!/usr/bin/env bash
# Inspect existing, independently downloaded releases on a disposable emulator.
# Never run this against a user's device: it removes the package in the emulator.
set -euo pipefail

old_apk="${1:?path to the previous APK required}"
new_apk="${2:?path to the current APK required}"
expected_page_size="${3:-}"
package=com.gf2227.dezhban

# The update probe uninstalls its own test package. Refuse physical devices.
serial=$(adb get-serialno | tr -d '\r')
is_emulator=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$is_emulator" != 1 ]]; then
    echo 'Refusing to run the uninstall test outside a disposable Android emulator.' >&2
    exit 1
fi

for file in "$old_apk" "$new_apk"; do
    test -s "$file"
done

sdk=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
page_size=$(adb shell getconf PAGE_SIZE 2>/dev/null | tr -d '\r' || true)
echo "Android API: $sdk; userspace page size: ${page_size:-unknown}."
if [[ -n "$expected_page_size" && "$page_size" != "$expected_page_size" ]]; then
    echo "The emulator page size was not the requested $expected_page_size."
    exit 1
fi
# Some older emulator images do not permit clearing every log buffer.
adb logcat -c 2>/dev/null || echo 'Log buffer could not be cleared; continuing.'

echo 'Fresh install of the previous APK:'
if old_result=$(adb install "$old_apk" 2>&1); then
    echo "$old_result"
    echo 'Attempting to update it with the current APK:'
    if update_result=$(adb install -r "$new_apk" 2>&1); then
        echo "$update_result"
        echo 'Update result: accepted.'
    else
        echo "$update_result"
        echo 'Update result: rejected; see the Android installer error above.'
    fi
    # This test always runs in a brand-new emulator, not on a physical device.
    adb uninstall "$package"
else
    echo "$old_result"
    echo 'Previous APK did not install on this emulator; continuing with the current APK.'
fi

echo 'Fresh install of the current APK:'
if new_result=$(adb install "$new_apk" 2>&1); then
    echo "$new_result"
else
    echo "$new_result"
    adb logcat -d -s PackageManager:V PackageInstaller:V installd:V | tail -80 || true
    exit 1
fi

echo 'Launching the freshly installed APK:'
if launch_result=$(adb shell am start -W -a android.intent.action.MAIN \
        -c android.intent.category.LAUNCHER -p "$package" 2>&1); then
    echo "$launch_result"
else
    echo "$launch_result"
    adb logcat -d -s AndroidRuntime:E Godot:V linker:V libc:E | tail -80 || true
    exit 1
fi
if ! grep -q 'Status: ok' <<< "$launch_result"; then
    adb logcat -d -s AndroidRuntime:E Godot:V linker:V libc:E | tail -80 || true
    exit 1
fi
sleep "${LAUNCH_WAIT_SECONDS:-12}"
if ! adb shell pidof "$package"; then
    echo 'The application process did not survive the initial launch.'
    adb logcat -d -s AndroidRuntime:E Godot:V linker:V libc:E | tail -80 || true
    exit 1
fi
echo 'Fresh installation and launcher startup succeeded.'
