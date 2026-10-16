"""ساخت آیکون اپلیکیشن «دزیان» — ۱۰۲۴×۱۰۲۴ PNG، آمادهٔ آیکون APK اندروید.

هنر پایه از raw/ (تصویرسازی مربع) می‌آید؛ عنوان فارسی «دزیان» با وزیرمتن FD
(بولد، طلایی با دور مشکی و سایهٔ تخت) در نوار پایین اضافه می‌شود تا خوانا و
غیرغالب بماند. خروجی‌ها:
  game_icon.png          — با عنوان
  game_icon_notext.png   — بدون عنوان (برای سایزهای خیلی کوچک)
  preview_icon_sizes.png — راستی‌آزمایی خوانایی در ۲۵۶/۱۲۸/۹۶/۴۸ پیکسل

Usage:  python3 iconproc.py
"""
import os

import arabic_reshaper
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"
OUT = HERE
SIZE = 1024
FONTS = os.path.normpath(f"{HERE}/../games/dejban/godot/assets/fonts")

GOLD = (255, 206, 64)
GOLD_DARK = (146, 92, 10)
BLACK = (20, 16, 12)


def fa(t):
    return get_display(arabic_reshaper.reshape(t))


def add_title(img):
    dr = ImageDraw.Draw(img)
    font = ImageFont.truetype(f"{FONTS}/Vazirmatn-FD-Bold.ttf", 170)
    txt = fa("دزیان")
    cx, cy = SIZE / 2, 916
    # سایهٔ تخت ساده
    dr.text((cx + 6, cy + 10), txt, font=font, fill=GOLD_DARK, anchor="mm")
    # متن طلایی با دور مشکی ضخیم
    dr.text((cx, cy), txt, font=font, fill=GOLD, anchor="mm",
            stroke_width=16, stroke_fill=BLACK)
    return img


def build():
    img = Image.open(f"{RAW}/game_icon_raw.png").convert("RGB")
    w, h = img.size
    side = min(w, h)
    img = img.crop(((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2))
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    plain = img.copy()
    return add_title(img), plain


def preview_sizes(icon, path):
    sizes = [256, 128, 96, 48]
    pad = 24
    total_w = sum(sizes) + pad * (len(sizes) + 1)
    sheet = Image.new("RGB", (total_w, max(sizes) + 2 * pad), (238, 240, 244))
    x = pad
    for s in sizes:
        sheet.paste(icon.resize((s, s), Image.LANCZOS), (x, pad))
        x += s + pad
    sheet.save(path, optimize=True)


if __name__ == "__main__":
    icon, plain = build()
    icon.save(f"{OUT}/game_icon.png", optimize=True)
    plain.save(f"{OUT}/game_icon_notext.png", optimize=True)
    preview_sizes(icon, f"{OUT}/preview_icon_sizes.png")
    print("game_icon.png", icon.size, icon.mode)
