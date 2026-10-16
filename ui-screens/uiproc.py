"""پردازش صفحه‌های پیروزی/شکست: برش وسط به نسبت ۱۶:۹ و مقیاس دقیق به ۱۹۲۰×۱۰۸۰.

خروجی‌ها PNG بدون آلفا (پس‌زمینهٔ تمام‌صفحه) و آمادهٔ Construct 3:
  victory_screen.png , defeat_screen.png

Usage:  python3 uiproc.py
"""
import os

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"
OUT = HERE
W, H = 1920, 1080
TARGET_RATIO = W / H

SCREENS = {
    "victory_raw.png": "victory_screen.png",
    "defeat_raw.png": "defeat_screen.png",
}


def cover_crop(im):
    """برش وسط به نسبت ۱۶:۹ (بدون حذف نواحی بالا/پایین که فضای متن‌اند)."""
    w, h = im.size
    ratio = w / h
    if ratio > TARGET_RATIO:      # تصویر پهن‌تر است: از پهنا کم کن
        nw = round(h * TARGET_RATIO)
        box = ((w - nw) // 2, 0, (w - nw) // 2 + nw, h)
    else:                          # تصویر بلندتر است: از ارتفاع کم کن
        nh = round(w / TARGET_RATIO)
        box = (0, (h - nh) // 2, w, (h - nh) // 2 + nh)
    return im.crop(box)


if __name__ == "__main__":
    for src, dst in SCREENS.items():
        im = Image.open(f"{RAW}/{src}").convert("RGB")
        im = cover_crop(im).resize((W, H), Image.LANCZOS)
        im = im.filter(ImageFilter.UnsharpMask(radius=2, percent=60, threshold=2))
        im.save(f"{OUT}/{dst}", optimize=True)
        print(dst, im.size, im.mode)
