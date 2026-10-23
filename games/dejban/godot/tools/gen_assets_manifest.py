#!/usr/bin/env python3
"""فهرست دارایی‌ها را از پوشهٔ assets می‌سازد و src/data/Assets.gd را می‌نویسد.

هر کلید یک نام منطقی («پوشه/نام») است و به مسیر res:// نگاشت می‌شود؛ کد بازی
فقط با همین کلیدها کار می‌کند، پس جایگزینی هنر بدون تغییر کد ممکن است:

    python3 tools/gen_assets_manifest.py

پک‌های ایزومتریک ۱۲۸×۱۲۸ (شبکهٔ «ردیف = جهت، ستون = فریم») کلیدِ دستهٔ خودشان را
می‌گیرند تا با هنر قدیمی قاطی نشوند (ISO_PACKS)؛ مثلاً
assets/sprites/units/iso_tank/tank_move.png ← «units_iso_tank/tank_move».
"""

from __future__ import annotations

import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(ROOT, "assets", "sprites")
ICONS = os.path.join(ROOT, "assets", "icons")
SOUNDS = os.path.join(ROOT, "assets", "sounds")
TARGET = os.path.join(ROOT, "src", "data", "Assets.gd")

# پوشهٔ پک (نسبت به assets/sprites) ← دستهٔ کلید در مانیفست. فایل‌ها بی‌تغییرِ نام کپی
# می‌شوند و کلیدشان «دسته/نامِ فایل» است؛ IsoSheet.gd همین کلیدها را برش می‌دهد.
ISO_PACKS = {
    "towers_iso": "towers_iso",
    "units/iso_tank": "units_iso_tank",
    "units/iso_tank_armored": "units_iso_tank_armored",
    "units/iso_tank_heavy": "units_iso_tank_heavy",
}

# پشتوانهٔ هر دستهٔ ایزومتریک یک شیت با همان شبکهٔ ۸ ردیفی است تا برش جهت‌ها
# حتی با کلید گم‌شده هم درست بماند.
ISO_FALLBACKS = {
    "towers_iso": "towers_iso/machinegun_idle",
    "units_iso_tank": "units_iso_tank/tank_idle",
    "units_iso_tank_armored": "units_iso_tank_armored/tank_armored_idle",
    "units_iso_tank_heavy": "units_iso_tank_heavy/tank_heavy_idle",
}

HEADER = '''## این فایل با tools/gen_assets_manifest.py ساخته می‌شود؛ دستی ویرایش نکنید.
##
## MANIFEST کلید منطقی هر دارایی («پوشه/نام») را به مسیر واقعی فایل نگاشت می‌کند.
## FALLBACKS هم پشتوانهٔ دسته‌هاست: اگر کلیدی در ساخت نبود، نزدیک‌ترین هنر همان
## خانواده استفاده می‌شود تا میدان نبرد هرگز جای خالی یا رنگ سرخ نگیرد؛ تست
## بدون رابط هم کمبود کلید را با صدا اعلام می‌کند.
class_name Assets
extends RefCounted

const MANIFEST := {
__MANIFEST__
}

const FALLBACKS := {
__FALLBACKS__
}

const SOUND_MANIFEST := {
__SOUNDS__
}

static var _cache: Dictionary = {}


## بافت یک کلید را برمی‌گرداند (با حافظهٔ نهان) و در صورت نبودِ کلید هشدار می‌دهد.
static func tex(key: String) -> Texture2D:
\tif _cache.has(key):
\t\treturn _cache[key]
\tvar path: String = MANIFEST.get(key, "")
\tif path.is_empty():
\t\tvar fallback: String = FALLBACKS.get(key.get_slice("/", 0), "")
\t\tif fallback.is_empty() or not MANIFEST.has(fallback):
\t\t\tpush_error("Assets: کلید ناشناخته «%s»" % key)
\t\t\treturn null
\t\tpush_warning("Assets: کلید «%s» موجود نبود؛ «%s» استفاده شد" % [key, fallback])
\t\tpath = MANIFEST[fallback]
\tvar res: Texture2D = load(path)
\t_cache[key] = res
\treturn res


static func sound(key: String) -> AudioStream:
\tvar path: String = SOUND_MANIFEST.get(key, "")
\tif path.is_empty():
\t\tpush_error("Assets: صدای ناشناخته «%s»" % key)
\t\treturn null
\treturn load(path)


static func has_key(key: String) -> bool:
\treturn MANIFEST.has(key)


static func keys() -> Array:
\treturn MANIFEST.keys()


static func sound_keys() -> Array:
\treturn SOUND_MANIFEST.keys()
'''


