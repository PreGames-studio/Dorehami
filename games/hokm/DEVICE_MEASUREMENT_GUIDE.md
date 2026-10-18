# راهنمای اندازه‌گیری روی گوشی (M-1 / M-2)

> برای بستن HK-013 (PARTIAL) و شروع GX-06/GX-08. فقط یک گوشی اندرویدی + یک کامپیوتر با `adb` لازم است.

## ۱. گرفتن APK

1. در GitHub → Actions → ورک‌فلوی **Debug APK** را روی برنچ `arena/9e041d6a-dorehami` اجرا کن (یا `gh workflow run debug-apk.yml --ref arena/9e041d6a-dorehami`).
2. artifact `Dorehami-debug.apk` را دانلود و روی گوشی نصب کن: `adb install -r -t Dorehami-debug.apk`.

## ۲. اتصال adb

```bash
adb devices          # گوشی باید در لیست باشد
adb shell getprop ro.build.fingerprint   # برای ثبت در BASELINE.md
```

## ۳. اندازه‌گیری‌ها (BASELINE.md §۴ و §۷)

```bash
# فریم (p95, %jank) — سناریوی ثابت را اجرا کن، سپس:
adb shell dumpsys gfxinfo ir.dorehami

# حافظه (پس از ۱۰ چرخهٔ بازی):
adb shell dumpsys meminfo ir.dorehami

# شروع سرد:
adb shell am start -W -n ir.dorehami/.MainActivity

# تأخیر لمس: از Profiler Android Studio یا systrace
```

نتایج را با شناسهٔ دستگاه، commit (`git rev-parse --short HEAD`) و تاریخ در `games/hokm/BASELINE.md` §۷ ثبت کن.

## ۴. دسترس‌پذیری

- TalkBack را روشن کن → هر صفحه را با swipe بررسی کن (برچسب‌ها از `HokmA11y`).
- قلم ۲۰۰٪ (Settings → Display → Font size) → بررسی overflow/clipping.
- کنتراست: ابزار Accessibility Scanner.

## ۵. تصمیم M-2 (ابزار screenshot)

**پیشنهاد:** golden روی دستگاه (ساده‌تر، بدون وابستگی جدید). اگر Paparazzi را ترجیح می‌دهی، یک کارتِ جدا با توضیح «چرا» لازم است (AGENTS.md §۴.۶).

## ۶. بعد از اندازه‌گیری

نتایج را بفرست تا:
- M-1/M-2 را ببندم.
- GX-06 (Baseline Profile + Macrobenchmark) و GX-08 (رگرسیون بصری) را شروع کنم.
- HK-013 را DONE بزنم.

> **نکته:** اگر فقط گوشی داری و کامپیوتری با adb نداری، می‌توانی از «Wireless debugging» (Android 11+) برای اتصال بی‌سیم به یک کامپیوتر دیگر استفاده کنی، یا از ابزارهای on-device profiling (مثل Android Studio روی لپ‌تاپ).
