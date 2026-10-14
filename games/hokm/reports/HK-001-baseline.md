# HK-001 — گزارش خط پایهٔ ساخت و آزمون حکم

> تاریخ بررسی: **2026-10-13**، منطقهٔ زمانی گزارش: Asia/Tehran.
> [نقشهٔ راه حکم](../ROADMAP.md) · [راهنمای عامل حکم](../AGENTS.md)
> **وضعیت نهایی: HK-001.a = DONE، HK-001.b = DONE، HK-001 = DONE.** full baseline روی commit اصلاح‌شده سبز شد؛ workflow/JUnit/log/APK artifactها ثبت شدند. آزمون دستگاه اجرا نشده و جداگانه گزارش می‌شود؛ HK-002 هنوز شروع نشده است.

## ۱. خلاصهٔ قابل تصمیم‌گیری

1. در سه run baseline، کد بازی تغییر نکرد. پس از مشاهدهٔ lint error، کاربر رفع محدود را خواست؛ زیرمرحلهٔ HK-001.b فقط container داخلی خط 661 را از `BoxWithConstraints` به `Box` تبدیل کرده است. dependency و تنظیم build و CI عادی تغییر نکرده‌اند؛ path trigger baseline فقط همان فایل هدف را هم پوشش می‌دهد.
2. برای **همان commit مبنا**، دو اجرای قبلی و موفق CI و Debug APK پیدا و هویت commit آن‌ها تطبیق داده شد.
3. annotation رسمی CI گزارش می‌کند: **۱۳۸ آزمون، صفر شکست، ۷ ماژول دارای نتیجه**. آن اجرای قبلی `server:test` و lint را مستثنا کرده است؛ معادل اجرای کامل خط پایه نیست.
4. artifact نسخهٔ debug برای همان commit وجود دارد. آزمون دستگاه انجام نشده؛ وجود APK با بررسی آن روی گوشی یکی نیست.
5. این محیط در ابتدا JDK کامل، Gradle، Kotlin compiler، Android SDK و adb نداشت. runtime موقت Java 17 آماده شد، ولی `javac` در آن نبود.
6. اجرای محلی تست حکم، پیش از پیکربندی پروژه، در دریافت Gradle با خطای TLS متوقف شد؛ دریافت archive از GitHub نیز در مقصد release-assets شکست خورد.
7. کاربر تأیید HK-001 را داد و سپس خواست مراحل با برنچ تازه ادامه یابد. **به علت ثابت‌بودن برنچ Arena، شاخهٔ دیگری ایجاد نمی‌شود**؛ تمام تغییرها فقط روی `arena/05a354c8-dorehami` هستند. پس از شواهد اصلاح‌شده، مرحلهٔ والد جمع‌بندی می‌شود؛ HK-002 خودکار آغاز نمی‌شود.
8. اولین اجرای full baseline روی [run `38401546325`](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325) برای commit `564ca5b` شروع شد: action API برای گام‌های حکم، سرور، `checkAll`، APK و lint conclusion سبز نشان می‌داد، اما آن‌ها `continue-on-error` بودند؛ gate خراب بود و outcome واقعی هیچ‌کدام را جداگانه ثبت نکرد. job به‌دلیل `fi` اضافه در gate fail شد. بنابراین نتیجهٔ واقعی آن گام‌ها در run اول نامعلوم است.
9. اجرای اصلاح‌شده روی [run `38403237601`](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601)، commit `e08bd50`: outcome واقعی حکم، سرور، APK و lint موفق؛ `checkAll` ناموفق. خلاصهٔ runner: **۲۰ JUnit XML، ۱۱۹ تست، صفر assertion failure/error**؛ علت هنوز روشن نبود.
10. [اجرای سوم `38404495408`](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495408) روی `2a12b4c` علت را مشخص کرد: task `:games:hokm:ui:lintDebug` با `UnusedBoxWithConstraintsScope` در `HokmEntry.kt:661` شکست داشت (۱ error، ۱۷ warning، ۴ hint). حکم/سرور/APK و `:app:lint` صریح موفق شدند؛ ۲۰ XML/۱۱۹ تست/۰ failure/error. در آن زمان HK-001 هنوز BLOCKED بود؛ زیرمرحلهٔ بعدیِ مصوب در run چهارم خطا را رفع و gate را سبز کرد.
11. CI معمول همان commit، [run `38404495547`](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495547)، `success` است؛ CI معمول `-x lint -x :server:test` اجرا می‌کند و به‌هیچ‌وجه شکست baseline کامل را خنثی نمی‌کند.
12. کاربر پس از پیشنهاد تغییر محدود گفت «بهترین شکل عمل کن». HK-001.b فقط container داخلی در `HokmEntry.kt:661` را به `Box` تبدیل کرد و path trigger را به همین فایل دقیق افزود.
13. [run چهارم `38407327112`](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112) روی `224c2cfe6b19af2ffb3e725b4b86328044685688` کامل و **success** شد: core/server tests، `checkAll` بدون exclusion، APK، app lint، summary/uploads و gate نهایی همگی success. Summary status نیز success بود. JUnit XMLهای run چهارم در artifact گزارش موجودند؛ run قبلی ۲۰ XML/۱۱۹ تست/۰ شکست داشت، اما شمار تفکیکی run چهارم از API JSON قابل خواندن نبود و در این گزارش برای run چهارم حدس زده نمی‌شود.
14. این شواهد معیار HK-001 را می‌بندد؛ HK-001.a/.b و parent `HK-001` = `DONE`. آزمون دستگاه/عملکرد/صدا مستقل است و این run جای آن را نمی‌گیرد. HK-002 خودکار شروع نمی‌شود.

