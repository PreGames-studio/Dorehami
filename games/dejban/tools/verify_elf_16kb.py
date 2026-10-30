#!/usr/bin/env python3
"""Fail unless every LOAD segment in each supplied ELF supports 16 KiB pages."""
from __future__ import annotations

from pathlib import Path
import subprocess
import sys

PAGE_SIZE = 16384


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
        if (offset - address) % PAGE_SIZE:
            raise ValueError(f"LOAD offset {offset:#x} and address {address:#x} are not 16 KiB-congruent")


def main() -> int:
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} ELF [ELF ...]", file=sys.stderr)
        return 2
    for name in sys.argv[1:]:
        path = Path(name)
        try:
            output = subprocess.run(
                ["readelf", "-lW", str(path)], check=True, capture_output=True, text=True
            ).stdout
            segments = load_segments(output)
            validate_segments(segments)
        except (OSError, subprocess.CalledProcessError, ValueError) as error:
            print(f"{path}: 16 KiB ELF validation failed: {error}", file=sys.stderr)
            return 1
        print(f"{path}: {len(segments)} LOAD segments support 16 KiB pages")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
