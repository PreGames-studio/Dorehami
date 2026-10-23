#!/usr/bin/env python3
"""بررسی ارجاع‌های ساده در کد GDScript.

هرجا کدی چیزی مثل `Balance.HUD_HEIGHT` یا `Ui.fa_decimal(...)` بنویسد، این اسکریپت
بررسی می‌کند که آن عضو واقعاً در همان فایل تعریف شده باشد. خطاهایی مثل «ثابت
فراموش‌شده» یا «نام تابع اشتباه» را پیش از اجرای Godot پیدا می‌کند:

    python3 tools/check_refs.py
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_DIRS = ["src", "tests"]

# نام کلاس/خودبار → مسیر فایل
CLASS_FILES = {
    "Balance": "src/data/Balance.gd",
    "Weapons": "src/data/Weapons.gd",
    "Enemies": "src/data/Enemies.gd",
    "Maps": "src/data/Maps.gd",
    "GameMap": "src/data/GameMap.gd",
    "Assets": "src/data/Assets.gd",
    "Fx": "src/fx/Fx.gd",
    "Game": "src/autoload/Game.gd",
    "Audio": "src/autoload/Audio.gd",
    "Style": "src/autoload/Style.gd",
    "Ui": "src/autoload/Ui.gd",
    "BuildGuide": "src/game/BuildGuide.gd",
}

MEMBER_PATTERNS = [
    re.compile(r"^\s*(?:static\s+)?const\s+([A-Za-z_][A-Za-z0-9_]*)"),
    re.compile(r"^\s*(?:static\s+)?var\s+([A-Za-z_][A-Za-z0-9_]*)"),
    re.compile(r"^\s*(?:static\s+)?func\s+([A-Za-z_][A-Za-z0-9_]*)"),
    re.compile(r"^\s*signal\s+([A-Za-z_][A-Za-z0-9_]*)"),
]

# اعضایی که از Object/Node به ارث می‌رسند و در فایل خودمان نیستند
INHERITED = {
    "new", "call", "get", "set", "connect", "emit", "has_meta", "set_meta", "get_meta",
    "duplicate", "keys", "size", "is_empty", "has", "get_class", "free", "queue_free",
}


def declared_members(path: str) -> set[str]:
    members: set[str] = set()
    with open(path, encoding="utf-8") as handle:
        for line in handle:
            for pattern in MEMBER_PATTERNS:
                found = pattern.match(line)
                if found:
                    members.add(found.group(1))
    return members


def gd_files() -> list[str]:
    files: list[str] = []
    for top in SRC_DIRS:
        for root, _dirs, names in os.walk(os.path.join(ROOT, top)):
            for name in names:
                if name.endswith(".gd"):
                    files.append(os.path.join(root, name))
    return sorted(files)


def main() -> int:
    members = {name: declared_members(os.path.join(ROOT, path)) for name, path in CLASS_FILES.items()}
    problems: list[str] = []
    usage = re.compile(r"\b([A-Z][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)")
    for path in gd_files():
        with open(path, encoding="utf-8") as handle:
            for number, line in enumerate(handle, start=1):
                if line.lstrip().startswith("#"):
                    continue
                # داخل رشته‌ها را نادیده بگیر
                cleaned = re.sub(r'"[^"]*"', '""', line)
                for cls, member in usage.findall(cleaned):
                    if cls not in members or member in INHERITED:
                        continue
                    if member not in members[cls]:
                        problems.append("%s:%d  %s.%s" % (os.path.relpath(path, ROOT), number, cls, member))
    if problems:
        print("ارجاع‌های ناموجود:")
        for item in problems:
            print("  ✗", item)
        return 1
    print("همهٔ ارجاع‌ها به اعضای کلاس‌های پروژه درست است.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
