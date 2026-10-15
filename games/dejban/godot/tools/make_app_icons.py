#!/usr/bin/env python3
"""آیکون‌های اپ را از تصویر مادرِ انتخاب‌شده می‌سازد و در جای خودشان می‌نویسد.

    python3 tools/make_app_icons.py            ساخت آیکون‌ها
    python3 tools/make_app_icons.py --check    فقط بررسی اندازه‌ها و قاب ایمن

قرارداد اندازه‌ها همان است که تست بدون رابط می‌سنجد و در export_presets.cfg
ثبت شده:

    icon_192.png            ۱۹۲×۱۹۲  آیکون ساده (بدون ماسک)
    icon_512.png            ۵۱۲×۵۱۲  آیکون فهرست برنامه‌ها
    assets/icons/app_512.png ۵۱۲×۵۱۲  همان تصویر برای فهرست دارایی‌ها
    icon_foreground_432.png ۴۳۲×۴۳۲  پیش‌زمینهٔ آیکون تطبیقی اندروید
    icon_background_432.png ۴۳۲×۴۳۲  پس‌زمینهٔ آیکون تطبیقی اندروید

اندروید لایه‌های تطبیقی را با ماسک (دایره/اسکوئرکل) می‌بُرد و از بومِ ۴۳۲
پیکسلی فقط مربعِ مرکزیِ ۲۸۸ پیکسلی دیده می‌شود. بنابر انتخاب کاربر، عنوانِ
طلایی «DEZHBAN» هم باید بماند؛ پس کل هنر — با عنوانش — داخل دایرهٔ ایمنِ
شعاعِ ۱۴۴ جا داده می‌شود و کوچک می‌شود، نه اینکه بریده شود.

پس‌زمینهٔ لایهٔ تطبیقی همان آبیِ تیرهٔ گوشه‌های تصویر است با یک وینیتِ ملایم
به سمت بیرون (نه هالهٔ روشن)، تا هنرِ کوچک‌شده لبهٔ سختی با پشتِ خود نداشته
باشد؛ لبه‌های خودِ هنر هم با یک شیبِ آلفا در پس‌زمینه حل می‌شوند.
"""

from __future__ import annotations

import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    sys.exit("Pillow لازم است:  pip install pillow")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTER = os.path.join(ROOT, "spritesheets", "app_icon", "icon_military.png")
SIMPLE = [(os.path.join(ROOT, "icon_192.png"), 192),
          (os.path.join(ROOT, "icon_512.png"), 512),
          (os.path.join(ROOT, "assets", "icons", "app_512.png"), 512)]
ADAPTIVE_FG = os.path.join(ROOT, "icon_foreground_432.png")
ADAPTIVE_BG = os.path.join(ROOT, "icon_background_432.png")

CANVAS = 432                 # بومِ لایه‌های تطبیقی
VISIBLE = 288                # مربعِ دیده‌شده زیر ماسک اندروید
SAFE_RADIUS = VISIBLE / 2.0  # شعاعِ دایرهٔ ایمن = ۱۴۴
BRIGHT = 96                  # آستانهٔ «محتوای دیدنی» روی روشنایی
FEATHER = 12                 # پهنای شیبِ آلفا در سه لبهٔ هنر
FEATHER_BOTTOM = 4           # لبهٔ پایین کم، چون عنوان به آن نزدیک است
VIGNETTE_DARK = (2, 7, 18)   # رنگِ بیرونی‌ترین حلقهٔ پس‌زمینه
VIGNETTE_DROP = 0.41         # شدتِ وینیت روی کانال روشنایی


