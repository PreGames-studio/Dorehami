#!/usr/bin/env python3
"""Reports which `ir.dorehami.*` classes are really *defined* inside a built APK.

An Android app can compile against a game module and still miss its classes at runtime — that is
exactly the chess crash: `NoClassDefFoundError: ir.dorehami.games.chess.ChessModule` while
`ir.dorehami.games.chess.ui.*` is present. A plain string search over the DEX files is *not* enough
to see it: the name `Lir/dorehami/games/chess/ChessModule;` also appears in the DEX as a mere
reference from the UI code. So this script parses the DEX `class_defs` table and reports the classes
that are actually packaged.

Usage: inspect_apk.py <apk> [report-file]
"""
from __future__ import annotations

import re
import struct
import sys
import zipfile

# One class that must be inside the APK for the app to work, per module.
REQUIRED = {
    "core/gamesdk": "Lir/dorehami/gamesdk/GameEngine;",
    "core/protocol": "Lir/dorehami/protocol/PlayerId;",
    "core/session": "Lir/dorehami/session/SessionClient;",
    "core/uikit (FaDigits)": "Lir/dorehami/uikit/FaDigitsKt;",
    "core/uikit (page frame)": "Lir/dorehami/core/uikit/DorehamiPageFrameKt;",
    "chess rules (ChessModule)": "Lir/dorehami/games/chess/ChessModule;",
    "chess rules (Board)": "Lir/dorehami/games/chess/Board;",
    "chess rules (ChessBot)": "Lir/dorehami/games/chess/ChessBot;",
    "chess rules (ChessSession)": "Lir/dorehami/games/chess/ChessSession;",
    "chess ui": "Lir/dorehami/games/chess/ui/ChessScreenKt;",
    "hokm rules (HokmModule)": "Lir/dorehami/games/hokm/HokmModule;",
    "hokm rules (HokmSession)": "Lir/dorehami/games/hokm/HokmSession;",
    "hokm ui": "Lir/dorehami/games/hokm/ui/HokmScreenKt;",
    "pantohub rules (engine)": "Lir/dorehami/games/pantohub/PantohubEngine;",
    "pantohub rules (bank)": "Lir/dorehami/games/pantohub/WordBankRules;",
    "pantohub ui": "Lir/dorehami/games/pantohub/ui/PantohubScreenKt;",
}

PACKAGE_REF = re.compile(rb"L(ir/dorehami/[A-Za-z0-9_/$]+);")


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7


class Dex:
    """Minimal DEX reader: the class descriptors in the `class_defs` table."""

    def __init__(self, data: bytes, name: str):
        self.data = data
        self.name = name
        if data[:4] != b"dex\n":
            raise ValueError("not a dex file")

    def _u32(self, offset: int) -> int:
        return struct.unpack_from("<I", self.data, offset)[0]

    def string(self, index: int) -> str:
        offset = self._u32(self._u32(0x3C) + 4 * index)
        _, offset = _uleb128(self.data, offset)
        end = self.data.index(b"\x00", offset)
        return self.data[offset:end].decode("utf-8", "replace")

    def type_descriptor(self, index: int) -> str:
        string_index = self._u32(self._u32(0x44) + 4 * index)
        return self.string(string_index)

    def defined_classes(self) -> list[str]:
        size, offset = self._u32(0x60), self._u32(0x64)
        out = []
        for i in range(size):
            class_index = self._u32(offset + 32 * i)
            out.append(self.type_descriptor(class_index))
        return out

    def referenced_classes(self) -> set[str]:
        return {m.group(1).decode("ascii") for m in PACKAGE_REF.finditer(self.data)}


def read_apk(apk: str) -> list[Dex]:
    dexes = []
    with zipfile.ZipFile(apk) as zf:
        names = sorted(n for n in zf.namelist() if re.fullmatch(r"classes\d*\.dex", n))
        for name in names:
            dexes.append(Dex(zf.read(name), name))
    return dexes


