# HK-001 — گزارش خط پایهٔ ساخت و آزمون حکم

> 👑 رهبر این گروه **گلپا** است؛ قواعد همکاری مطابق [LEADER.md](../../../LEADER.md).
> تاریخ بررسی: **2026-10-13**، منطقهٔ زمانی گزارش: Asia/Tehran.
> [نقشهٔ راه حکم](../ROADMAP.md) · [راهنمای عامل حکم](../AGENTS.md)
> **وضعیت مرحله: ACTIVE — زیرمرحلهٔ HK-001.a برای اجرای baseline کامل روی runner ابری آغاز شده است؛ تا دریافت نتایج آن، این مرحله هنوز DONE نیست.**

## ۱. خلاصهٔ قابل تصمیم‌گیری

1. کد بازی، تنظیمات ساخت و نسخهٔ کتابخانه‌ها **تغییر نکردند**. workflow دستی جدید `.github/workflows/hokm-baseline.yml` برای اجرای کامل خط پایه اضافه شده است؛ workflow اصلی CI تغییر نکرده.
2. برای **همان commit مبنا**، دو اجرای قبلی و موفق CI و Debug APK پیدا و هویت commit آن‌ها تطبیق داده شد.
3. annotation رسمی CI گزارش می‌کند: **۱۳۸ آزمون، صفر شکست، ۷ ماژول دارای نتیجه**. آن اجرای قبلی `server:test` و lint را مستثنا کرده است؛ معادل اجرای کامل خط پایه نیست.
4. artifact نسخهٔ debug برای همان commit وجود دارد. آزمون دستگاه انجام نشده؛ وجود APK با بررسی آن روی گوشی یکی نیست.
5. این محیط در ابتدا JDK کامل، Gradle، Kotlin compiler، Android SDK و adb نداشت. runtime موقت Java 17 آماده شد، ولی `javac` در آن نبود.
6. اجرای محلی تست حکم، پیش از پیکربندی پروژه، در دریافت Gradle با خطای TLS متوقف شد؛ دریافت archive از GitHub نیز در مقصد release-assets شکست خورد.
7. کاربر تأیید HK-001 را داد و سپس خواست مراحل با برنچ تازه ادامه یابد. **به علت ثابت‌بودن برنچ Arena، شاخهٔ دیگری ایجاد نمی‌شود**؛ اجرای HK-001.a فقط روی `arena/05a354c8-dorehami` پیش می‌رود. پس از دریافت نتیجهٔ واقعی، مرحلهٔ والد جمع‌بندی می‌شود؛ HK-002 خودکار آغاز نمی‌شود.

### اقدام فعال — HK-001.a

کاربر ادامهٔ کار را درخواست کرد. workflow دستی `[hokm-baseline.yml](../../../.github/workflows/hokm-baseline.yml)` برای همان برنچ کاری آماده شده تا تست حکم، سرور، `checkAll` بدون حذف، APK و lint را اجرا کند و XML/log/artifact نگه دارد. تغییر مرتبط فقط روی برنچ ثابت این نشست push می‌شود تا workflow آزمایشی dispatch شود. **بدون ایجاد شاخهٔ دیگر، ادغام به `main`، انتشار APK یا deploy تولیدی.** نتیجهٔ اجرای ابری تا لحظهٔ ثبت گزارش هنوز نامعلوم/منتظر اجراست.

## ۲. دامنه و منشأ داده‌ها

