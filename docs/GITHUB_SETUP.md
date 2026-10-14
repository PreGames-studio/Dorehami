# گذاشتن پروژه روی گیت‌هاب — دقیقاً چه فایل‌هایی و چه تنظیماتی

> **خلاصهٔ فارسی:** **همهٔ** فایل‌های پروژه را آپلود کن (فهرست کامل در بخش ۱؛ بدون هیچ استثنا جز
> موارد بخش ۲). هیچ فایلی لازم نیست دستی بسازی. بعد از آپلود، فقط ۵ تنظیم در گیت‌هاب انجام بده (بخش ۴) —
> مهم‌ترینش عوض‌کردن نام‌های نمونه در `CODEOWNERS` است.

---

## ۱. فهرست فایل‌هایی که آپلود می‌شوند (و کار هرکدام)

```
dorehami/
├── README.md                     ← از اینجا شروع کن
├── AGENTS.md                     ← دفترچهٔ عامل هوش مصنوعی (Copilot/Claude/Cursor)
├── CONTRIBUTING.md               ← قواعد همکاری + مالکیت (CODEOWNERS)
├── .gitignore                    ← جلوی آپلود فایل‌های موقت/رمزها را می‌گیرد
├── .editorconfig                 ← یکسان‌سازی سبک نوشتن بین ادیتورها
├── build.gradle.kts              ← ساخت ریشه + تسک checkAll
├── settings.gradle.kts           ← فهرست ماژول‌ها
├── gradle.properties             ← نسخهٔ اپ، آدرس سرور، تنظیمات ساخت
├── gradlew  /  gradlew.bat       ← دستور ساخت مستقل از نصب Gradle
├── gradle/
│   ├── libs.versions.toml        ← همهٔ نسخهٔ کتابخانه‌ها، یک‌جا
│   └── wrapper/                  ← gradle-wrapper.jar + properties  (ساخت باید کار کند)
├── app/build.gradle.kts          ← تنظیمات اپ اندروید
├── server/build.gradle.kts       ← تنظیمات سرور
├── docs/
│   ├── architecture-simple-fa.html   ← معماری به زبان ساده (برای همه)
│   ├── ARCHITECTURE.md              ← معماری فنی
│   ├── GAME_CONTRACT.md             ← قرارداد بازی (هفت سؤال)  ★ مهم‌ترین سند برای بازی‌سازها
│   ├── PROTOCOL.md                  ← شکل پیام‌ها و خطاها
│   ├── ADD_A_GAME.md                ← افزودن بازی جدید قدم‌به‌قدم
│   ├── ROADMAP.md                   ← ترتیب ساخت + ۲۰ تسک آمادهٔ ایشو + تقسیم کار
│   ├── DEPLOY.md                    ← نصب روی سرور (systemd + Nginx)
│   ├── GITHUB_SETUP.md              ← همین فایل
│   ├── HOSTING_GUIDE.md             ← خرید دامنه/سرور به زبان ساده
│   └── adr/ADR-0001…0005            ← چرا این تصمیم‌ها گرفته شد
└── .github/
    ├── workflows/ci.yml             ← تست خودکار روی هر PR
    ├── workflows/device-check.yml    ← اجرای اپ روی امولاتور و تست مسیرهای کاربر
    ├── pull_request_template.md     ← قالب PR (خلاصه، تست، محافظت)
    └── CODEOWNERS                   ← مالکیت مسیرها (نام کاربری‌ها را عوض کن!)
```

## ۲. چه چیزی آپلود **نمی‌شود** (خودِ `.gitignore` جلویش را می‌گیرد)

| مورد | چرا |
|---|---|
| `build/`, `.gradle/`, `.idea/` | خروجی ساخت و تنظیمات ادیتور — روی کامپیوتر هرکس خودش ساخته می‌شود |
| `local.properties` | مسیر SDK هر کامپیوتر فرق دارد |
| `*.jks`, `*.keystore`, `keystore.properties` | **کلید امضای اپ — هرگز به گیت نرود** |
| `.env`, `server.env` | رمزها (توکن سرور، رمز دیتابیس) |
| فایل زیپ معماری (`dorehami-architecture.zip`) | برای انتقال بین کامپیوترها است، نه برای مخزن |

> اگر `local.properties` لازم شد، از `local.properties.example` کپی بگیر (در همین پوشه هست).

## ۳. دستورها (۱۰ خط، یک‌بار)

```bash
cd dorehami

git init
git add -A
git commit -m "docs: complete architecture, game contract and agent guides"

# نام شاخه را main کن (استاندارد گیت‌هاب)
git branch -M main

# مخزن خالی را اول در گیت‌هاب بساز (بدون README، بدون .gitignore) بعد:
git remote add origin git@github.com:USERNAME/dorehami.git     # یا https://github.com/USERNAME/dorehami.git
git push -u origin main
```

