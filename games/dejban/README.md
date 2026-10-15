# دژبان (`games/dejban`)

> **خلاصه:** پروژهٔ Godot بازی دژبان (دفاع از قلعه) در `godot/` نشسته است؛ ماژول‌های Gradle
> یعنی `core/` و `ui/` هنوز جای‌نگهدارند و بازی **هنوز به اپ دورهمی وصل نشده است**.

| پوشه | چیست |
|---|---|
| `godot/` | پروژهٔ Godot 4.3 دژبان: صحنه‌ها، کد GDScript، دارایی‌ها، تست بدون رابط و ابزارهای پایتونی |
| `godot/standalone_repo/` | `README.md` و `.gitignore` ریشهٔ مخزن مستقل دژبان (هم‌نام با فایل‌های ریشهٔ دورهمی) |
| `core/` | جای‌نگهدار `:games:dejban:core` (فقط `build.gradle.kts` توضیحی) |
| `ui/` | جای‌نگهدار `:games:dejban:ui` (فقط `build.gradle.kts` توضیحی) |

## منشأ `godot/`

محتوای `godot/` از فایل `Dezhban.zip` (گوگل‌درایو، ۲۰۲۶-۱۰-۱۵) آمده است:

- zip اصلی: ۸۱٬۶۳۲٬۵۵۱ بایت · ۸۶۸ فایل ·
  SHA-256 `10cd6ce40453b4d7bf3c19125eddf6eda9c8a6fa8e3710ff2181464f1d3e0aeb`
- **حذف‌شده** (۴۸۸ فایل، حدود ۵۰ مگابایت؛ هیچ‌کدام هنگام اجرای بازی بارگذاری نمی‌شوند):
  `google_drive/` (عکس‌های خام)، `spritesheets/` (شیت‌های منبع و پیش‌نمایش)،
  `soundsheets/` (موسیقی‌های کاندید)، `tools/art_source/` (هنر منبع ابزارها).
- **جابه‌جاشده:** `README.md` و `.gitignore` به `godot/standalone_repo/`. قواعد آن `.gitignore`
  (کش `.godot/`، `export/`، فایل‌های APK و…) حالا در `.gitignore` ریشهٔ مخزن، محدود به همین پوشه، است.
- بقیهٔ فایل‌ها بایت‌به‌بایت همان محتوای zip است.

پیامدهای حذف:

- چند ابزار بازسازی هنر در `godot/tools/` (مثل `import_drive_art.py`، `gen_turret_sprites.py`،
  `compose_music.py`) ورودی یا خروجی‌شان همان پوشه‌های حذف‌شده است؛ برای اجرای دوباره‌شان
  نسخهٔ کامل در همان zip روی درایو هست.
- `src/data/TurretWeapons.gd` هنوز مسیر شیت‌های `spritesheets/turrets/tiers` را در فیلد
  `sheets` نگه می‌دارد؛ این فیلد فقط اطلاعات است و هیچ کدی آن را بارگذاری نمی‌کند (تصویر
  برجک‌ها از `assets/sprites/towers` می‌آید).

`godot/standalone_repo/README.md` و `godot/RULES.md` اسناد اصلی همان پروژه‌اند و پیوندهایشان
به مخزن مستقل دژبان اشاره می‌کند.

## اجرا و تست

نیازمندی: Godot 4.3 (نسخهٔ استاندارد، نه .NET).

```bash
godot --path games/dejban/godot                                         # اجرای بازی
godot --headless --path games/dejban/godot --import                     # ایمپورت دارایی‌ها
godot --headless --path games/dejban/godot res://tests/headless_test.tscn   # تست بدون رابط
python3 games/dejban/godot/tools/check_refs.py                          # بررسی ارجاع‌های GDScript
```

در CI، گردش‌کار **Dejban (Godot) verify** (`.github/workflows/dejban-godot-verify.yml`) همین
ایمپورت و تست را اجرا می‌کند؛ فقط وقتی `games/dejban/godot/**` عوض شود.
گردش‌کارهای داخل `godot/.github/workflows/` در این مخزن **اجرا نمی‌شوند**، چون GitHub فقط
پوشهٔ `.github` ریشه را می‌خواند.

## چرا داخل `godot/` و نه ریشهٔ مخزن؟

- ریشهٔ zip با ریشهٔ دورهمی روی `README.md` و `.gitignore` هم‌نام است و گردش‌کارهایش روی
  هر push و PR دورهمی فعال می‌شدند.
- اگر `project.godot` در ریشه بود، Godot کل مخزن (ماژول‌های اندرویدی هم) را اسکن و برای
  تصویرها، صداها و فونت‌هایشان فایل `.import` می‌ساخت.
- قاعدهٔ ۱ در `AGENTS.md`: هر بازی فقط داخل پوشهٔ خودش.