| موضوع | مقدار/شاهد |
|---|---|
| تأیید کاربر | پاسخ «تایید است» به پیشنهاد اجرای HK-001 |
| برنچ کاری | `arena/05a354c8-dorehami`؛ تعویض نشد |
| commit مبنا | `19115ea56bb358eb602c5434a21a2d1291b95a91` |
| عنوان commit | `Merge pull request #9 from PreGames-studio/arena/18d1c9f6-dorehami` |
| تغییرهای موجود ابتدای نوبت | `AGENTS.md`، `docs/ROADMAP.md`، `games/hokm/README.md` و دو سند جدید `games/hokm/AGENTS.md` و `games/hokm/ROADMAP.md`؛ همگی مستندات نوبت قبل |
| فایل تازهٔ این نوبت | همین گزارش در `games/hokm/reports/HK-001-baseline.md` |
| کد و تنظیمات نسبت به مبنا | بدون تغییر؛ بنابراین شواهد CI آن commit برای کد مبنا مرتبط‌اند، نه شاهد اجرای تازه در sandbox |
| عملیات خارجی | فقط خواندن متادیتا/نتایج قبلی CI و دریافت ابزار؛ workflow جدید اجرا، commit/push، PR، merge یا release انجام نشد |

**چهار نوع شاهد از هم جدا هستند:** خواندن کد، اجرای دستور در این محیط، نتیجهٔ قبلی CI روی همان commit، و آزمون واقعی دستگاه. این گزارش آن‌ها را به جای یکدیگر جا نمی‌زند.

## ۳. محیط و نسخه‌های مورد نیاز

### تنظیمات واقعی مخزن

| مؤلفه | مقدار موجود | محل تعیین |
|---|---|---|
| JDK/toolchain و هدف JVM | 17 | اسکریپت ماژول‌ها؛ برای Android نیز `VERSION_17`/`JVM_17` |
| Gradle | 9.8.1 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.3.1 | `gradle/libs.versions.toml` |
| Kotlin و Compose compiler plugin | 2.4.20 | همان version catalog |
| Compose BOM | 2026.09.00 | همان version catalog |
| Coroutines / Serialization | 1.11.0 / 1.11.0 | همان version catalog |
| Ktor | 3.6.0 | همان version catalog |
| JUnit BOM | 5.14.4 | همان version catalog |
| compileSdk / targetSdk / minSdk | 37 / 36 / 24 | `gradle.properties` |
| شناسهٔ اپ | `ir.dorehami.app` | `gradle.properties` |
| نسخهٔ اپ | `0.1.0`، کد 1 | `gradle.properties` |
| مخازن وابستگی | Google Maven، Maven Central و Gradle Plugin Portal | `settings.gradle.kts` |

متن راهنماهای قدیمی `compileSdk 36` می‌گوید، اما مقدار واقعی **37** است. این اختلاف ثبت شد و در این مرحله برای تغییر رفتار build، نسخه‌ای دست‌کاری نشد. توضیح بالای version catalog سقف 9.7.x برای Gradle ذکر می‌کند ولی wrapper مقدار 9.8.1 دارد؛ به‌تنهایی شاهد ناسازگاری نیست، خصوصاً با وجود CI موفق. در بازبینی بعدی ابزارها باید متن و ماتریس پشتیبانی همگام شوند.

وجود انتشار رسمی `v9.8.1` در مخزن `gradle/gradle-distributions` از API بررسی شد؛ مانع این نوبت، اثبات «وجودنداشتن آن نسخه» نیست.

### محیط محلی مشاهده‌شده

- Linux `6.1.158+`، معماری `x86_64`؛ حدود ۲۰GB فضای آزاد در شروع بررسی.
- در شروع، `java`، `javac`، `gradle`، `kotlinc`، `sdkmanager` و `adb` در PATH نبودند و در مسیرهای معمول بررسی‌شده نصب مرتبط پیدا نشد.
- `local.properties` وجود نداشت؛ Android SDK و دستگاه قابل آزمون برای این نوبت در اختیار نبود.
- `gh`، `curl`، Python و npm موجود بودند. نسخهٔ CLI نصب‌شدهٔ `gh` پرچم `gh run list --commit` را نمی‌پذیرفت؛ برای فیلتر commit از endpoint رسمی Actions با `head_sha` استفاده شد.
- برای پیشروی در تشخیص، `jdk4py==17.0.9.2` از PyPI در `/home/user/.cache/hokm-baseline/python` نصب شد؛ خارج از مخزن و فایل‌های نسخه‌دار.
- خروجی Java: `OpenJDK Runtime Environment Temurin-17.0.9+9`. در این بسته فایل اجرایی `javac` وجود ندارد. این runtime فقط مانع شروع Wrapper را کنار زد؛ محیط کامل JDK/Android نشد.
- ابزارهای موقت در cache هستند؛ ماندگاری آن‌ها بین محیط‌ها تضمین نمی‌شود و نباید به عنوان dependency اپ ثبت شوند.