def collect(folder: str) -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    base = os.path.join(ROOT, "assets", folder)
    for root, _dirs, files in os.walk(base):
        for name in sorted(files):
            lower = name.lower()
            if not (lower.endswith(".png") or lower.endswith(".jpg") or lower.endswith(".jpeg")):
                continue
            ext_len = 5 if lower.endswith(".jpeg") else 4
            full = os.path.join(root, name)
            rel_with_ext = os.path.relpath(full, base).replace(os.sep, "/")
            rel = rel_with_ext[:-ext_len]
            rows.append((rel, "res://assets/%s/%s" % (folder, rel_with_ext)))
    return rows


def iso_key(rel: str) -> str:
    """کلید منطقی یک تصویر: پوشهٔ پک‌های ایزومتریک با دستهٔ خودشان عوض می‌شود."""
    for folder, category in ISO_PACKS.items():
        if rel.startswith(folder + "/"):
            return category + rel[len(folder):]
    return rel


def collect_sounds() -> list[tuple[str, str]]:
    rows: list[tuple[str, str]] = []
    for root, dirs, files in os.walk(SOUNDS):
        dirs.sort()
        for name in sorted(files):
            lower = name.lower()
            if not (lower.endswith(".wav") or lower.endswith(".ogg")):
                continue
            full = os.path.join(root, name)
            rel_with_ext = os.path.relpath(full, SOUNDS).replace(os.sep, "/")
            rel = os.path.splitext(rel_with_ext)[0]
            rows.append((rel, "res://assets/sounds/%s" % rel_with_ext))
    return rows


def main() -> None:
    sprites = [(iso_key(rel), path) for rel, path in collect("sprites")]
    # آیکون‌ها در پوشهٔ خودشان دستهٔ «icons» را می‌سازند: icons/w_bow
    icons = [("icons/" + rel, path) for rel, path in collect("icons")]
    sounds = collect_sounds()
    lines = []
    for key, path in sprites + icons:
        lines.append('\t"%s": "%s",' % (key, path))
    lines.append('\t"icon/app": "res://icon_512.png",')
    lines.append('\t"icon/foreground": "res://icon_foreground_432.png",')
    lines.append('\t"icon/background": "res://icon_background_432.png",')

    fallbacks = [
        ('"ground"', '"ground/grass"'),
        ('"maps"', '"maps/map_01"'),
        ('"props"', '"props/rock_small"'),
        ('"units"', '"units/soldier"'),
        ('"structures"', '"structures/keep"'),
        ('"towers"', '"towers/base"'),
        ('"bullets"', '"bullets/stone"'),
        ('"fx"', '"fx/explosion_3"'),
        ('"icons"', '"icons/coin"'),
        ('"icon"', '"icon/app"'),
    ]
    keys = {key for key, _path in sprites}
    for category, fallback in ISO_FALLBACKS.items():
        if fallback not in keys:
            raise SystemExit("پشتوانهٔ دستهٔ %s در دارایی‌ها نیست: %s" % (category, fallback))
        fallbacks.append(('"%s"' % category, '"%s"' % fallback))
    for category in ISO_PACKS.values():
        if category not in ISO_FALLBACKS:
            raise SystemExit("دستهٔ ایزومتریک %s پشتوانه ندارد" % category)
    fb_lines = ["\t%s: %s," % (k, v) for k, v in fallbacks]

    sound_lines = ['\t"%s": "%s",' % (key, path) for key, path in sounds]

    body = (HEADER
            .replace("__MANIFEST__", "\n".join(lines))
            .replace("__FALLBACKS__", "\n".join(fb_lines))
            .replace("__SOUNDS__", "\n".join(sound_lines)))
    os.makedirs(os.path.dirname(TARGET), exist_ok=True)
    with open(TARGET, "w", encoding="utf-8") as handle:
        handle.write(body)
    print("نوشته شد: %s  (%d تصویر، %d صدا)" % (TARGET, len(sprites) + len(icons) + 3, len(sounds)))


if __name__ == "__main__":
    main()