def _self_test() -> None:
    """Checks the DEX reader against a hand-built dex file (guards against bit-rot in offsets)."""
    import struct as _struct

    descriptors = ["Lir/dorehami/games/chess/ChessModule;", "Lir/dorehami/games/chess/Board;"]

    def build_dex(names: list[str]) -> bytes:
        count = len(names)
        string_ids_off, type_ids_off = 0x70, 0x70 + 4 * count
        class_defs_off = type_ids_off + 4 * count
        data_off = class_defs_off + 32 * count
        data = bytearray()
        string_id_values = []
        for name in names:
            encoded = name.encode()
            string_id_values.append(data_off + len(data))
            data += bytes([len(encoded)]) + encoded + b"\x00"
        out = bytearray(0x70 + 4 * count + 4 * count + 32 * count)
        out[0:8] = b"dex\n035\x00"
        _struct.pack_into("<I", out, 0x38, count)
        _struct.pack_into("<I", out, 0x3C, string_ids_off)
        _struct.pack_into("<I", out, 0x40, count)
        _struct.pack_into("<I", out, 0x44, type_ids_off)
        _struct.pack_into("<I", out, 0x60, count)
        _struct.pack_into("<I", out, 0x64, class_defs_off)
        _struct.pack_into("<" + "I" * count, out, string_ids_off, *string_id_values)
        _struct.pack_into("<" + "I" * count, out, type_ids_off, *range(count))
        for i in range(count):
            _struct.pack_into("<I", out, class_defs_off + 32 * i, i)
        return bytes(out) + bytes(data)

    assert Dex(build_dex(descriptors), "classes.dex").defined_classes() == descriptors


def main() -> int:
    if "--self-test" in sys.argv:
        _self_test()
        print("self-test ok")
        return 0
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
    report_path = sys.argv[2] if len(sys.argv) > 2 else ""

    dexes = read_apk(apk)
    defined: set[str] = set()
    referenced: set[str] = set()
    per_dex: dict[str, list[str]] = {}
    for dex in dexes:
        classes = dex.defined_classes()
        per_dex[dex.name] = classes
        defined.update(classes)
        referenced.update(dex.referenced_classes())

    lines = [f"APK: {apk}", f"dex files: {len(dexes)}", f"ir.dorehami classes defined: {len(defined)}", ""]

    lines.append("== required classes (defined in the APK) ==")
    missing = []
    for label, descriptor in REQUIRED.items():
        if descriptor in defined:
            lines.append(f"ok   {label}: {descriptor}")
        elif descriptor in referenced:
            lines.append(f"MISS {label}: {descriptor}  <-- referenced but NOT defined!")
            missing.append(f"{label} ({descriptor})")
        else:
            lines.append(f"MISS {label}: {descriptor}")
            missing.append(f"{label} ({descriptor})")

    lines.append("")
    lines.append("== classes per dex, grouped by package ==")
    for dex_name, classes in per_dex.items():
        groups: dict[str, int] = {}
        for descriptor in classes:
            parts = descriptor.strip("L;").split("/")
            key = "/".join(parts[:4]) if len(parts) > 4 else "/".join(parts[:-1])
            groups[key] = groups.get(key, 0) + 1
        lines.append(f"-- {dex_name}: {len(classes)} classes")
        for key in sorted(groups):
            lines.append(f"     {groups[key]:5d}  {key}")

    lines.append("")
    lines.append("== every chess descriptor that is defined vs only referenced ==")
    chess_defined = sorted(d for d in defined if d.startswith("Lir/dorehami/games/chess"))
    chess_referenced = sorted(r for r in referenced if r.startswith("ir/dorehami/games/chess"))
    lines.append(f"defined ({len(chess_defined)}):")
    lines.extend("  " + d for d in chess_defined)
    only_ref = [r for r in chess_referenced if f"L{r};" not in defined]
    lines.append(f"referenced but not defined ({len(only_ref)}):")
    lines.extend("  " + r for r in only_ref)

    text = "\n".join(lines)
    print(text)
    if report_path:
        with open(report_path, "w", encoding="utf-8") as fh:
            fh.write(text)
    if missing:
        print("\n::error::missing from the APK: " + ", ".join(missing))
        return 1
    print("\n::notice::every required class is packaged")
    return 0


if __name__ == "__main__":
    sys.exit(main())