### اثرانگشت تنظیمات مبنا

```text
gradle/wrapper/gradle-wrapper.jar
238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5

gradle/wrapper/gradle-wrapper.properties
03e759961c8cfd784481123c7d328fbc9d5e4ecdbb3c83ef563f90ef84ee3000

gradle/libs.versions.toml
3d0c7526ed0bbb56d3e8454623c1e8b720af077f19d93e79dfce2d9c7e49a176

gradle.properties
7b07ceee7eea47ecc339111841ebc19458ffe361a8249998ff538400d24c5264
```

این hashها هویت فایل بررسی‌شده را ثبت می‌کنند؛ جای بررسی امنیتی زنجیرهٔ تأمین یا امضای ناشر نیستند.

## ۴. تلاش‌های محلی و نتیجهٔ واقعی

### L-01 — پیش از آماده‌سازی runtime

```bash
./gradlew :games:hokm:core:test --no-daemon --stacktrace
```

**Exit code: 1**؛ Wrapper حتی وارد JVM نشد:

```text
ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
```

### L-02 — آماده‌سازی محدود Java برای تشخیص

```bash
python3 -m pip install --index-url https://pypi.org/simple \
  --no-deps --only-binary=:all: \
  --target /home/user/.cache/hokm-baseline/python 'jdk4py==17.0.9.2'
```

**Exit code: 0**؛ نصب runtime موفق بود. این دستور راه‌اندازی کامل محیط ساخت Android نیست و برای انتشار/سرور تولید توصیه نشده است.

### L-03 — تلاش دوم با runtime آماده

```bash
export JAVA_HOME="$(PYTHONPATH=/home/user/.cache/hokm-baseline/python \
  python3 -c 'import jdk4py; print(jdk4py.JAVA_HOME)')"
export GRADLE_USER_HOME=/home/user/.cache/hokm-baseline/gradle
./gradlew :games:hokm:core:test --no-daemon --stacktrace
```

**Exit code: 1**؛ پروژه هنوز configure/compile نشده و هیچ آزمونی آغاز نشده است:

```text
Downloading https://services.gradle.org/distributions/gradle-9.8.1-bin.zip
Attempt 1/1 failed. Reason: Remote host terminated the handshake
javax.net.ssl.SSLHandshakeException: Remote host terminated the handshake
Caused by: java.io.EOFException: SSL peer shut down incorrectly
```

### L-04 — بررسی راه رسمی جایگزین دریافت همان Gradle

```bash
curl --fail --location --silent --show-error \
  --connect-timeout 10 --max-time 90 \
  --output /home/user/.cache/hokm-baseline/downloads/gradle-9.8.1-bin.zip \
  https://github.com/gradle/gradle-distributions/releases/download/v9.8.1/gradle-9.8.1-bin.zip
```

**Exit code: 35**؛ دریافت هنگام انتقال به میزبان فایل release متوقف شد:

```text
curl: (35) OpenSSL SSL_connect: SSL_ERROR_SYSCALL
in connection to release-assets.githubusercontent.com:443
```

اعتبارسنجی SSL خاموش نشد، mirror نامطمئن اضافه نشد و Wrapper/نسخهٔ Gradle تغییر نکرد. این خطاها **شکست تست حکم یا خطای کامپایل Kotlin نیستند**؛ شکست آماده‌سازی محیط‌اند.

### وضعیت تک‌تک گیت‌ها

