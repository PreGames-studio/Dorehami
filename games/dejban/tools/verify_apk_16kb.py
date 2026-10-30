#!/usr/bin/env python3
"""Check the APK's 64-bit ELF segments and native-library ZIP packaging."""
from __future__ import annotations

from collections import Counter
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZIP_DEFLATED, ZIP_STORED, ZipFile

from verify_elf_16kb import PAGE_SIZE, validate_elf

ABIS = ("arm64-v8a", "x86_64")
REQUIRED = ("libgodot_android.so", "libc++_shared.so")


def zip_data_offset(raw, item) -> int:
    raw.seek(item.header_offset)
    header = raw.read(30)
    if len(header) != 30 or header[:4] != b"PK\x03\x04":
        raise ValueError(f"Invalid local ZIP header for {item.filename}")
    name_size, extra_size = struct.unpack("<HH", header[26:30])
    return item.header_offset + 30 + name_size + extra_size


def check_apk(apk: Path, aapt: str) -> None:
    output = subprocess.run(
        [aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml"],
        check=True, capture_output=True, text=True,
    ).stdout
    compressed = False
    with tempfile.TemporaryDirectory() as temp, ZipFile(apk) as archive, apk.open("rb") as raw:
        items = archive.infolist()
        counts = Counter(item.filename for item in items)
        duplicates = [name for name, count in counts.items() if count != 1]
        if duplicates:
            raise ValueError(f"Duplicate ZIP entries: {duplicates}")
        for abi in ABIS:
            for name in REQUIRED:
                required = f"lib/{abi}/{name}"
                if required not in counts:
                    raise ValueError(f"Missing 64-bit native library: {required}")
        files = [
            item for item in items
            if any(item.filename.startswith(f"lib/{abi}/") for abi in ABIS)
            and item.filename.endswith(".so")
        ]
        if not files:
            raise ValueError("No 64-bit native libraries found")
        for i, item in enumerate(files):
            if item.compress_type == ZIP_STORED:
                offset = zip_data_offset(raw, item)
                if offset % PAGE_SIZE:
                    raise ValueError(f"Uncompressed {item.filename} is not 16 KiB ZIP-aligned")
            elif item.compress_type == ZIP_DEFLATED:
                compressed = True
            else:
                raise ValueError(f"Unsupported compression for {item.filename}")
            library = Path(temp) / f"{i}.so"
            with archive.open(item) as source, library.open("wb") as destination:
                shutil.copyfileobj(source, destination)
            abi = item.filename.split("/")[1]
            try:
                validate_elf(library, abi)
            except (OSError, subprocess.CalledProcessError, ValueError) as error:
                raise ValueError(f"{item.filename}: {error}") from error
            print(f"{item.filename}: {abi} identity and 16 KiB ELF check passed")
    if compressed:
        extract_line = next((line for line in output.splitlines() if "android:extractNativeLibs" in line), "")
        if "0xffffffff" not in extract_line.lower() and "=true" not in extract_line.lower():
            raise ValueError("Compressed native libraries require android:extractNativeLibs=true")
        print("Compressed libraries will be extracted at install time.")


def main() -> int:
    if len(sys.argv) != 3:
        print(f"Usage: {sys.argv[0]} APK PATH_TO_AAPT", file=sys.stderr)
        return 2
    try:
        check_apk(Path(sys.argv[1]), sys.argv[2])
    except (BadZipFile, OSError, subprocess.CalledProcessError, ValueError) as error:
        print(f"APK 16 KiB validation failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
