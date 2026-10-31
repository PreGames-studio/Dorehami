#!/usr/bin/env python3
"""Replace only the 64-bit native libraries in a Godot 4.3 Android template."""
from __future__ import annotations

from collections import Counter
from copy import copy
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from zipfile import BadZipFile, ZipFile

from verify_elf_16kb import validate_elf

LIBRARIES = ("libgodot_android.so", "libc++_shared.so")
ABIS = ("arm64-v8a", "x86_64")


def replace_libraries(template: Path, libraries: Path, output: Path) -> None:
    replacements = {
        f"lib/{abi}/{name}": libraries / abi / name
        for abi in ABIS
        for name in LIBRARIES
    }
    if output.resolve() in {template.resolve(), *(path.resolve() for path in replacements.values())}:
        raise ValueError("The output must differ from the template and replacement libraries")
    for name, file in replacements.items():
        if not file.is_file() or file.stat().st_size == 0:
            raise ValueError(f"Missing rebuilt template library: {file}")
        abi = name.split("/")[1]
        try:
            validate_elf(file, abi)
        except (OSError, subprocess.CalledProcessError, ValueError) as error:
            raise ValueError(f"Invalid rebuilt library {file}: {error}") from error

    with ZipFile(template) as source:
        items = source.infolist()
        counts = Counter(item.filename for item in items)
        duplicates = [name for name, count in counts.items() if count != 1]
        if duplicates:
            raise ValueError(f"Duplicate template entries: {duplicates}")
        for name in replacements:
            if name not in counts:
                raise ValueError(f"Missing template entry: {name}")
        unexpected = [
            name for name in counts
            if any(name.startswith(f"lib/{abi}/") for abi in ABIS)
            and name.endswith(".so") and name not in replacements
        ]
        if unexpected:
            raise ValueError(f"Unexpected 64-bit template libraries: {unexpected}")

        # Do not damage an existing output if the input is corrupt or a copy fails.
        with tempfile.NamedTemporaryFile(dir=output.parent, prefix=".dezhban-template-", delete=False) as temporary:
            temp_path = Path(temporary.name)
        try:
            with ZipFile(temp_path, "w", allowZip64=True) as result:
                result.comment = source.comment
                for item in items:
                    with result.open(copy(item), "w") as destination:
                        if item.filename in replacements:
                            with replacements[item.filename].open("rb") as replacement:
                                shutil.copyfileobj(replacement, destination)
                        else:
                            with source.open(item) as original:
                                shutil.copyfileobj(original, destination)
            temp_path.replace(output)
        finally:
            temp_path.unlink(missing_ok=True)
    print("Updated Godot 4.3 template with validated ARM64 and x86_64 libraries.")


def main() -> int:
    if len(sys.argv) != 4:
        print(f"Usage: {sys.argv[0]} TEMPLATE.apk LIBRARY_DIR OUTPUT.apk", file=sys.stderr)
        return 2
    template, libraries, output = map(Path, sys.argv[1:])
    try:
        replace_libraries(template, libraries, output)
    except (BadZipFile, OSError, ValueError) as error:
        print(f"Could not prepare the template: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
