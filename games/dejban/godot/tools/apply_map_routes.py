#!/usr/bin/env python3
"""waypoints، pads و keep نقشه‌های ۱ تا ۱۲ را از فایل مسیرهای استخراج‌شده در Maps.gd می‌نویسد.

نقشه‌های ۱۳ تا ۱۵ و هر خط دیگری از فایل دست‌نخورده می‌ماند.

    python3 tools/apply_map_routes.py            # بازنویسی Maps.gd
    python3 tools/apply_map_routes.py --dry-run  # فقط نمایش تغییر
"""
from __future__ import annotations

import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_JSON = os.path.join(ROOT, "tools", "art_source", "maps_v2", "routes_extracted.json")
TARGET = os.path.join(ROOT, "src", "data", "Maps.gd")

TILE_X, TILE_Y = 64.0, 72.0
COLS, ROWS = 20, 10
N_MAPS = 12


def tile_coords(px: float, py: float) -> tuple[float, float]:
    return round(px / TILE_X, 2), round(py / TILE_Y, 2)


def tile_of(px: float, py: float) -> tuple[int, int]:
    x = min(max(int(px // TILE_X), 0), COLS - 1)
    y = min(max(int(py // TILE_Y), 0), ROWS - 1)
    return x, y


def fmt_vec(x: float, y: float) -> str:
    return "Vector2(%.2f, %.2f)" % (x, y)


def wrap(items: list[str], indent: str = "\t\t\t", per_line: int = 3) -> str:
    rows = []
    for i in range(0, len(items), per_line):
        rows.append("%s%s," % (indent, ", ".join(items[i:i + per_line])))
    return "\n".join(rows)


def replace_array(text: str, key: str, body: str) -> str:
    pattern = re.compile(r'("%s":\s*\[)[^\]]*(\],)' % re.escape(key))
    return pattern.sub(lambda m: m.group(1) + "\n" + body + "\n\t\t" + m.group(2), text, count=1)


def main() -> None:
    dry = "--dry-run" in sys.argv
    data = json.load(open(SRC_JSON, encoding="utf-8"))
    text = open(TARGET, encoding="utf-8").read()
    original = text

    for i in range(1, N_MAPS + 1):
        entry = data[str(i)]
        verts = [(float(x), float(y)) for x, y in entry["verts"]]
        pads = [(float(x), float(y)) for x, y in entry["pads"]]
        keep_px = verts[-1]
        keep_tile = tile_of(*keep_px)

        # --- پیدا کردن بلوک همین نقشه (تا انتهای دیکشنری مرتبط)
        anchor = '"bg": "maps/map_%02d"' % i
        at = text.find(anchor)
        if at < 0:
            raise SystemExit("بلوک %s پیدا نشد" % anchor)
        end = text.find('\n\t},\n', at)
        if end < 0:
            raise SystemExit("انتهای بلوک %s پیدا نشد" % anchor)
        end += len('\n\t},\n')
        block = text[at:end]

        # --- keep
        block = re.sub(r'"keep":\s*Vector2i\(\d+,\s*\d+\)',
                       '"keep": Vector2i(%d, %d)' % keep_tile, block, count=1)

        # --- waypoints
        wps = [fmt_vec(*tile_coords(x, y)) for x, y in verts]
        block = replace_array(block, "waypoints", wrap(wps))

        # --- pads
        pads_g = [fmt_vec(*tile_coords(x, y)) for x, y in pads]
        block = replace_array(block, "pads", wrap(pads_g))

        text = text[:at] + block + text[end:]

    if dry:
        import difflib
        diff = difflib.unified_diff(original.splitlines(), text.splitlines(),
                                    "Maps.gd (before)", "Maps.gd (after)", lineterm="", n=1)
        print("\n".join(list(diff)[:120]))
        print("\n… خلاصه: %d خط قبل، %d خط بعد" % (len(original.splitlines()), len(text.splitlines())))
        return

    open(TARGET, "w", encoding="utf-8").write(text)
    print("نوشته شد: %s" % os.path.relpath(TARGET, ROOT))
    print("تغییر خطوط: %d → %d" % (len(original.splitlines()), len(text.splitlines())))


if __name__ == "__main__":
    main()