### نتیجهٔ HK-001.a — DONE (اجرای baseline و علت‌یابی)

commitهای `8f0520a`, `564ca5b`, `e08bd50` و `2a12b4c` فقط روی برنچ ثابت push شدند. dispatch دستی به‌ترتیب 404 (workflow هنوز در default branch نبود) و 403 (محدودیت مجوز integration) داد؛ trigger محدود push سه run خودکار را آغاز کرد. run نخست خطای syntax در gate (`fi` اضافه) داشت؛ با `bash -n` بازتولید و رفع شد. run دوم outcome `checkAll` را failure نشان داد؛ اجرای سوم excerpt از log را منتشر کرد و علت را مشخص ساخت: `:games:hokm:ui:lintDebug`، خط `HokmEntry.kt:661`، `UnusedBoxWithConstraintsScope` (۱ error، ۱۷ warning، ۴ hint). در run سوم تست حکم/سرور، APK و app lint موفق و JUnit برابر ۲۰ XML/۱۱۹ تست/۰ شکست بود. بنابراین اجرای تشخیصی کامل و ثبت‌شده است؛ در دامنهٔ HK-001.a هیچ تغییر Kotlin/build/dependency انجام نشد. خطای lint در HK-001.b با تأیید کاربر اصلاح شد و full baseline سبز شد. آزمون دستی روی گوشی اجرا نشده است.

### HK-001.b — اصلاح محدود lint و rerun (DONE)

- **تأیید:** کاربر پس از پیشنهاد صریح «بهترین شکل عمل کن» را گفت؛ اجرا فقط به خطای مشخص‌شده و rerun baseline محدود شد.
- **تغییر کد worktree:** همان `BoxWithConstraints` داخلی در `games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui/HokmEntry.kt:661` به `Box` تبدیل شد؛ چون scope constraints استفاده نمی‌شود. `Box` از پیش import شده بود؛ import دیگر/outer `BoxWithConstraints` خط 610 تغییر نکرد.
- **تغییر trigger workflow:** `push.paths` همان برنچ ثابت اکنون فقط workflow و همین فایل `HokmEntry.kt` را شامل می‌شود؛ هیچ path عمومی‌تری اضافه نشده تا commit کد baseline اجرا کند.
- **تست:** انتظار runner `ubuntu-24.04` برای `:games:hokm:ui:lintDebug`, core/server tests, full `checkAll`, APK و explicit app lint، همراه summary/JUnit/artifacts. تست محلی Android در این محیط در دسترس نیست و ادعا نمی‌شود.
- **معیار پایان:** lint هدف و تمام گیت‌ها نتیجهٔ واقعی داشته باشند؛ اگر سبز شد HK-001 والد قابل جمع‌بندی است. اگر خطای دیگری ظاهر شد، فقط گزارش و قبل از گسترش scope تأیید تازه.
- **خارج از دامنه:** تغییر warningهای دیگر، lint baseline/استثنا، بازطراحی/رفتار بازی، merge/release/deploy و آغاز HK-002.

## ۲. دامنه و منشأ داده‌ها