| گیت مصوب | اجرای تازه در این محیط | شاهد دیگر / محدودیت |
|---|---|---|
| `:games:hokm:core:test` | دو تلاش؛ هر دو پیش از اجرای تست متوقف شدند | عدد تست اجراشدهٔ محلی: **صفر**؛ CI قبلی فقط جمع کل آزمون‌ها را در annotation قابل دریافت ارائه کرده است |
| `:core:gamesdk:test :core:session:test :transport:api:test` | اجرا نشد؛ مانع مشترک Wrapper رفع نشده | بخشی از برنامهٔ `checkAll` قبلی؛ XML جداگانه در این نوبت دریافت نشد |
| `:server:test` | اجرا نشد | در CI مبنا صریحاً حذف شده است؛ هیچ شاهد موفقیت برای آن در این گزارش وجود ندارد |
| `checkAll` بدون استثنا | اجرا نشد | CI قبلی نسخهٔ محدودشدهٔ این دستور را اجرا کرده است |
| `:app:assembleDebug` | اینجا اجرا نشد و APK محلی ساخته نشد | دو job قبلی ساخت APK برای همان commit موفق بوده‌اند؛ artifact رسمی وجود دارد |
| `:app:lint` | اجرا نشد | lint از job آزمون CI کنار گذاشته شده؛ Debug APK نیز آن را صریحاً اجرا نمی‌کند |
| آزمون دستی/عملکرد/صدا/Nearby | اجرا نشد | دستگاه، Android SDK/adb و محیط شنیداری/رادیویی در اختیار نبود |

دستورهای دارای مانع یکسان، برای تولید شش خطای تکراری دوباره اجرا نشدند. موارد «اجرا نشد» exit code ساختگی ندارند. اسکریپت کمکی `tools/jvm-check/run.sh` نیز اجرا نشد؛ کامپایلر Kotlin آماده نیست و نتیجهٔ stub آن جای گیت‌های بالا را نمی‌گیرد.

## ۵. شواهد مستقل CI روی همان commit

**همهٔ زمان‌های این بخش UTC و از API سرویس CI هستند.** این اجراها پیش از نوبت حاضر ایجاد شده‌اند؛ عامل آن‌ها را در این نوبت trigger نکرده است.

