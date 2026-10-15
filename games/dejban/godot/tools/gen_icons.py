#!/usr/bin/env python3
"""سازندهٔ آیکون‌های اپ دژبان — حالا فقط پوسته‌ای روی tools/make_app_icons.py.

پیش‌تر این اسکریپت خودش نشانِ هندسی می‌ساخت و همان پنج فایل را می‌نوشت. از
وقتی آیکونِ نهایی از تصویرِ هنریِ انتخاب‌شده ساخته می‌شود، تنها یک منبعِ درست
وجود دارد: spritesheets/app_icon/icon_military.png. این پوسته نگه داشته شده تا
اجرای دستورِ قدیمی همان نتیجهٔ درست را بدهد و آیکون را به نسخهٔ قدیمی برنگرداند.

    python3 tools/gen_icons.py

خروجی‌ها در ریشهٔ مخزن و برای خروجی اندروید:
  icon_192.png              آیکون معمولی لانچر
  icon_512.png              نسخهٔ بزرگ (پلی‌استور و پیش‌نمایش)
  icon_foreground_432.png   لایهٔ پیش‌زمینهٔ آیکون تطبیقی اندروید ۸+
  icon_background_432.png   لایهٔ پس‌زمینهٔ آیکون تطبیقی
  assets/icons/app_512.png  همان ۵۱۲ برای واسط بازی
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import make_app_icons  # noqa: E402  (مسیر بالا افزوده شد تا کنارِ همین فایل پیداش کند)


def main() -> int:
    print("این اسکریپت به tools/make_app_icons.py سپرده شده است.")
    make_app_icons.build()
    return make_app_icons.check()


if __name__ == "__main__":
    raise SystemExit(main())