| موضوع | مقدار/شاهد |
|---|---|
| تأیید کاربر | پاسخ «تایید است» به پیشنهاد اجرای HK-001 |
| برنچ کاری | `arena/05a354c8-dorehami`؛ تعویض نشد |
| commit مبنا برای کد بازی | `19115ea56bb358eb602c5434a21a2d1291b95a91` |
| commit اول مستندات/workflow | `8f0520a001c8a3f13cb18abb4125b0a8522ac370`؛ push شد |
| commit اجرای baseline اول | `564ca5ba3c7bdc3382188654f16a5cab52c251ea`؛ فقط workflow/گزارش، بدون تغییر Kotlin/build |
| commit outcome واقعی | `e08bd502f5c2bea3c26b998bc5f6d117521a6620`؛ gate پایانی اصلاح شد؛ کد بازی تغییر نکرد |
| commit تشخیص lint | `2a12b4c8023d1b586c7635dc93d25eadfd82afd8`؛ فقط استخراج/annotation diagnostics و گزارش؛ کد بازی تغییر نکرد |
| commit fix و trigger دقیق | `224c2cfe6b19af2ffb3e725b4b86328044685688`؛ همان تغییر یک‌خطی و path-filter؛ baseline سبز روی همین SHA |
| عنوان commit مبنا | `Merge pull request #9 from PreGames-studio/arena/18d1c9f6-dorehami` |
| تغییرهای موجود ابتدای نوبت | `AGENTS.md`، `docs/ROADMAP.md`، `games/hokm/README.md` و دو سند جدید `games/hokm/AGENTS.md` و `games/hokm/ROADMAP.md`؛ همگی مستندات نوبت قبل |
| فایل‌های مرتبط این نوبت | workflow مستقل `.github/workflows/hokm-baseline.yml`، گزارش و roadmap؛ trigger روی branch ثابت به workflow و فایل هدف دقیق `games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui/HokmEntry.kt` محدود است |
| کد و تنظیمات نسبت به مبنا | سه اجرای اولیه کد را تغییر ندادند؛ زیرمرحلهٔ تأییدشدهٔ HK-001.b فقط container داخلی UI را از `BoxWithConstraints` به `Box` تغییر می‌دهد. Dependency/Gradle/CI عادی دست‌نخورده‌اند |
| عملیات خارجی | commitهای `8f0520a`, `564ca5b`, `e08bd50`, `2a12b4c`, `99ede1f`, `224c2cf` فقط به برنچ ثابت push شدند؛ run `38407327112` روی `224c2cf` full baseline را سبز کرد. dispatch دستی قبلی 404/403؛ PR/merge/release انجام نشده |

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

### اجرای تازهٔ خط پایه — commit `564ca5b`

| کار | شاهد API روی اجرای `38401546325` |
|---|---|
| workflow | [Hokm Baseline](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325)، `push`، branch `arena/05a354c8-dorehami`، commit `564ca5ba3c7bdc3382188654f16a5cab52c251ea` |
| job | [Full build and test baseline](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325/job/115310166453)، مدت ثبت‌شده حدود ۷ دقیقه و ۴۶ ثانیه؛ **نتیجهٔ کل: failure** |
| تست حکم | action conclusion `success`؛ چون `continue-on-error` فعال بود، outcome واقعی از API این run معلوم نشد؛ شمار آزمون نیز در دسترس نبود |
| تست سرور | action conclusion `success`؛ outcome واقعی از این run قابل تأیید نیست (`continue-on-error`) |
| تمام checkهای Gradle | action conclusion `success`؛ outcome واقعی از این run قابل تأیید نیست (`continue-on-error`) |
| APK | action conclusion `success`؛ build واقعی نیز از این run به‌تنهایی قابل تأیید نیست (`continue-on-error`)، artifact debug بارگذاری شد |
| lint اپ | فرمان اجرا شد و action conclusion `success` بود؛ outcome واقعی از این run قابل تأیید نیست (`continue-on-error`). گزارش summary/JUnit بارگذاری شد، ولی در این sandbox قابل خواندن نبود |
| خلاصه و uploadها | report step و هر دو upload با conclusion `success`؛ فایل خلاصه و XML به artifact پیوست شدند |
| علت قابل اثبات failure کل | gate Bash آخر workflow در commit اولیه یک `fi` اضافه داشت. با استخراج متن همان step و `bash -n`، `syntax error near unexpected token 'fi'` بازتولید شد؛ اصلاح شد. این خطای workflow به‌تنهایی run را fail می‌کرد، اما چون gate نتوانست `steps.*.outcome` را ثبت کند، از این run نمی‌توان سلامت تست‌ها/build را نتیجه گرفت |

