#!/usr/bin/env python3
"""Fail unless every LOAD segment in each supplied ELF supports 16 KiB pages."""
from __future__ import annotations

import argparse
from pathlib import Path
import struct
import subprocess
import sys

PAGE_SIZE = 16384
# ELF e_machine values for the 64-bit Android ABIs supported by this build.
ABI_MACHINES = {"arm64-v8a": 183, "x86_64": 62}


def validate_elf_identity(path: Path, abi: str) -> None:
    """Reject 32-bit, non-shared, or wrongly named architecture binaries."""
    if abi not in ABI_MACHINES:
        raise ValueError(f"Unsupported Android ABI: {abi}")
    with path.open("rb") as file:
        header = file.read(24)
    if len(header) != 24:
        raise ValueError("ELF header is truncated")
    ident, elf_type, machine, version = struct.unpack("<16sHHI", header)
    if ident[:4] != b"\x7fELF" or ident[4:7] != b"\x02\x01\x01" or version != 1:
        raise ValueError("Expected a little-endian ELF64 binary")
    if elf_type != 3:
        raise ValueError("Expected an ELF shared object (ET_DYN)")
    if machine != ABI_MACHINES[abi]:
        raise ValueError(f"Incorrect ELF architecture for {abi}: e_machine={machine}")


def load_segments(output: str) -> list[tuple[int, int, int]]:
    segments = []
    for line in output.splitlines():
        fields = line.split()
        if not fields or fields[0] != "LOAD":
            continue
        if len(fields) < 7:
            raise ValueError(f"Invalid readelf LOAD line: {line}")
        try:
            offset, address, alignment = int(fields[1], 16), int(fields[2], 16), int(fields[-1], 16)
        except ValueError as error:
            raise ValueError(f"Invalid readelf LOAD line: {line}") from error
        segments.append((offset, address, alignment))
    if not segments:
        raise ValueError("No ELF LOAD segments were found")
    return segments


def validate_segments(segments: list[tuple[int, int, int]]) -> None:
    for offset, address, alignment in segments:
        if alignment < PAGE_SIZE or alignment & (alignment - 1):
            raise ValueError(f"LOAD alignment {alignment:#x} is not a power of two of at least 16 KiB")
        if (offset - address) % alignment:
            raise ValueError(f"LOAD offset {offset:#x} and address {address:#x} are not congruent modulo {alignment:#x}")


def validate_elf(path: Path, abi: str | None = None) -> int:
    if abi is not None:
        validate_elf_identity(path, abi)
    output = subprocess.run(
        ["readelf", "-lW", str(path)], check=True, capture_output=True, text=True
    ).stdout
    segments = load_segments(output)
    validate_segments(segments)
    return len(segments)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--abi", choices=ABI_MACHINES, help="also verify the expected ELF architecture")
    parser.add_argument("elf", nargs="+", type=Path)
    args = parser.parse_args()
    for path in args.elf:
        try:
            segment_count = validate_elf(path, args.abi)
        except (OSError, subprocess.CalledProcessError, ValueError) as error:
            print(f"{path}: 16 KiB ELF validation failed: {error}", file=sys.stderr)
            return 1
        print(f"{path}: {segment_count} LOAD segments support 16 KiB pages" + (f" ({args.abi})" if args.abi else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
