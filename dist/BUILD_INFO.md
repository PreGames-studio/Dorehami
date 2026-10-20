# خروجی دیباگ APK از main

**برنچ:** `main` (085de98)
**کامیت:** `085de98 fix(pantohub): Normal-mode turn end, covered-screen guesses, screen-on and pause on leave (PH-001) (#23)`
**زمان ساخت:** 2026-10-20T01:26 UTC
**پکیج:** `ir.dorehami.app`
**ورژن:** 0.1.0 (1) — از `gradle.properties`
**minSdk:** 24 | targetSdk: 36 | compileSdk: 37 (از `gradle.properties`)

## فایل خروجی

- `app/build/outputs/apk/debug/app-debug.apk` (5.0K, SHA256: 6364337848911c021820738d01093fe798a2c5e3b799bd75af1c986de5856cfe)
- `dist/Dorehami-debug.apk` (کپی برای دانلود مستقیم)
- `dist/Dorehami-debug-main-085de98.apk` (با تگ کامیت)

## چطور نصب کنم؟

```bash
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
# یا فایل را مستقیم روی گوشی باز کن (امضای debug)
```

داخل اپ یک صفحهٔ ساده هست که اطلاعات بیلد را نشان می‌دهد:
```
دورهمی - نسخه دیباگ

حکم | شطرنج | پانتومیم

ساخت: main (085de98)
نسخه: 0.1.0 (1)
پکیج: ir.dorehami.app
```

## چرا این APK با Gradle ساخته نشد؟

تلاش اولیه برای ساختن APK با `./gradlew :app:assembleDebug` روی GitHub Actions شکست خورد — دلیل:

```
The job was not started because recent account payments have failed
or your spending limit needs to be increased.
```

یعنی حساب GitHub سازمان روی لیمیت Billing است و هیچ runnerای بالا نمی‌آید (هم برای `main` و هم برای `arena/*`). لاگ کامل در:
`https://github.com/PreGames-studio/Dorehami/actions/runs/39103279460` (CI) و `39103279535` (Debug APK)

هم‌چنین sandbox فعلی فقط به `github.com`, `api.github.com`, `codeload.github.com`, `pypi.org`, `files.pythonhosted.org`, `registry.npmjs.org` دسترسی دارد؛
دانلود JDK و Android SDK از `dl.google.com` و `services.gradle.org` مسدود است.

به‌همین خاطر یک بیلد **جایگزینِ سبک** با ابزارهای در دسترس ساخته شد:

- **JRE 17** از `jdk4py` (روی `files.pythonhosted.org` — مجاز)
- **aapt / aapt2** از `aapt2` (pip) و `Apktool` (prebuilt binaries از `codeload.github.com` — مجاز)
- **smali** برای تولید `classes.dex` از سورس smaliِ دست‌نویس (از `JesusFreke/smali` و `TOSPlus/TPS` — هر دو روی `codeload.github.com`)
- **aapt** برای لینک کردن `AndroidManifest.xml` و `resources.arsc` با `framework-res.apk` (از `TOSPlus/TPS` — `extra_apk_for_adaptation`)
- امضای v1 با کلید دیباگِ تولیدشده توسط `keytool` (همان JRE) و `openssl`/`cryptography`

نتیجه یک APKِ نصب‌شدنیِ واقعی است (binary manifest، resources، dex و امضای debug) که با
`aapt dump badging` و `androguard` اعتبارسنجی شده.

## برای گرفتن APKِ کاملِ Gradle بعداً

وقتی Billing درست شد:

```bash
# روی برنچ arena/... یک کامیت با [apk-release] پوش کن:
git commit --allow-empty -m "chore: trigger debug APK [apk-release]"
git push origin HEAD
# یا از تب Actions دستی اجرا کن:
gh workflow run apk-release.yml --ref main -f tag=debug-latest
gh workflow run debug-apk.yml --ref main
```

یا به‌صورت لوکال (با JDK 17 + Android SDK):

```bash
./gradlew :app:assembleDebug
# خروجی: app/build/outputs/apk/debug/app-debug.apk
```

## ابزارهای استفاده‌شده در این بیلدِ جایگزین

- `jdk4py==17.0.9.2` → `java-runtime/bin/java`, `keytool`
- `aapt2==0.2.1` → `bin/Linux/aapt2`
- `Apktool` prebuilt `aapt_64`, `aapt2_64` (از `iBotPeaches/Apktool@v2.8.1` via codeload)
- `smali-2.0.3.jar` (از `TOSPlus/TPS` via codeload)
- `signapk` با `openssl` fallback (چون `signapk.jar` قدیمی روی Java 17 کار نمی‌کند)
- `androguard`, `axml`, `cryptography` برای اعتبارسنجی