اگر با **GitHub Desktop** کار می‌کنی: `File → Add local repository` → پوشهٔ `dorehami` → `Publish
repository`. اگر می‌خواهی از **مرورگر** آپلود کنی: دکمهٔ `Add file → Upload files` و **کل پوشه را
drag کن** (فایل‌های نقطه‌دار مثل `.github` و `.gitignore` ممکن است جا بیفتند — با drag&drop کل پوشه
درست می‌شود، ولی git مطمئن‌ترین راه است).

## ۴. پنج تنظیم بعد از آپلود (به همین ترتیب)

### ۱) نام کاربری‌های `CODEOWNERS` را عوض کن — **مهم‌ترین کار**

در `.github/CODEOWNERS` این نام‌ها نمونه‌اند و باید با نام کاربری واقعی همکارانت جایگزین شوند:

```
@core-owner   →  یوزرنیم نفر «هسته» (الف)
@app-owner    →  یوزرنیم نفر «اپ» (ج)
@team-lead    →  یوزرنیم خودت (یا هرکس تأیید دوم می‌دهد)
@hokm-owner / @panto-owner / @chess-owner  →  مالک هر بازی
```

اگر عوض نشوند، گیت‌هاب فقط یک هشدار می‌دهد و **بازبین اجباری انتخاب نمی‌شود**.

### ۲) محافظت شاخهٔ `main`

`Settings → Branches → Add branch protection rule` برای `main`:

- ✅ Require a pull request before merging (حداقل ۱ تأیید)
- ✅ Require status checks to pass (گزینهٔ `JVM tests (checkAll)` را انتخاب کن)
- ✅ Require conversation resolution before merging
- ❌ Allow force pushes (خاموش)

از این لحظه هیچ‌کس — حتی خودت — مستقیم روی `main` نمی‌نویسد؛ همه از PR.

### ۳) تست خودکار (Actions)

`.github/workflows/ci.yml` خودش فعال است: روی هر PR دو کار اجرا می‌شود — `checkAll` (تست‌های جاوایی)
و ساخت APK دیباگ. لازم نیست کاری کنی؛ فقط توجه: تا وقتی کد ماژول‌ها ساخته نشده، این کارها قرمز
خواهند بود (بخش ۵ را بخوان).

### ۴) برچسب‌ها (Labels)

از `Issues → Labels` این‌ها را بساز (یا اگر `gh` نصب داری، یک‌جا):

```bash
gh label create "game/hokm"    --color B60205
gh label create "game/pantohub" --color D93F0B
gh label create "game/chess"   --color FBCA04
gh label create "core/protocol" --color 0E8A16
gh label create "core/gamesdk"  --color 0E8A16
gh label create "core/session"  --color 0E8A16
gh label create "transport"     --color 1D76DB
gh label create "server"        --color 5319E7
gh label create "app"           --color C2E0C6
gh label create "docs"          --color FEF2C0
gh label create "good-first-issue" --color 7057FF
gh label create "blocked"       --color 000000
```

### ۵) تابلو کار (Projects)

یک Project بساز با چهار ستون `Backlog / در حال انجام / بازبینی / انجام‌شده` و **همان ۲۰ تسک جدول
`docs/ROADMAP.md`** را به‌عنوان کارت اضافه کن. سه تسک اول را به‌عنوان «شروع از اینجا» علامت بزن.

## ۵. نکتهٔ مهم روز اول: پروژه هنوز کد ندارد

الان این مخزن **سند + اسکلت ساخت** است؛ پوشه‌های ماژول‌ها (`core/…`, `games/…`, `app/src`,
`server/src`) هنوز ساخته نشده‌اند و `settings.gradle.kts` آن‌ها را `include` می‌کند. پس تا تسک اول
انجام نشود، `checkAll` خطا می‌دهد — این طبیعی است، نه خرابی.

**اولین تسک تیم** (در ROADMAP با شمارهٔ ۱ آمده) دقیقاً همین است: ساخت اسکلت ماژول‌ها تا
`./gradlew checkAll` سبز شود. اگر بخواهی، من همین حالا این اسکلت را می‌سازم تا مخزن از دقیقهٔ اول
سبز باشد و هر سه نفر بدون سؤال شروع کنند.

## ۶. روز اول هر همکار (سه خط که به‌شان می‌فرستی)

```bash
git clone git@github.com:USERNAME/dorehami.git && cd dorehami
./gradlew checkAll        # باید سبز شود (بعد از تسک اسکلت)
```

و این چیزها را بخوانند: **`AGENTS.md`** (قواعد ساخت/تست)، `CONTRIBUTING.md` (گردش کار PR)،
`docs/GAME_CONTRACT.md` (اگر بازی می‌سازند)، `docs/ROADMAP.md` (چه کاری برای کیست).

## ۷. اگر بعداً خواستی خودکار منتشر کنی

در `app/build.gradle.kts` یک keystore امضا اضافه می‌شود و در گیت‌هاب به‌صورت **Secrets** ذخیره
می‌شود (`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEY_ALIAS`, …). هیچ‌وقت کلید امضا در مخزن نمی‌رود.
این کار مربوط به مرحلهٔ انتشار در کافه‌بازار است (ROADMAP بخش M10).
