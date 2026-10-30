#!/usr/bin/env bash
# Build only Godot 4.3 Android native libraries, not a Dezhban APK.
set -euo pipefail
arch="${1:?usage: build_godot43_android_16kb.sh arm64|x86_64 OUTPUT_DIR}"
out="${2:?output directory required}"
case "$arch" in
    arm64) abi=arm64-v8a ;;
    x86_64) abi=x86_64 ;;
    *) echo "Unsupported architecture: $arch" >&2; exit 2 ;;
esac
: "${ANDROID_HOME:?ANDROID_HOME must point to the Android SDK}"
: "${RUNNER_TEMP:?RUNNER_TEMP must be set}"
repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
out=$(mkdir -p "$out" && cd "$out" && pwd)
work="$RUNNER_TEMP/dezhban-godot43-$arch"
mkdir -p "$work"
archive="$work/godot.tar.gz"
# Godot 4.3 stable, commit 77dcf97d82cbfe4e4615475fa52ca03da645dbd8.
curl -fsSL --retry 3 'https://codeload.github.com/godotengine/godot/tar.gz/refs/tags/4.3-stable' -o "$archive"
echo "6a441d02957432d76343052296dc514b1b6bd72af920c9f2518b1ec3cb534874  $archive" | sha256sum -c -
tar -xzf "$archive" -C "$work"
source_dir="$work/godot-4.3-stable"
python3 "$repo_root/games/dejban/tools/patch_godot43_android_ndk.py" "$source_dir"
if [[ ! -d "$ANDROID_HOME/ndk/28.1.13356709" ]]; then
    "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" 'ndk;28.1.13356709'
fi
jobs=$(nproc)
if (( jobs > 4 )); then jobs=4; fi
(
    cd "$source_dir"
    scons -j "$jobs" platform=android target=template_debug arch="$arch" \
        generate_apk=no debug_symbols=no lto=none
)
lib_dir="$source_dir/platform/android/java/lib/libs/debug/$abi"
test -s "$lib_dir/libgodot_android.so"
test -s "$lib_dir/libc++_shared.so"
python3 "$repo_root/games/dejban/tools/verify_elf_16kb.py" \
    "$lib_dir/libgodot_android.so" "$lib_dir/libc++_shared.so"
cp "$lib_dir/libgodot_android.so" "$lib_dir/libc++_shared.so" "$out/"
echo "Built 16 KiB-aligned Godot 4.3 Android libraries for $abi."
