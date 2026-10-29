#!/usr/bin/env bash
# Exercise Android's visible PackageInstaller on a disposable emulator only.
set -euo pipefail
apk="${1:?path to existing APK required}"
package=com.gf2227.dezhban
serial=$(adb get-serialno | tr -d '\r')
is_emulator=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
if [[ ! "$serial" =~ ^emulator-[0-9]+$ || "$is_emulator" != 1 ]]; then
    echo 'Refusing to run installer diagnostics outside a disposable emulator.' >&2
    exit 1
fi
test -s "$apk"
if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
    echo 'Expected a fresh emulator without an existing Dezhban installation.' >&2
    exit 1
fi

log_errors() {
    adb logcat -d -v brief 2>/dev/null | \
        grep -Ei 'AndroidRuntime|PackageInstaller|InstallStart|InstallStaging|PackageParser|PackageManager|INSTALL_|FATAL EXCEPTION|Caused by:' | \
        tail -55 || true
}
dump_ui() {
    local target="$1"
    adb shell uiautomator dump /sdcard/dezhban-diagnostic-ui.xml >/dev/null 2>&1
    adb pull /sdcard/dezhban-diagnostic-ui.xml "$target" >/dev/null 2>&1
    python3 - "$target" <<'PY'
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
for node in root.iter('node'):
    text = (node.get('text') or '').strip()
    description = (node.get('content-desc') or '').strip()
    if text or description:
        print(f"UI: {text} {description} | {node.get('resource-id', '')} | {node.get('package', '')}")
PY
}
find_coordinates() {
    python3 - "$1" "$2" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
root = ET.parse(sys.argv[1]).getroot()
kind = sys.argv[2]
for node in root.iter('node'):
    text = (node.get('text') or '').strip().casefold()
    description = (node.get('content-desc') or '').strip().casefold()
    if kind == 'install':
        matches = text in ('install', 'update', 'reinstall')
    elif kind == 'file':
        matches = 'dezhban-install-diagnostic.apk' in (text + ' ' + description)
    elif kind == 'downloads':
        matches = text in ('downloads', 'download')
    else:
        matches = description in ('show roots', 'open navigation drawer', 'navigate up')
    if not matches or node.get('enabled') == 'false':
        continue
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}
tap_coordinates() {
    local coordinates="$1" x y
    read -r x y <<< "$coordinates"
    adb shell input tap "$x" "$y"
}

adb shell input keyevent 82 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb logcat -c 2>/dev/null || true
# The shell acts as a download source on this test emulator only; do not bypass
# the package signature or package manager checks.
adb shell appops set com.android.shell REQUEST_INSTALL_PACKAGES allow
name=dezhban-install-diagnostic.apk
adb push "$apk" "/sdcard/Download/$name" >/dev/null
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
    -d "file:///sdcard/Download/$name" >/dev/null 2>&1 || true

id=''
for _ in $(seq 1 30); do
    row=$(adb shell content query --uri content://media/external/downloads \
        --projection _id:_display_name 2>/dev/null | tr -d '\r' | \
        grep -F "_display_name=$name" | tail -1 || true)
    id=$(sed -n 's/.*_id=\([0-9][0-9]*\),.*/\1/p' <<< "$row")
    [[ -n "$id" ]] && break
    sleep 2
done
if [[ -z "$id" ]]; then
    echo 'The emulator did not index the downloaded APK; UI test is inconclusive.'
    log_errors
    exit 1
fi
uri="content://media/external/downloads/$id"
echo "Opening the APK using the Android package installer: $uri"
adb shell am start -W -a android.intent.action.VIEW -t application/vnd.android.package-archive \
    -d "$uri" --grant-read-uri-permission
sleep 8
dump_ui /tmp/dezhban-install-before.xml

coordinates=$(find_coordinates /tmp/dezhban-install-before.xml install)
if [[ -z "$coordinates" ]]; then
    # Some production images reject intents originating from the adb shell.
    # If so, actually tap the downloaded file in the system Files app instead.
    echo 'No Install button after the shell intent; trying the system Files app.'
    adb logcat -d -v brief -s InstallStart:V InstallStaging:V 2>/dev/null | tail -30 || true
    files_package=''
    for candidate in com.google.android.documentsui com.android.documentsui; do
        if adb shell pm path "$candidate" 2>/dev/null | tr -d '\r' | grep -q '^package:'; then
            files_package="$candidate"
            break
        fi
    done
    if [[ -n "$files_package" ]]; then
        adb shell appops set "$files_package" REQUEST_INSTALL_PACKAGES allow || true
        adb shell am start -W -n "$files_package/com.android.documentsui.files.FilesActivity" \
            -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
        sleep 8
        file_tapped=no
        for _ in 1 2 3 4; do
            dump_ui /tmp/dezhban-files.xml
            file_coordinates=$(find_coordinates /tmp/dezhban-files.xml file)
            if [[ -n "$file_coordinates" ]]; then
                echo 'Opening the downloaded APK in the system Files app.'
                tap_coordinates "$file_coordinates"
                file_tapped=yes
                sleep 8
                break
            fi
            nav_coordinates=$(find_coordinates /tmp/dezhban-files.xml downloads)
            if [[ -z "$nav_coordinates" ]]; then
                nav_coordinates=$(find_coordinates /tmp/dezhban-files.xml drawer)
            fi
            [[ -n "$nav_coordinates" ]] || break
            tap_coordinates "$nav_coordinates"
            sleep 4
        done
        if [[ "$file_tapped" == yes ]]; then
            dump_ui /tmp/dezhban-install-before.xml
            coordinates=$(find_coordinates /tmp/dezhban-install-before.xml install)
        fi
    fi
fi
if [[ -z "$coordinates" ]]; then
    echo 'No Install button was reached; visible installer test is inconclusive.'
    log_errors
    exit 1
fi
echo 'Pressing Install in the Android package installer.'
tap_coordinates "$coordinates"
installed=no
for _ in $(seq 1 45); do
    if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
        installed=yes
        break
    fi
    sleep 2
done
sleep 3
dump_ui /tmp/dezhban-install-after.xml || true
if [[ "$installed" != yes ]]; then
    echo 'The APK was not installed by the visible installer.'
    log_errors
    exit 1
fi
echo 'The exact published APK installed successfully through the visible Android installer.'
