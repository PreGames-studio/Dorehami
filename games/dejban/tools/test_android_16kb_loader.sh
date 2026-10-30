#!/usr/bin/env bash
# Compare a published Godot library with a rebuilt one, without making a game APK.
set -euo pipefail

if [ "$#" -ne 5 ]; then
  echo "Usage: $0 PROBE OLD_LIB_DIR NEW_LIB_DIR EXPECTED_PAGE_SIZE x86_64|arm64-v8a" >&2
  exit 2
fi
probe=$1
old_lib_dir=$2
new_lib_dir=$3
expected_page_size=$4
probe_abi=$5
if [[ "$expected_page_size" != 4096 && "$expected_page_size" != 16384 ]]; then
  echo "Expected page size must be 4096 or 16384." >&2
  exit 2
fi
if [[ "$probe_abi" != x86_64 && "$probe_abi" != arm64-v8a ]]; then
  echo "Probe ABI must be x86_64 or arm64-v8a." >&2
  exit 2
fi
for file in "$probe" "$old_lib_dir/libgodot_android.so" "$old_lib_dir/libc++_shared.so" \
  "$new_lib_dir/libgodot_android.so" "$new_lib_dir/libc++_shared.so"; do
  test -f "$file" || { echo "Missing file: $file" >&2; exit 2; }
done

serial=$(adb get-serialno)
qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
page_size=$(adb shell getconf PAGESIZE | tr -d '\r')
if [[ "$serial" != emulator-* || "$qemu" != 1 || "$abi" != x86_64 || "$page_size" != "$expected_page_size" ]]; then
  echo "Refusing to run outside the requested x86_64 Android emulator: serial=$serial qemu=$qemu abi=$abi page_size=$page_size expected=$expected_page_size" >&2
  exit 2
fi
if [[ "$probe_abi" == arm64-v8a ]]; then
  supported_abis=$(adb shell getprop ro.product.cpu.abilist64 | tr -d '\r')
  bridge_exec=$(adb shell getprop ro.enable.native.bridge.exec | tr -d '\r')
  if [[ ",$supported_abis," != *,arm64-v8a,* || "$bridge_exec" != 1 ]]; then
    echo "This emulator cannot run translated ARM64 executables: abis=$supported_abis bridge=$bridge_exec" >&2
    exit 2
  fi
  echo 'Testing ARM64 through the emulator translation layer, not on physical ARM hardware.'
else
  echo 'Testing native x86_64.'
fi

remote=/data/local/tmp/dezhban-16kb-loader-test
adb shell rm -rf "$remote"
adb shell mkdir -p "$remote/old" "$remote/new"
adb push "$probe" "$remote/probe" >/dev/null
for lib in libgodot_android.so libc++_shared.so; do
  adb push "$old_lib_dir/$lib" "$remote/old/$lib" >/dev/null
  adb push "$new_lib_dir/$lib" "$remote/new/$lib" >/dev/null
done
adb shell chmod 755 "$remote/probe"

set +e
old_output=$(adb shell "cd $remote/old && LD_LIBRARY_PATH=$remote/old $remote/probe ./libgodot_android.so $expected_page_size" 2>&1)
old_status=$?
set -e
printf 'Published library load (exit %s):\n%s\n' "$old_status" "$old_output"
if [[ "$expected_page_size" == 4096 && "$old_status" -ne 0 ]]; then
  echo 'The original library failed the 4 KiB control test.' >&2
  exit 1
elif [[ "$expected_page_size" == 16384 && "$old_status" -eq 0 ]]; then
  echo 'Note: the negative control loaded; this does not establish its game runtime compatibility.'
fi
printf '\nRebuilt library load:\n'
adb shell "cd $remote/new && LD_LIBRARY_PATH=$remote/new $remote/probe ./libgodot_android.so $expected_page_size"