def corner_color(img: Image.Image) -> tuple[int, int, int]:
    """میانگینِ رنگ چهار گوشه = رنگِ پس‌زمینهٔ تصویر."""
    w, h = img.size
    box = max(8, int(min(w, h) * 0.04))
    corners = [(0, 0), (w - box, 0), (0, h - box), (w - box, h - box)]
    total = [0, 0, 0]
    for px, py in corners:  # کوچک‌کردن هر گوشه به یک پیکسل = میانگینِ آن
        r, g, b = img.crop((px, py, px + box, py + box)).convert("RGB").resize((1, 1), Image.LANCZOS).getpixel((0, 0))
        total[0] += r
        total[1] += g
        total[2] += b
    n = len(corners)
    return (total[0] // n, total[1] // n, total[2] // n)


def bright_reach(img: Image.Image, threshold: int = BRIGHT) -> float:
    """بیشترین فاصلهٔ پیکسل‌های روشن از مرکز، در مقیاس خودِ تصویر مادر.

    ملاکِ «محتوای دیدنی» همین است، نه کادر کامل: گوشه‌های تیرهٔ آسمان هم‌رنگ
    پس‌زمینه‌اند و اگر ماسک آن‌ها را ببُرد فرقی دیده نمی‌شود، ولی برج و رون‌ها
    و عنوان باید سالم بمانند.
    """
    probe = 400
    small = img.convert("L").resize((probe, probe), Image.LANCZOS).load()
    centre = (probe - 1) / 2.0
    best = 0.0
    for y in range(probe):
        for x in range(probe):
            if small[x, y] > threshold:
                dx, dy = x - centre, y - centre
                d = (dx * dx + dy * dy) ** 0.5
                if d > best:
                    best = d
    return best / float(probe) * float(max(img.size))


def art_side_for_safe_circle(img: Image.Image) -> tuple[int, float]:
    """بزرگ‌ترین اندازهٔ هنر که محتوای روشنش داخل دایرهٔ ایمن بماند."""
    reach = bright_reach(img)
    if reach <= 0.0:
        return VISIBLE, 0.0
    side = min(VISIBLE, int(round(SAFE_RADIUS * max(img.size) / reach)))
    return max(160, side), reach


def feathered_mask(side: int) -> Image.Image:
    """ماسکِ آلفا با لبه‌های نرم؛ پایین کم‌تر تا عنوان محو نشود."""
    mask = Image.new("L", (side, side), 0)
    draw = ImageDraw.Draw(mask)
    draw.rectangle((FEATHER, FEATHER, side - FEATHER - 1, side - FEATHER_BOTTOM - 1), fill=255)
    return mask.filter(ImageFilter.GaussianBlur(3.0))


def backdrop(bg: tuple[int, int, int]) -> Image.Image:
    """پس‌زمینهٔ تمام‌قابِ ۴۳۲: آبیِ تیرهٔ گوشه‌ها + وینیتِ ملایم به بیرون.

    هالهٔ روشن آزمایش شد و آیکون را شسته و کم‌کنتراست می‌کرد؛ تیرگیِ یکدست
    لبهٔ هنر را نامرئی می‌کند.
    """
    base = Image.new("RGB", (CANVAS, CANVAS), bg)
    ring = Image.new("L", (CANVAS, CANVAS), 0)
    draw = ImageDraw.Draw(ring)
    centre = CANVAS / 2.0
    steps = 70
    inner = VISIBLE / 2.0
    outer = SAFE_RADIUS + 40.0
    for i in range(steps):
        radius = inner + (outer - inner) * i / (steps - 1.0)
        draw.ellipse((centre - radius, centre - radius, centre + radius, centre + radius),
                     fill=int(255 * VIGNETTE_DROP * i / (steps - 1.0)))
    return Image.composite(Image.new("RGB", (CANVAS, CANVAS), VIGNETTE_DARK), base,
                           ring.filter(ImageFilter.GaussianBlur(26)))


def build() -> None:
    if not os.path.exists(MASTER):
        sys.exit("تصویر مادر پیدا نشد: %s" % MASTER)
    master = Image.open(MASTER).convert("RGB")
    bg = corner_color(master)
    side, reach = art_side_for_safe_circle(master)
    scaled = reach * side / float(max(master.size))

    # ۱) آیکون‌های ساده: تمام‌قاب، بدون ماسک
    for path, px in SIMPLE:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        master.resize((px, px), Image.LANCZOS).save(path, optimize=True)

    # ۲) پس‌زمینهٔ تطبیقی: آبیِ تیره + وینیت
    backdrop(bg).save(ADAPTIVE_BG, optimize=True)

    # ۳) پیش‌زمینهٔ تطبیقی: هنرِ کامل (با عنوان) داخل دایرهٔ ایمن، با لبهٔ نرم
    art = master.resize((side, side), Image.LANCZOS)
    fg = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = (CANVAS - side) // 2
    fg.paste(art, (offset, offset), feathered_mask(side))
    fg.save(ADAPTIVE_FG, optimize=True)

    print("رنگ پس‌زمینه: #%02x%02x%02x" % bg)
    print("دورترین پیکسلِ روشن: %.0f از مرکز (مقیاس %d) ⇒ هنر %d از %d"
          % (reach, max(master.size), side, CANVAS))
    print("   ⇒ محتوای دیدنی تا %.1f پیکسل از شعاعِ ایمنِ %.0f می‌رسد" % (scaled, SAFE_RADIUS))
    for path, px in SIMPLE:
        print("  %-30s %dx%d" % (os.path.relpath(path, ROOT), px, px))
    print("  %-30s %dx%d" % ("icon_foreground_432.png", CANVAS, CANVAS))
    print("  %-30s %dx%d" % ("icon_background_432.png", CANVAS, CANVAS))


def check() -> int:
    """اندازهٔ فایل‌ها و ماندنِ محتوای دیدنی داخل دایرهٔ ایمن را می‌سنجد."""
    problems = 0
    for path, px in SIMPLE + [(ADAPTIVE_FG, CANVAS), (ADAPTIVE_BG, CANVAS)]:
        rel = os.path.relpath(path, ROOT)
        if not os.path.exists(path):
            print("  ✗ گم شده: %s" % rel)
            problems += 1
            continue
        size = Image.open(path).size
        ok = size == (px, px)
        print("  %s %-30s %s" % ("✓" if ok else "✗", rel, size))
        problems += 0 if ok else 1

    if not os.path.exists(MASTER):
        print("  ✗ تصویر مادر نیست؛ قاب ایمن بررسی نشد")
        return problems + 1
    master = Image.open(MASTER).convert("RGB")
    side, reach = art_side_for_safe_circle(master)
    scaled = reach * side / float(max(master.size))
    if scaled > SAFE_RADIUS + 1.0:
        print("  ✗ محتوای دیدنی تا %.0f پیکسل می‌رسد؛ دایرهٔ ایمن %.0f است" % (scaled, SAFE_RADIUS))
        problems += 1
    else:
        print("  ✓ محتوای دیدنی داخل دایرهٔ ایمن است (%.0f از %.0f پیکسل)" % (scaled, SAFE_RADIUS))
    print("نتیجه: %s" % ("بدون مشکل" if problems == 0 else "%d مشکل" % problems))
    return 1 if problems else 0


if __name__ == "__main__":
    if "--check" in sys.argv[1:]:
        sys.exit(check())
    build()
