#!/usr/bin/env bash
# Compare a published Godot library with a rebuilt one, without making a game APK.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 X86_64_PROBE OLD_LIB_DIR NEW_LIB_DIR" >&2
  exit 2
fi
probe=$1
old_lib_dir=$2
new_lib_dir=$3
for file in "$probe" "$old_lib_dir/libgodot_android.so" "$old_lib_dir/libc++_shared.so" \
  "$new_lib_dir/libgodot_android.so" "$new_lib_dir/libc++_shared.so"; do
  test -f "$file" || { echo "Missing file: $file" >&2; exit 2; }
done

serial=$(adb get-serialno)
qemu=$(adb shell getprop ro.kernel.qemu | tr -d '\r')
abi=$(adb shell getprop ro.product.cpu.abi | tr -d '\r')
page_size=$(adb shell getconf PAGESIZE | tr -d '\r')
if [[ "$serial" != emulator-* || "$qemu" != 1 || "$abi" != x86_64 || "$page_size" != 16384 ]]; then
  echo "Refusing to run outside an x86_64 16 KiB Android emulator: serial=$serial qemu=$qemu abi=$abi page_size=$page_size" >&2
  exit 2
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
old_output=$(adb shell "cd $remote/old && LD_LIBRARY_PATH=$remote/old $remote/probe ./libgodot_android.so" 2>&1)
old_status=$?
set -e
printf 'Published library load (exit %s):\n%s\n' "$old_status" "$old_output"
if [ "$old_status" -eq 0 ]; then
  echo 'Note: the negative control unexpectedly loaded; this result does not establish its game runtime compatibility.'
fi
printf '\nRebuilt library load:\n'
adb shell "cd $remote/new && LD_LIBRARY_PATH=$remote/new $remote/probe ./libgodot_android.so"