**artifactهای این اجرای نخست** (موقت؛ انقضای اعلام‌شده `2026-10-27`):

- [log و گزارش JUnit](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325/artifacts/11815577585) — artifact `11815577585`, 30,735 bytes، API digest `sha256:e308263a017a92d10ac50ac45fc77cba8ef29bf1148fc18fb40ec3b5fc470c6c`.
- [debug APK](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325/artifacts/11816047359) — artifact `11816047359`, 17,773,024 bytes، API digest `sha256:beee8ea4abe16c15c3f59faf9e69b90c1ee27e1fe4a96e062b4ec476d0d77c43`.

این digestها متادیتای artifact آرشیوشده‌اند؛ hash APK استخراج‌شده نیستند. sandbox نتوانست لاگ خام را از `results-receiver.actions.githubusercontent.com` یا ZIPها را از `productionresultssa6.blob.core.windows.net` بگیرد (میزبان‌ها خارج از دسترسی محیط بودند). فایل‌ها از صفحهٔ workflow برای کاربری که دسترسی GitHub دارد قابل دریافت‌اند. ساخت APK نیز آزمون دستی روی گوشی محسوب نمی‌شود.

### اجرای دومِ baseline — commit `e08bd50`

| گیت/مدرک | نتیجهٔ واقعی در run `38403237601` |
|---|---|
| workflow/job | [Hokm Baseline](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601) · [job `115316004947`](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601/job/115316004947) · commit `e08bd502f5c2bea3c26b998bc5f6d117521a6620` · پایان `13:18:38Z` · ۵:۱۰ · **کل job: failure** |
| تست هستهٔ حکم | outcome `success` |
| تست سرور واقعی Ktor | outcome `success` |
| `checkAll` کامل، بدون exclusion | **outcome `failure`**؛ خطای workflow اول نیست. چون test XML صفر شکست دارد، شکست در task/مرحلهٔ دیگری از aggregate است؛ نام task/علت دقیق از annotation عمومی حاضر به‌دست نیامد |
| `:app:assembleDebug` | outcome `success`؛ APK artifact بارگذاری شد |
| `:app:lint` | outcome `success`؛ گزارش اصلاح‌شدهٔ gate outcome را خواند، پس این بار نتیجهٔ زیرین `continue-on-error` هم PASS بوده است |
| JUnit | **۲۰ فایل XML، ۱۱۹ تست، صفر failure/error**؛ این شمارش فقط تست‌هاست و موفق‌بودن `checkAll` را ثابت نمی‌کند |
| upload summary/JUnit و APK | موفق؛ `check-all.log` نیز داخل artifact گزارش قرار گرفت |
| final gate | failure درست و مورد انتظار، چون outcome واقعی `checkAll` failure بود؛ هیچ تست قرمزی از XML گزارش نشد |

Artifactهای اجرای دوم، با انقضای اعلام‌شدهٔ `2026-10-27`:

- [خلاصه، لاگ Gradle و JUnit XMLها](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601/artifacts/11815519697) — ID `11815519697`, 30,918 bytes; digest API: `sha256:fdbb67282c594bf5e16bd434c8032125d5527c02807fda83218f8fa3e195bd48`.
- [debug APK](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601/artifacts/11815739589) — ID `11815739589`, 17,773,024 bytes; digest API: `sha256:b0b6dca8218a2acb8b5e982ee5297b3486e47d9f528e88574d356cd0b6d79525`.

### اجرای سوم و علت نهایی — commit `2a12b4c`

| گیت/مدرک | نتیجهٔ واقعی در run `38404495408` |
|---|---|
| workflow/job | [Hokm Baseline](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495408) · [job `115320424519`](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495408/job/115320424519) · commit `2a12b4c8023d1b586c7635dc93d25eadfd82afd8` · پایان `13:30:14Z` · **کل job: failure** |
| تست حکم | outcome `success` |
| تست سرور واقعی Ktor | outcome `success` |
| `:app:assembleDebug` | outcome `success`؛ APK artifact ساخته/بارگذاری شد |
| `:app:lint` | outcome `success` |
| `./gradlew checkAll` بدون exclusion | outcome `failure`؛ annotation واقعی علت را ثبت کرد: task `:games:hokm:ui:lintDebug` fail شده است |
| JUnit | ۲۰ XML، ۱۱۹ تست، صفر failure/error؛ رد شدن checkAll از تست‌های assertion نیست |
| خلاصه و uploadها | موفق؛ XMLها، log شامل `check-all.log` و APK بارگذاری شدند |
| final gate | به‌درستی failure داد چون outcome واقعی `checkAll` برابر failure بود؛ دیگر خطای syntax در gate وجود نداشت |

