#!/usr/bin/env python3
"""Reports which `ir.dorehami.*` classes actually made it into a built APK.

An Android app can compile against a game module and still miss its classes at runtime — that is
exactly the chess crash: `NoClassDefFoundError: ir.dorehami.games.chess.ChessModule` while
`ir.dorehami.games.chess.ui.*` is present. This script reads the DEX files inside the APK and lists
the class descriptors per package, so a packaging mistake is visible in seconds instead of only on a
device.

Usage: inspect_apk.py <apk> [report-file]
"""
from __future__ import annotations

import re
import sys
import zipfile

PACKAGE = re.compile(rb"L(ir/dorehami/[A-Za-z0-9_/$]+);")

# Classes that must be inside the APK for the app to work (one per game + the shared core).
REQUIRED = {
    "core/gamesdk": "ir/dorehami/gamesdk/GameEngine",
    "core/protocol": "ir/dorehami/protocol/PlayerId",
    "core/uikit": "ir/dorehami/uikit/FaDigits",
    "chess rules": "ir/dorehami/games/chess/ChessModule",
    "chess rules (board)": "ir/dorehami/games/chess/Board",
    "chess ui": "ir.dorehami/games.chess.ui.ChessScreen".replace(".", "/"),
    "hokm rules": "ir/dorehami/games/hokm/HokmModule",
    "hokm ui": "ir/dorehami/games/hokm/ui/HokmScreen",
    "pantohub rules": "ir/dorehami/games/pantohub/PantohubEngine",
    "pantohub ui": "ir/dorehami/games/pantohub/ui/PantohubScreen",
}


def dex_class_names(apk: str) -> set[str]:
    """Every `ir/dorehami/...` class descriptor found in the APK's DEX files."""
    names: set[str] = set()
    with zipfile.ZipFile(apk) as zf:
        for entry in zf.namelist():
            if not re.fullmatch(r"classes\d*\.dex", entry):
                continue
            data = zf.read(entry)
            for match in PACKAGE.finditer(data):
                names.add(match.group(1).decode("ascii"))
    return names


def main() -> int:
    apk = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
    report_path = sys.argv[2] if len(sys.argv) > 2 else ""

    names = dex_class_names(apk)
    lines = [f"APK: {apk}", f"ir.dorehami.* classes inside the APK: {len(names)}", ""]

    lines.append("== required classes ==")
    missing = []
    for label, cls in REQUIRED.items():
        present = cls in names
        lines.append(f"{'ok   ' if present else 'MISS '}{label}: {cls}")
        if not present:
            missing.append(f"{label} ({cls})")

    lines.append("")
    lines.append("== classes per package ==")
    groups: dict[str, int] = {}
    for name in names:
        parts = name.split("/")
        key = "/".join(parts[:4]) if len(parts) > 4 else "/".join(parts[:-1])
        groups[key] = groups.get(key, 0) + 1
    for key in sorted(groups):
        lines.append(f"{groups[key]:5d}  {key}")

    lines.append("")
    lines.append("== every chess class in the APK ==")
    chess = sorted(name for name in names if name.startswith("ir/dorehami/games/chess"))
    lines.extend(chess or ["(none — the whole chess module is missing!)"])

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
