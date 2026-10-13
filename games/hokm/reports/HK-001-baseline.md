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
7. کاربر تأیید HK-001 را داد و سپس خواست مراحل با برنچ تازه ادامه یابد. **به علت ثابت‌بودن برنچ Arena، شاخهٔ دیگری ایجاد نمی‌شود**؛ تمام تغییرها فقط روی `arena/05a354c8-dorehami` هستند. پس از شواهد اصلاح‌شده، مرحلهٔ والد جمع‌بندی می‌شود؛ HK-002 خودکار آغاز نمی‌شود.
8. اولین اجرای full baseline روی [run `38401546325`](https://github.com/PreGames-studio/Dorehami/actions/runs/38401546325) برای commit `564ca5b` شروع شد: action API برای گام‌های حکم، سرور، `checkAll`، APK و lint conclusion سبز نشان می‌داد، اما آن‌ها `continue-on-error` بودند؛ gate خراب بود و outcome واقعی هیچ‌کدام را جداگانه ثبت نکرد. job به‌دلیل `fi` اضافه در gate fail شد. بنابراین نتیجهٔ واقعی آن گام‌ها در run اول نامعلوم است.
9. اجرای اصلاح‌شده روی [run `38403237601`](https://github.com/PreGames-studio/Dorehami/actions/runs/38403237601)، commit `e08bd50`: outcome واقعی حکم، سرور، APK و lint موفق؛ `checkAll` ناموفق. خلاصهٔ runner: **۲۰ JUnit XML، ۱۱۹ تست، صفر assertion failure/error**؛ بنابراین گیت را شکست یک check غیرتستی داخل `checkAll` می‌داند، نه تست قرمز. علت/task دقیق از annotationها معلوم نشد؛ برای اجرای بعد excerpt از `check-all.log` به summary/annotation افزوده می‌شود.

### اقدام فعال — HK-001.a

commitهای `8f0520a`, `564ca5b` و `e08bd50` فقط روی برنچ ثابت push شدند. dispatch دستی به‌ترتیب 404 (workflow هنوز در default branch نبود) و 403 (محدودیت مجوز integration) داد؛ trigger محدود push خودکار اجرا شد. run نخست gate Bash را به‌خاطر `fi` اضافه شکست داد؛ syntax با `bash -n` بازتولید و اصلاح شد. outcome گام‌های `continue-on-error` در آن run نامعلوم ماند، هرچند action conclusion آن‌ها سبز بود. run دوم gate را درست اجرا کرد و مشخص کرد `checkAll` واقعاً outcome شکست دارد، با اینکه حکم/سرور/APK/lint موفق و ۱۱۹ تست از ۲۰ XML بدون شکست بودند. artifact logs/JUnit بارگذاری شدند اما میزبان دانلود ZIP/log از sandbox قابل دسترسی نبود؛ علت دقیق checkAll هنوز معلوم نیست. workflow برای ارسال excerpt خطا به annotation/summary تقویت می‌شود و run سوم لازم است. **این‌ها نتیجهٔ workflow هستند، نه آزمون دستی روی گوشی.** شاخهٔ دیگر، merge، انتشار APK یا deploy تولیدی انجام نمی‌شود.

## ۲. دامنه و منشأ داده‌ها

| موضوع | مقدار/شاهد |
|---|---|
| تأیید کاربر | پاسخ «تایید است» به پیشنهاد اجرای HK-001 |
| برنچ کاری | `arena/05a354c8-dorehami`؛ تعویض نشد |
| commit مبنا برای کد بازی | `19115ea56bb358eb602c5434a21a2d1291b95a91` |
| commit اول مستندات/workflow | `8f0520a001c8a3f13cb18abb4125b0a8522ac370`؛ push شد |
| commit اجرای baseline اول | `564ca5ba3c7bdc3382188654f16a5cab52c251ea`؛ فقط workflow/گزارش عوض شد، Kotlin و تنظیمات build تغییر نکردند |
| عنوان commit مبنا | `Merge pull request #9 from PreGames-studio/arena/18d1c9f6-dorehami` |
| تغییرهای موجود ابتدای نوبت | `AGENTS.md`، `docs/ROADMAP.md`، `games/hokm/README.md` و دو سند جدید `games/hokm/AGENTS.md` و `games/hokm/ROADMAP.md`؛ همگی مستندات نوبت قبل |
| فایل‌های تازهٔ این نوبت | workflow مستقل `.github/workflows/hokm-baseline.yml` و این گزارش؛ trigger آن فقط با تغییر همین workflow در برنچ ثابت اجرا می‌شود |
| کد و تنظیمات نسبت به مبنا | بدون تغییر؛ بنابراین شواهد CI آن commit برای کد مبنا مرتبط‌اند، نه شاهد اجرای تازه در sandbox |
| عملیات خارجی | commitهای `8f0520a`, `564ca5b`, `e08bd50` فقط به همان برنچ push شدند؛ run `38401546325` خطای syntax در gate داشت؛ run `38403237601` گیت را درست اجرا کرد اما `checkAll` fail شد؛ artifactهای XML/log و APK هر دو run بارگذاری شدند. dispatch دستی 404/403؛ PR/merge/release انجام نشد؛ diagnostic excerpt و run سوم لازم است |

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

برای اجرای بعدی، workflow از همان `check-all.log` بخش کوتاه `FAILURE / What went wrong` را به summary و API annotation می‌فرستد؛ علت task و هر خطای غیرتستی باید از آن استخراج شود پیش از هر تصمیم دربارهٔ تغییر محصول یا build.

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
| B-09 | `checkAll` در run دوم outcome failure داد، در حالی‌که ۱۱۹ تست/۲۰ XML صفر failure/error دارند | شکست task غیرتستی یا سایر checkها، P1؛ علت دقیق نامشخص | workflow excerpt را از `check-all.log` به annotation/summary بیفزاید و run سوم را بگیرد؛ پس از علت‌یابی تصمیم جداگانه، نه تغییر بی‌اجازهٔ کد محصول |

R-01 تا R-09 نقشهٔ راه همچنان یافته‌های خواندن کد هستند؛ در این نوبت هیچ‌کدام با اجرای تست محلی بازتولید یا رفع نشده‌اند.

## ۹. خروج از BLOCKED و ادامهٔ امن

دو مسیر معتبر وجود دارد:

1. **مسیر فعال:** run اول (`38401546325`) به‌خاطر syntax gate fail شد و اصلاح گردید. run دوم (`38403237601`) gate درست کار کرد: حکم/سرور/APK/lint موفق، ولی `checkAll` outcome failure با ۲۰ XML/۱۱۹ تست/۰ assertion failure داد. لاگ raw از sandbox دانلودپذیر نیست؛ برای run بعدی استخراج `What went wrong` از log داخلی و ارسال به annotation/summary به workflow افزوده می‌شود. علت را ثبت و گزارش کن؛ merge لازم نیست.
2. اگر runner ابری/گزارش‌دهی بعد از diagnostic اصلاح‌شده باز هم کار نکند، محیط توسعهٔ دارای JDK کامل 17، SDK واقعی API 37 و دسترسی به مخازن رسمی فراهم شود؛ سپس دستورهای زیر روی همین مبنا اجرا و XML/خروجی‌ها بررسی شوند.

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
- [x] هیچ کد بازی، تنظیم build یا dependency تغییر نکرد؛ workflow مستقل روی trigger محدود آماده و در runner واقعی اجرا شد.
- [x] commitهای `8f0520a`, `564ca5b` و `e08bd50` فقط روی برنچ ثابت Arena push شدند؛ path-trigger هر دو اجرای baseline را آغاز کرد.
- [x] اجرای نخست gate syntax error داشت؛ علت با `bash -n` بازتولید و `fi` اضافه حذف شد.
- [x] اجرای دوم outcome واقعی را ثبت کرد: حکم/سرور/APK/lint موفق؛ `checkAll` شکست؛ ۲۰ JUnit XML، ۱۱۹ تست و صفر assertion failure/error.
- [x] خلاصهٔ/API annotation/artifact metadata ثبت شد؛ task دقیق checkAll از log قابل دانلود در این sandbox نبود.
- [ ] workflow برای چاپ excerpt تشخیصی `check-all.log` و اسناد به‌روز شده روی branch ثابت commit/push شود.
- [ ] run سوم task/علت شکست `checkAll` را روشن کند و نتیجهٔ کامل، به‌خصوص build/checkAll بدون استثنا، ثبت شود.
- [ ] `HK-001` پس از گیت نهایی موفق یا ثبت علت blocker به‌صورت شواهد کامل جمع‌بندی شود؛ HK-002 خودکار آغاز نشود.
- [ ] آزمون دستی/عملکرد/صدا روی دستگاه، هرجا در دسترس شد، شاهد مستقل داشته باشد.

**نتیجهٔ جاری:** `HK-001` هنوز `BLOCKED` و `HK-001.a` فعال است. اجرای دوم `38403237601` اجرای درست workflow را ثابت کرد، نه پاس‌شدن baseline: تست حکم، سرور، APK و lint پاس شدند، ولی `checkAll` failure بود؛ شمار JUnit ۱۱۹/۰ خطا، پس احتمالاً شکست در task غیرتستی و علتش هنوز نامشخص است. اصلاح بعدی فقط diagnostics همان workflow است؛ کد بازی/dependency/build دست نخورده می‌ماند تا علت واقعی ثبت شود. هیچ مرحلهٔ محصولی/گرافیکی آغاز نشده است.