**علت دقیق lint در `checkAll`:** `UnusedBoxWithConstraintsScope` در `games/hokm/ui/src/main/kotlin/ir/dorehami/games/hokm/ui/HokmEntry.kt:661`. گزارش Gradle از **۱ error، ۱۷ warning و ۴ hint** می‌گوید. در همان فایل، محدودهٔ 661 یک `BoxWithConstraints` فرزند است که scope constraints را استفاده نمی‌کند؛ container بیرونی در خط 610 از `maxWidth` استفاده می‌کند. این نتیجه توضیح می‌دهد چرا `:app:lint` جداگانه سبز است، ولی `checkAll` که verificationهای ماژول را می‌گیرد سبز نمی‌شود.

در run سوم فقط خطا ثبت شد و هیچ تغییری در Kotlin، baseline lint، Gradle task یا exclusion اعمال نشد. سپس کاربر تغییر حداقلی را تأیید کرد؛ در HK-001.b همان container داخلی از `BoxWithConstraints` به `Box` تبدیل و در run چهارم lint/full baseline سبز شد.

Artifactهای اجرای سوم (انقضای اعلام‌شده `2026-10-27`):

- [گزارش، summary، log و JUnit XMLها](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495408/artifacts/11819325974) — ID `11819325974`, 31,900 bytes; digest API `sha256:c741850e83b9243aa2ac326f11bfba67c081bbd621943d0627a032c9d81620e3`.
- [debug APK](https://github.com/PreGames-studio/Dorehami/actions/runs/38404495408/artifacts/11818611767) — ID `11818611767`, 17,773,024 bytes; digest API `sha256:45e2354f29a909144ad3f451695000805397a7f962e991c152bdf2fde19dfb6f`.

### اجرای چهارم — baseline سبز روی اصلاح مصوب (`224c2cf`)

| گیت/مدرک | نتیجهٔ run `38407327112` |
|---|---|
| workflow/job | [Hokm Baseline](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112) · [job `115330246490`](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112/job/115330246490) · SHA `224c2cfe6b19af2ffb3e725b4b86328044685688` · پایان `13:51:12Z` · ۷:۲۶ · **success** |
| تست هستهٔ حکم | `success` |
| `:server:test` با Ktor واقعی | `success` |
| `./gradlew checkAll --no-daemon --stacktrace` | `success`، بدون `-x lint` یا `-x :server:test` |
| `:app:assembleDebug` | `success`؛ APK ساخته و بارگذاری شد |
| `:app:lint` | `success` |
| خلاصهٔ JUnit | summary status `success`؛ gate آن را بررسی کرد. تعداد دقیق XML/test در metadata قابل دسترس API این محیط نبود؛ در summary و XML داخل artifact گزارش است و از run قبل به‌جای آن استفاده نشده است |
| final gate و uploadها | `success`؛ summary/log/JUnit و APK هر دو بارگذاری شدند |
| annotation باقی‌مانده | فقط هشدار deprecated شدن Node.js 20 در actionهای استفاده‌شده؛ علت failure نبود و run سبز ماند |

**Artifactهای run چهارم** (انقضای اعلام‌شده `2026-10-27`):

- [گزارش، summary، log و JUnit XMLها](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112/artifacts/11820780122) — ID `11820780122`, 32,467 bytes; digest API `sha256:5f0ffe46ea598634cf2fc3c46c96ab4b998e84058735b6d840d3fab7f0934496`.
- [debug APK](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112/artifacts/11820070925) — ID `11820070925`, 17,773,024 bytes; digest API `sha256:98e70cedc4c8820a10807b04edb98bbfe2ffa2cfd37e4e5e7917a822577a98be`.

Digestها به آرشیو artifact تعلق دارند، نه الزاماً فایل APK استخراج‌شده. خروجی APK فقط debug artifact است؛ دانلود، نصب روی گوشی، امضا برای release، انتشار یا deploy انجام نشده است.

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
| B-08 | gate نهایی workflow baseline در اجرای اول به‌خاطر `fi` اضافه syntax error داشت | خطای ابزار baseline، P1، **رفع شد** | با `bash -n` بازتولید؛ `fi` حذف شد؛ اجرای دوم gate را درست اجرا کرد و outcome واقعی را ثبت کرد |
| B-09 | run سوم `checkAll` به lint `UnusedBoxWithConstraintsScope` در `HokmEntry.kt:661` برخورد کرد | یافتهٔ baseline، **resolved** با اصلاح حداقلیِ مصوب | container داخلی به `Box` تغییر کرد؛ run چهارم lint و `checkAll` را سبز کرد. ۱۷ warning/۴ hint اجرای قبلی باقی ماندند ولی error رفع شد؛ baseline lint یا حذف task به کار نرفت |

R-01 تا R-09 نقشهٔ راه همچنان یافته‌های خواندن کد هستند؛ در این نوبت هیچ‌کدام با اجرای تست محلی بازتولید یا رفع نشده‌اند.

## ۹. خروج از BLOCKED و ادامهٔ امن

[run چهارم `38407327112`](https://github.com/PreGames-studio/Dorehami/actions/runs/38407327112) برای commit `224c2cf` تمام گیت‌های baseline را سبز کرد؛ بنابراین **HK-001 از حالت `BLOCKED` خارج و `DONE` شد**. رفع lint تنها تغییر کد این زیرمرحله بود؛ نه baseline lint اضافه شد و نه `checkAll`/server/lint از گیت حذف شدند.

شواهد اجرای نهایی:

- Full `checkAll` و `:games:hokm:ui:lintDebug` موفق؛ تست حکم و سرور واقعی Ktor موفق.
- Debug APK ساخته و به عنوان artifact بارگذاری شد؛ `:app:lint` جداگانه هم موفق.
- Summary status و final gate موفق؛ XML/log و APK در artifactهای linked بالا هستند.
- JUnit summary همان run از metadata API قابل خواندن نبود؛ به همین دلیل این گزارش برای run چهارم عددی حدس نمی‌زند. summary/XML داخل artifact گزارش موجود است. اجرای قبل از fix ۲۰ XML/۱۱۹ تست/صفر failure داشت.
- آزمون روی دستگاه، FPS، صدا، Nearby و usability اجرا نشده‌اند و جداگانه در بخش ۷ تعریف شده‌اند.

دستورهای زیر برای تکرار دستی در محیط دارای JDK/SDK معتبر نگه داشته شده‌اند؛ آن‌ها نتیجهٔ مستقل تازه‌ای برای این گزارش تولید نکردند.

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
- [x] کد UI فقط در container داخلی یک خط تغییر کرد؛ dependency و Gradle/build نسخه‌ای تغییر نکردند؛ trigger فقط workflow و همین فایل هدف را می‌پوشاند.
- [x] commitهای baseline/diagnosis/fix فقط روی branch ثابت Arena push شدند؛ run چهارم نیز از روی push فایل هدف خودکار اجرا شد.
- [x] سه اجرای اول bugهای workflow و lint را آشکار کردند؛ gate outcome واقعی را خواند و علت lint ثبت شد.
- [x] اصلاح محدود `BoxWithConstraints` داخلی به `Box` انجام شد؛ نه lint baseline اضافه شد، نه task حذف شد.
- [x] run چهارم `38407327112` روی `224c2cf` success: حکم/سرور/`checkAll` بدون exclusion/APK/app lint/summary/final gate همگی success.
- [x] summary status، logs/JUnit و debug APK به artifactهای run چهارم پیوست و metadata ثبت شد.
- [x] HK-001.a و HK-001.b کامل؛ HK-001 بر اساس معیار cloud baseline `DONE` شد.
- [ ] HK-002 به‌صورت مرحله‌ای پیشنهاد و برایش تأیید مستقل گرفته شود؛ خودکار شروع نشود.
- [x] آزمون دستگاه/عملکرد/صدا در این زیرمرحله انجام نشد و صادقانه جدا از گیت ابری باقی ماند.

**نتیجهٔ نهایی:** `HK-001.a = DONE`; `HK-001.b = DONE`; `HK-001 = DONE` بر اساس full cloud baseline سبز روی SHA `224c2cfe6b19af2ffb3e725b4b86328044685688`. عدد دقیق تست‌های run چهارم در artifact summary است و از API metadata قابل بازیابی نبود؛ gate سبز و artifact موجودند. هیچ merge/release/deploy یا آزمون دستگاه انجام نشده است. `HK-002` هنوز `TODO` و منتظر پیشنهاد/تأیید تازه است.
