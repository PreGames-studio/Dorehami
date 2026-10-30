#!/usr/bin/env python3
"""Prepare the exact Godot 4.3 Android source for NDK r28.

The 4.3 editor and engine APIs stay unchanged. This adapts the old NDK path
and links Android libraries with 16 KiB-compatible LOAD segments, following
Godot's upstream NDK r27/r28 changes (PRs 105611 and 106358).
"""
from pathlib import Path
import sys


def replace_once(path: Path, before: str, after: str) -> None:
    contents = path.read_text(encoding="utf-8")
    count = contents.count(before)
    if count != 1:
        raise SystemExit(f"Unexpected Godot 4.3 source in {path}: found {count} matches")
    path.write_text(contents.replace(before, after, 1), encoding="utf-8")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_godot43_android_ndk.py GODOT_SOURCE_DIRECTORY")
    root = Path(sys.argv[1]).resolve()
    detect = root / "platform/android/detect.py"
    scsub = root / "platform/android/SCsub"

    replace_once(detect, 'return "23.2.8568313"', 'return "28.1.13356709"')
    replace_once(
        detect,
        '    env.Append(LINKFLAGS="-Wl,--gc-sections -Wl,--no-undefined -Wl,-z,now".split())\n',
        '    env.Append(LINKFLAGS="-Wl,--gc-sections -Wl,--no-undefined -Wl,-z,now".split())\n'
        '    env.Append(LINKFLAGS=["-Wl,-z,max-page-size=16384"])\n',
    )
    replace_once(
        scsub,
        '    stl_lib_path = (\n'
        '        str(env["ANDROID_NDK_ROOT"]) + "/sources/cxx-stl/llvm-libc++/libs/" + lib_arch_dir + "/libc++_shared.so"\n'
        '    )\n',
        '    if not sys.platform.startswith("linux"):\n'
        '        raise RuntimeError("The Dezhban Android template builder requires Linux")\n'
        '    stl_triples = {\n'
        '        "armeabi-v7a": "arm-linux-androideabi",\n'
        '        "arm64-v8a": "aarch64-linux-android",\n'
        '        "x86": "i686-linux-android",\n'
        '        "x86_64": "x86_64-linux-android",\n'
        '    }\n'
        '    stl_lib_path = (str(env["ANDROID_NDK_ROOT"]) + "/toolchains/llvm/prebuilt/linux-x86_64/"\n'
        '        + "sysroot/usr/lib/" + stl_triples[lib_arch_dir] + "/libc++_shared.so")\n',
    )
    print("Godot 4.3 source patched for the Android NDK r28 and 16 KiB alignment.")


if __name__ == "__main__":
    main()
