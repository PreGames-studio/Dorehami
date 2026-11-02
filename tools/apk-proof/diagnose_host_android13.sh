#!/usr/bin/env bash
# Run only on a disposable emulator. Diagnose an existing APK, never export one.
set -u
apk=${1:?an existing APK is required}
serial=$(adb get-serialno | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
    echo 'Refusing to run diagnostic installation outside an emulator.' >&2
    exit 1
fi
printf 'Android API: '; adb shell getprop ro.build.version.sdk
printf 'Supported ABIs: '; adb shell getprop ro.product.cpu.abilist
printf 'APK SHA256: '; sha256sum "$apk"
printf 'Fresh install via the Android package manager (no -t or ABI override):\n'
adb_result=$(adb install "$apk" 2>&1)
adb_status=$?
printf '%s\n' "$adb_result"
if [ "$adb_status" -eq 0 ]; then
    echo 'Package manager accepted the exact file; removing it only from the disposable emulator before the UI test.'
    adb uninstall ir.dorehami.app || exit 1
else
    echo 'The package manager rejected the file on this emulator; check its ABI support before comparing it to a phone.'
fi
# Reuse the UI probe for the host package, leaving the existing Dezhban probe unchanged.
sed 's/^package=com.gf2227.dezhban$/package=ir.dorehami.app/' games/dejban/tools/diagnose_apk_installer_ui.sh > /tmp/host-installer.sh
bash /tmp/host-installer.sh "$apk"