| اجرا | شناسه / لینک | نتیجهٔ مشاهده‌شده |
|---|---|---|
| CI | [38384340348](https://github.com/PreGames-studio/Dorehami/actions/runs/38384340348) | `completed / success`؛ ایجادشده در `2026-10-13T10:34:55Z` |
| job آزمون‌های CI | [115252278632](https://github.com/PreGames-studio/Dorehami/actions/runs/38384340348/job/115252278632) | `success`؛ پایان `10:36:02Z` |
| job ساخت APK در CI | [115252278376](https://github.com/PreGames-studio/Dorehami/actions/runs/38384340348/job/115252278376) | `success`؛ پایان `10:36:31Z` |
| Debug APK | [38384340382](https://github.com/PreGames-studio/Dorehami/actions/runs/38384340382) | `completed / success`؛ همان head SHA |

annotation قابل دریافت job آزمون:

```text
checkAll: 138 tests, 0 failing, 7 module(s) with results
```

دستور واقعی workflow در همان نسخه:

```bash
./gradlew checkAll -x lint -x :server:test --stacktrace
```

بنابراین ادعای «همهٔ تست‌های سرور و lint سبزند» نادرست است. کلمهٔ `all JVM tests` در عنوان job، این دو استثنای واقعی را حذف نمی‌کند.

### متادیتای artifact APK

| فیلد | مقدار ثبت‌شده |
|---|---|
| نام artifact | `dorehami-debug-apk` |
| شناسه | `11804935943` |
| اجرای سازنده | `38384340382` |
| commit سازنده | `19115ea56bb358eb602c5434a21a2d1291b95a91` |
| حجم اعلام‌شدهٔ artifact | `34,202,230` بایت؛ **نه لزوماً حجم یک APK** |
| زمان ایجاد | `2026-10-13T10:36:05Z` |
| تاریخ انقضای اعلام‌شده | `2026-11-12T10:36:03Z`؛ در زمان بررسی منقضی نبود |
| digest اعلام‌شدهٔ artifact | `sha256:d588611f94d4d3b29dfa9c668954f2b1d7f5052e302c42857c98a40954e24961` |

این اطلاعات از API است. artifact در این نوبت دانلود/نصب نشده، digest باینری مستقل محاسبه نشده و hash بالا به عنوان hash یک APK استخراج‌شده معرفی نمی‌شود. دریافت برای کاربر از صفحهٔ اجرای Debug APK و بخش Artifacts امکان‌پذیر است تا زمانی که artifact معتبر و دسترسی حساب برقرار باشد.

### محدودیت دسترسی به لاگ خام

تلاش برای خواندن لاگ job با دستور زیر انجام شد:

```bash
gh run view 38384340348 --repo PreGames-studio/Dorehami \
  --job 115252278632 --log
```

**Exit code: 1**؛ میزبان `results-receiver.actions.githubusercontent.com` پاسخ قابل دریافت نداد (`EOF`). به همین دلیل گزارش به متادیتای job و annotation رسمی تکیه می‌کند، نه به ادعای خواندن XML همهٔ تست‌ها. آدرس‌های موقت امضاشده در گزارش ذخیره نشده‌اند.

### دستورهای خواندنی برای بازبینی شواهد

```bash
gh api --method GET repos/PreGames-studio/Dorehami/actions/runs \
  -f head_sha=19115ea56bb358eb602c5434a21a2d1291b95a91 -f per_page=20

gh api repos/PreGames-studio/Dorehami/actions/runs/38384340348/jobs

gh api repos/PreGames-studio/Dorehami/check-runs/115252278632/annotations

gh api repos/PreGames-studio/Dorehami/actions/runs/38384340382/artifacts
```

این دستورها فقط خواندنی‌اند. موفقیت API، موفقیت تست محلی محسوب نمی‌شود.

## ۶. موجودی آزمون‌ها و شکاف پوشش

این جدول از شمارش `@Test` در سورس همان commit تهیه شده است؛ **گزارش اجرا نیست**.

| ماژول | فایل آزمون/کمکی | متد `@Test` |
|---|---:|---:|
| `games/hokm/core` | 6 | 24 |
| `games/hokm/ui` | 0 | 0 |
| `core/protocol` | 0 | 0 |
| `core/gamesdk` | 2 | 6 |
| `core/session` | 1 | 7 |
| `core/testkit` | 0 | 0 |
| `transport/api` | 1 | 4 |
| `transport/online` | 0 | 0 |
| `transport/nearby` | 0 | 0 |
| `server` | 2 | 4 |

توزیع هستهٔ حکم: Rules برابر 7، Privacy برابر 4، Determinism برابر 4، Scoring برابر 4 و Variants برابر 5؛ فایل TestSupport آزمون مستقل ندارد. در سرور `ApplicationTest` یک و `RoomHubTest` سه متد آزمون دارد.

- **۲۴** = تعداد آزمون‌های تعریف‌شده در سورس حکم؛ **۱۳۸** = مجموع گزارش‌شدهٔ CI برای چند ماژول. این اعداد جای هم گزارش نمی‌شوند.
- نبود آزمون اختصاصی UI به معنی نبود همهٔ تست‌های یکپارچگی مربوط به حکم نیست؛ نشست و سرور هم از حکم استفاده می‌کنند.
- برای مرحله‌های آینده، screenshot/interaction/accessibility حکم و پوشش ربات ساده باید افزوده شوند؛ فقط شمارش فایل معیار کیفیت نیست.
- CI موفق با حذف سرور، رفتار HTTP/WS و امنیت اتاق را تأیید نمی‌کند.

## ۷. سناریوی پایهٔ دستگاه — تعریف شده، هنوز اجرا نشده

این دستورالعمل، نقطهٔ شروع قابل تکرار برای مقایسهٔ بعدی است؛ **هیچ screenshot، عدد FPS، اندازه‌گیری صدا یا نتیجهٔ انسانی برای آن در این نوبت تولید نشده است**.

### اطلاعاتی که پیش از اجرا باید ثبت شود

مدل گوشی، RAM، نسخهٔ Android، وضوح/density، نرخ نوسازی، اندازهٔ قلم، نوع اتصال، تنظیمات صدا/لرزش، نسخه و hash APK و زمان شروع. یک گوشی اقتصادی و یک میان‌ردهٔ واقعی انتخاب شوند؛ نام مدل بدون دراختیاربودن دستگاه جعل نشود.

### BS-01 — مسیر پایهٔ محلی

1. از APK همان commit، مسیر «بازی‌ها ← حکم» را باز کن؛ فارسی/RTL و قلم 100٪.
2. نام آزمون `بازیکن آزمون`، چهار بازیکن، امتیاز هدف ۱ و ربات حرفه‌ای؛ برای دور اول صدا/لرزش روشن. تنظیمات انتخاب‌شده را ثبت کن.
3. از صفحهٔ تنظیمات، تعیین حاکم، انتخاب حکم وقتی نوبت انسان است، میز دارای ۱۳ کارت، یک دور کامل، دست قبل، جدول امتیاز و پایان مسابقه شاهد بگیر.
4. یک کارت قانونی و یک کارت غیرمجاز را در موقعیت مناسب امتحان کن؛ رفتار واقعی، لمس لازم و متن خطا را ثبت کن.
5. همان مسیر را با صدای خاموش و قلم 200٪ بررسی کن؛ سپس خروج و ورود مجدد. مقایسهٔ ۲/۳ نفره و ربات ساده جدا گزارش شود.
6. برای تکرار تصویری دقیق، وضعیت/seed کنترل‌شده لازم است. UI فعلی ابزار مصوب انتخاب seed آزمون ندارد؛ بنابراین دورهای تصادفی متفاوت را golden برابر ننام. fixture/قلاب آزمون بعداً در HK-013 ساخته می‌شود.

### BS-02 — صدا و عملکرد

یک بازی ۳۰ دقیقه‌ای با دستگاه/تنظیمات ثبت‌شده، سپس بررسی frame time، حافظه، گرما و تأخیر صدای تماس. اندازه‌گیری Android با ابزار معتبر بعد از آماده‌شدن SDK انجام شود؛ شنیدن خروجی روی اسپیکر گوشی و هدفون نیازمند آزمون انسانی است. مشخصات فنی WAV به‌تنهایی «صدای طبیعی و باکیفیت» را اثبات نمی‌کند.

### BS-03 — اتاق و شبکه

ابتدا لابی و خطای اتصال به سرور آزمایشی را بررسی کن؛ سپس سناریوی چهار کلاینت، قطع/وصل و Nearby با دستگاه واقعی. برای این نوبت هیچ سرور، حساب واقعی یا جلسهٔ رادیویی راه‌اندازی نشده است. این تعریف سناریو مجوز فعال‌کردن سرویس تولید نیست.

## ۸. یافته‌ها، اولویت‌ها و تصمیم لازم

| ID | یافته | نوع/اولویت | اقدام بعدی |
|---|---|---|---|
| B-01 | JDK کامل/SDK و دریافت Gradle در محیط فعلی فراهم نیست | مانع محیط، P1 | اجرای گیت‌های کامل در runner معتبر؛ پیشنهاد HK-001.a |
| B-02 | سرور و lint در CI مبنا اجرا نمی‌شوند | شکاف شواهد، P1 | baseline دستی بدون استثنا؛ سپس اصلاح گیت پایدار در HK-002 |
| B-03 | annotation جمع کل تست‌ها موجود، لاگ خام در این محیط قابل دریافت نیست | قابلیت تشخیص، P2 | انتشار گزارش JUnit تفکیکی و خلاصهٔ ماژول‌ها در baseline/گیت بعدی؛ شکست در نبود گزارش مورد انتظار |
| B-04 | راهنماها compileSdk قدیمی دارند؛ توضیح سقف Gradle با wrapper همگام نیست | مستندات ابزار، P2 | همگام‌سازی محدود در کار ابزار/CI؛ بدون downgrade برای سبزکردن مصنوعی |
| B-05 | هشدار CI دربارهٔ Node.js 20 در `checkout@v4` و `setup-gradle@v4` و اجبار اجرا با Node.js 24 | نگه‌داری CI، P2 | سازگاری نسخهٔ actionها در HK-002 بررسی شود؛ این warning به‌تنهایی شکست build نیست |
| B-06 | annotation CI از مهاجرت `ubuntu-latest` به Ubuntu 26 از 2026-10-19 خبر می‌دهد | بازتولیدپذیری runner، P2 | تصویر runner و ابزارها در بررسی CI آگاهانه انتخاب/ثبت شوند؛ ارتقای بی‌بررسی نه |
| B-07 | آزمون اختصاصی UI حکم فعلاً وجود ندارد | پوشش کیفیت، P1/P2 | HK-002 و HK-013؛ هیچ ادعای تست‌شدن ظاهر/سنین بالاتر از CI کنونی نشود |

R-01 تا R-09 نقشهٔ راه همچنان یافته‌های خواندن کد هستند؛ در این نوبت هیچ‌کدام با اجرای تست محلی بازتولید یا رفع نشده‌اند.

## ۹. خروج از BLOCKED و ادامهٔ امن

دو مسیر معتبر وجود دارد:

1. **مسیر پیشنهادی:** تأیید HK-001.a، افزودن workflow دستیِ baseline، ثبت و ارسال تغییرات مرتبط فقط به برنچ کاری، اجرا و خواندن شواهد. این مسیر تست‌های کامل را می‌گیرد؛ HK-002 بعداً گیت دائمی CI را اصلاح می‌کند.
2. محیط توسعهٔ دارای JDK کامل 17، SDK واقعی API 37 و دسترسی به مخازن رسمی فراهم شود؛ سپس دستورهای زیر روی همین مبنا اجرا و XML/خروجی‌ها بررسی شوند.

```bash
# پیش‌نیاز: JDK کامل 17، Android SDK صحیح، مخازن رسمی در دسترس
./gradlew :games:hokm:core:test --no-daemon --stacktrace
./gradlew :core:gamesdk:test :core:session:test :transport:api:test --no-daemon --stacktrace
./gradlew :server:test --no-daemon --stacktrace
./gradlew checkAll --no-daemon --stacktrace
./gradlew :app:assembleDebug --no-daemon --stacktrace
./gradlew :app:lint --no-daemon --stacktrace
```

اگر اجرای واقعی یک تست/کامپایل در محیط معتبر شکست بخورد، همان شکست یافتهٔ معتبر baseline است و بدون اجازه باگ رفع نمی‌شود. اما شکست دریافت ابزار پیش از رسیدن به کد، جای اجرای آن تست را نمی‌گیرد.

### چک‌لیست تحویل این نوبت

- [x] تأیید و دامنهٔ HK-001 ثبت شد؛ برنچ و تغییرهای قبلی حفظ شدند.
- [x] نسخه‌های واقعی و موجودی آزمون‌ها مشخص شد.
- [x] تلاش اجرای محلی با exit code و تفکیک خطای محیط ثبت شد.
- [x] CI موفق و artifact قبلی به همان commit تطبیق داده شدند؛ استثناها پنهان نشدند.
- [x] سناریوهای پایهٔ دستگاه تعریف شدند؛ اجرای آن‌ها ادعا نشد.
- [x] هیچ کد بازی، تنظیم ساخت، dependency یا workflow تغییر نکرد.
- [ ] گیت کامل بدون حذف سرور/lint در محیط معتبر اجرا شود.
- [ ] گزارش تفکیکی آزمون‌ها و خروجی build کامل به این پرونده افزوده شود.
- [ ] آزمون دستگاه، هرجا لازم و در دسترس شد، شاهد مستقل داشته باشد.

**نتیجه:** گزارش تشخیصی تحویل شد؛ مرحلهٔ HK-001 به دلیل مانع محیط و شکاف گیت‌های CI هنوز `BLOCKED` است. کار بعدی رفع همین مانع است، نه شروع گرافیک یا ردشدن خودکار از مرحلهٔ اول.
