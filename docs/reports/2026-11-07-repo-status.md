# گزارش وضعیت مخزن — ۲۰۲۶-۱۱-۰۷

شاخهٔ کار: `arena/9923ccae-dorehami` · مبنا: `main` در `4208aed` (PR #47) · تاریخ بررسی: ۷ نوامبر ۲۰۲۶

این سند «عکس لحظه‌ای» است: چه ساخته شده، چه سالم است، چه شکسته است، و قدم بعدی چیست.
هیچ کدی در این نوبت تغییر نکرد؛ فقط سنجش و ثبت شاهد.

---

## ۱. خلاصهٔ اجرایی (سه خط)

1. **معماری سالم و منسجم است**؛ ۲۰ ماژول Gradle، ~۳۸ هزار خط Kotlin، ۴ بازی کامل + یک بازی Godot، و
   تفکیک «قواعد/چرخهٔ اتاق/ترنسپورت/رابط» در عمل رعایت شده است.
2. **`main` از ۲۰۲۶-۱۱-۰۴ قرمز است** — نه به‌خاطر منطق بازی، بلکه یک **غلط تایپی در import** در
   `HokmLocalMatchTest.kt` که کامپایل تست‌های `:games:hokm:ui` را می‌شکند. ۲۴۹ تست دیگر همه سبزند.
3. **برنچ `arena/17c934e4-dorehami` فقط مستندات است** (+۱ کامیت، ADR-0007) و از `main` عقب‌تر است؛
   محتوایش هم چند مشکل کیفیتی دارد که پیش از ادغام باید اصلاح شود.

---

## ۲. سنجش محیط (چه چیزی در این سندباکس ممکن است)

| ابزار | وضعیت | نتیجه |
|---|---|---|
| JDK / Android SDK / Gradle | ❌ نصب نیست | `./gradlew checkAll` و `assembleDebug` محلی ممکن نیست |
| `jdk4py` (pypi) + `kotlin-compiler 2.4.21` (npm) | ✅ راه‌اندازی شد | `tools/jvm-check/run.sh` در ۷۸ ثانیه اجرا شد: **۶۳ تست JVM + ۱۴ تست UI خالص، ۰ شکست** |
| `gh` + دسترسی Actions | ✅ | ساخت APK و اجرای CI از طریق ورک‌فلوها ممکن است |
| واکشی تاریخ کامل | ✅ (`git fetch --unshallow`) | ۴۴۸ کامیت؛ مقایسهٔ برنچ‌ها اکنون دقیق است |

**پیامد عملی:** در این محیط «تست سبز» فقط از دو راه به‌دست می‌آید: `tools/jvm-check/run.sh` برای
لایه‌های JVM، و CI گیت‌هاب برای Compose/Android. مستندات مخزن این را از قبل پذیرفته‌اند، ولی یک
شکاف واقعی وجود دارد: `jvm-check` تست‌های ماژول‌های Android (همان‌جایی که باگ فعلی است) را کامپایل
نمی‌کند — به همین دلیل این قرمزی فقط در CI دیده شد.

---

## ۳. معماری — آنچه واقعاً هست

```
core/protocol    ErrCodes + Ids                                          (۶۰ خط)
core/gamesdk     Contract/Engine/Determinism/Registry                     (۵۰۶ خط)
core/session     HostController + GuestController + SessionClient + Envelope/PROTO_VERSION (۱٬۲۶۲)
core/testkit     FakeHub                                                  (۵۰)
core/uikit       Theme/DorehamiPageFrame/ FaDigits/SoundPlayer            (۱۳۶)
transport/api    GameTransport + Frames (تکه‌بندی بلوتوث)                 (۱۹۲)
transport/nearby NearbyTransport + Permissions                            (۲۶۵)
transport/hotspot HotspotTransport (TCP داخل هات‌اسپات)                   (۳۱۲)
transport/online OnlineApi + WebSocketTransport                           (۲۶۶)
server           Application + Tokens + rooms/{RoomHub,Matchmaker,RelayTransport,RecordJson} (۸۲۴)
app              MainActivity + Home/Games/Settings/Store/Theme           (۱٬۲۶۹)
```

**قانون طلایی رعایت شده:** هیچ‌جا `core/*` به بازی یا سرور وابسته نیست؛ هیچ بازی‌ای به بازی دیگر
import ندارد. سرور همان `HostController` (بدون صندلی) را اجرا می‌کند و `RoomHubTest` این را روی
هاب واقعی می‌سنجد (دو مهمان + دو ربات تا پایان بازی).

### انحراف مستند از کد (باید ثبت/اصلاح شود)

| ادعای مستند | واقعیت کد |
|---|---|
| `docs/ARCHITECTURE.md` و `ROADMAP` می‌گویند Envelope/ProtocolCodec/Frames در `core/protocol` است | `core/protocol` فقط `ErrCodes.kt` و `Ids.kt` دارد؛ `Envelope`+`PROTO_VERSION` در `core/session/Protocol.kt` و `Frames` در `transport/api` است |
| `README.md`: «دژبان هنوز جای‌نگهدار است» | دژبان یک بازی کامل Godot است، `implemented = true` و از `MainActivity` اجرا می‌شود |
| `docs/ROADMAP.md` §M5: بلوتوث «آزمایش‌نشده روی گوشی» | درست است؛ هنوز شاهد دستگاه ندارد |

---

## ۴. وضعیت بازی‌ها و ماژول‌ها

| ماژول | main / test (فایل) | خط | وضعیت واقعی |
|---|---|---|---|
| `games/hokm/core` | ۵ / ۱۰ | ۲٬۱۱۸ | ✅ قواعد ۲/۳/۴ نفره، ربات، قطعیت، حریم، RULES.md v1.0 |
| `games/hokm/ui` | ۲۶ / ۱۵ | **۱۰٬۷۱۱** | 🟡 بزرگ‌ترین و پرریسک‌ترین ماژول؛ اتاق (Nearby/Hotspot/آنلاین)، میز ۲٫۵D، ذخیرهٔ بازی؛ **تست‌هایش الان کامپایل نمی‌شوند** |
| `games/chess/core+ui` | ۴+۲۱ / ۶+۴ | ۸٬۶۴۶ | ✅ میز سه‌بعدی OpenGL ES، ربات چهارسطحی، بلوتوث/هات‌اسپات؛ آنلاین نمایشی |
| `games/backgammon/core+ui` | ۵+۱۲ / ۶+۵ | ۵٬۶۰۲ | ✅ قواعد کامل + ربات قطعی؛ بلوتوث واقعی (تست هات‌اسپات/دو گوشی)؛ آنلاین نمایشی |
| `games/pantohub/core+ui` | ۶+۱۰ / ۹+۲ | ۵٬۲۸۷ | 🟡 بازی محلی/آفلاین؛ بانک **۲٬۵۰۰** رکورد یکتا (اسکریپت اعتبارسنجی تأیید کرد)، هدف ۲۰٬۰۰۰؛ به قرارداد `GameModule`/`SessionController` وصل نشده |
| `games/dejban` | core خالی / ۲ + Godot | ۲۰۴ + پک Godot | 🟡 بازی Godot جاسازی‌شده؛ APK ~۱۱۹ مگابایت؛ برخی تست‌های نصب/اجرا روی امولاتور قرمزند |
| `transport/*` | ۵+ | ۱٬۰۳۵ | 🟡 کد نوشته شده، شاهد دستگاه واقعی ندارد |
| `server` | ۶ / ۲ | ۸۲۴ | 🟡 درون‌حافظه (Postgres نه)؛ تست قرارداد و اتاق سبز |
| `app` | ۱۰ / ۱ | ۱٬۲۶۹ | 🟡 ناوبری دستی (`when (screen)`)؛ «منچ/مونوپولی/مافیا» فقط کارت به‌زودی |

**جایگاه در نقشهٔ راه کلان:** M0–M4 ✅، M4.5 ✅ (آفلاین)، M5/M6 🟡 (کد نوشته، شاهد دستگاه/استقرار
ندارد)، M7 🟡، M8 🟡 (پانتومیم محلی)، M9 ✅، M10 ❌ (تورنمنت/تماشاچی/انتشار).

---

## ۵. وضعیت سلامت: CI قرمز است

| شاهد | نتیجه |
|---|---|
| [run `42164089061`](https://github.com/PreGames-studio/Dorehami/actions/runs/42164089061) روی `main`/`4208aed` | `checkAll` **failure** · `Build debug APK` success |
| [run `41933244078`](https://github.com/PreGames-studio/Dorehami/actions/runs/41933244078) (۲۰۲۶-۱۱-۰۴) | همان خطا — یعنی قرمزی از ۳ روز پیش تکرار می‌شود |
| خلاصهٔ گیت | `checkAll: 249 tests, 0 failing, 15 module(s) with results; missing expected results: games/hokm/ui` |

**ریشهٔ خطا (قطعی):** `games/hokm/ui/src/test/kotlin/.../HokmLocalMatchTest.kt` خط‌های ۱۱ و ۱۲:

```kotlin
import kotlin.serialization.json.JsonArray     // ✗
import kotlin.serialization.json.JsonObject    // ✗
```

باید `kotlinx.serialization.json.*` باشد. خطا: `Unresolved reference 'serialization'` در تسک
`:games:hokm:ui:compileDebugUnitTestKotlin`. منبع: کامیت `5f163b2` (PR #46 / HK-011) که با CI قرمز
merge شد. گیت CI درست رفتار کرده و PR را «سبز» حساب نکرده — مشکل این است که ادغام انجام شد.

**نکتهٔ مثبت:** تنها همین دو خط مانع سبزی است؛ ۲۴۹ تست باقی (protocol/gamesdk/session/transport/
server/hokm-core/chess/backgammon) همه پاس می‌شوند و جاب APK هم سالم ساخته می‌شود.

**سیگنال دوم (جدا از CI اصلی):** در ورک‌فلوی «۲۲ KB/۱۶ KB Dejban» چند بررسی نصب/اجرای امولاتور
از ۱۱-۰۴ قرمزند (`SIGILL` هنگام اجرای بازی Godot روی arm64/x86_64 ترجمه‌شده). این به هستهٔ
Dorehami ربطی ندارد ولی یعنی «APK دژبان روی همهٔ امولاتورها اجرا نمی‌شود» هنوز باز است.

**وزن مخزن:** ۴۳۵ مگابایت working tree؛ `tower_defense_ui_assets.zip` (~۳۵MB) و پنج تصویر
چندمگابایتی در ریشهٔ مخزن track شده‌اند. برای تیم کوچک و CI قابل تحمل است، ولی کلون/بکاپ را
سنگین می‌کند و بهتر است به Release/LFS/پوشهٔ assets منتقل شود.

---

## ۶. برنچ مرج‌نشدهٔ `arena/17c934e4-dorehami`

### تفاوت با `main`

| سنجه | مقدار |
|---|---|
| نوک برنچ | `7dd57a9` — «hokm: HK-012 — ADR نسخه‌بندی قوانین، RNG، رکورد و پروتکل» (۲۰۲۶-۱۱-۰۶) |
| جد مشترک | `9216f11` (merge PR #46) |
| جلوتر از جد | **۱ کامیت** |
| عقب‌تر از `main` | **۴ کامیت** — همه فقط `.github/workflows/` (PR #47، تشخیص نصب APK) |
| تغییرات | `docs/adr/ADR-0007-hokm-versioning.md` (جدید، ۳۶ خط) + ۲ هانک در `games/hokm/ROADMAP.md` |
| کد/قواعد/سرور | **هیچ تغییری ندارد** |
| PR | باز نشده (فهرست PRها فقط تا #47 و همه merged) |
| CI | هیچ اجرای تستی ندارد؛ به‌خاطر `paths-ignore: **.md` اجرا نشد |
| تعارض ادغام | ندارد — `git merge-tree origin/main origin/arena/17c934e4-dorehami` تمیز بود |

### محتوا

ADR-0007 سه لایهٔ نسخه‌بندی را تعریف می‌کند: `MatchRecord.schema` (=۱)، `rulesVersion`/`rngVersion`
در `GameConfig.options`، و `PROTO_VERSION`/`Envelope.v` (=۱)؛ قانون سازگاری، سیاست رد امن رکورد
قدیمی، و موکول‌کردن RNG رمزنگارانه به HK-034. هم‌چنین HK-011/HK-012 را `DONE` ثبت می‌کند.

### ایرادهایی که پیش از ادغام باید رفع شود

1. **متن ADR آلوده است:** واژه‌های ناخواستهٔ چندزبانه داخل جمله‌های فارسی آمده — «политика»،
   «ب희»، «salva-state»، «موقوف شده است»، «به DONE مع 무기»، «قوالی»، «رفض»، «بازبیری»،
   «şaHK-034»، «consecuencias»، «Alternately-record». برای سندی که مرجع تصمیم است پذیرفتنی نیست.
2. **ادعای نادرست دربارهٔ کد:** می‌گوید `rulesVersion` و `rngVersion` «فعلاً هر دو `"۱"`» در
   `GameConfig.options` هستند؛ در کد چنین کلیدهایی **وجود ندارند** (فقط `MatchRecord.schema = 1`،
   `PROTO_VERSION = 1` و `SOLO_MATCH_SCHEMA` هست). ADR باید یا این‌ها را «تصمیم برای آینده» بنامد
   یا کد پیاده شود.
3. **ترتیب مراحل:** `main` هنوز HK-011 را `ACTIVE` می‌داند؛ برنچ آن را `DONE` می‌کند. ثبت DONE
   باید با شاهد CI سبز همراه باشد و اکنون CI قرمز است (به دلیل بند ۵). یعنی «شاهد» ناقص است.
4. نام سیاست در بعضی جمله‌ها به‌جای فارسی تقریباً انگلیسی‌نویسی شده؛ زبان سند باید یکدست باشد.

### توصیه

برنچ را **حفظ و اصلاح** کنیم نه حذف: محتوایش از نظر تصمیم درست و لازم است (HK-012 واقعاً یک
گام نقشهٔ راه است) و ادغامش بدون تعارض است. ترتیب پیشنهادی: (۱) رفع تایپوی main، (۲) سبز شدن CI،
(۳) بازنویسی تمیز ADR-0007 + هم‌راستاکردن ادعاها با کد، (۴) باز کردن PR و ادغام.

---

## ۷. آماده‌سازی توسعه (چه چیزی الان آماده است)

* شاخهٔ کار `arena/9923ccae-dorehami` تمیز است و روی `main` تازه rebase شده (بدون تغییر).
* گیت محلی JVM کار می‌کند: `KT=/tmp/kt/package JAVA_HOME=<jdk4py> tools/jvm-check/run.sh` ⇒ ۷۷ تست سبز.
* برای شاهد Compose/APK: `gh workflow run ci.yml --ref arena/9923ccae-dorehami` یا
  `gh workflow run debug-apk.yml --ref …` ⇒ artifact `Dorehami-debug.apk`.
* نقشهٔ راه فعال برای کار روزانه: `games/hokm/ROADMAP.md` (قاعده: هر جلسه یک مرحله، با تأیید صریح).
  مرحلهٔ بعدی طبق همان سند: **HK-013 — خط پایهٔ تصویری/عملکرد/دسترس‌پذیری** (HK-011 و HK-012 در
  صف بستن قرار دارند).

## ۸. قدم‌های بعدی (به ترتیب اولویت)

| # | کار | اندازه | چرا اول |
|---|---|---|---|
| ۱ | رفع دو import در `HokmLocalMatchTest.kt` + اجرای CI تا سبزی | ۲ خط | `main` قرمز است؛ هر PR بعدی با همین برچسب شکست می‌خورد |
| ۲ | ثبت HK-011 `DONE` با شاهد CI سبز (پس از ۱) | سند | ادعای برنچ مرج‌نشده بی‌شاهد است |
| ۳ | بازنویسی تمیز `ADR-0007` + هم‌راستاسازی با کد، سپس PR | سند | جلوی تصمیم مبهم دربارهٔ Migration/RNG را می‌گیرد |
| ۴ | HK-013: خط پایهٔ فریم/حافظه/اسکرین‌شات | متوسط | گیت کیفیت برای هر تغییر UI بعدی |
| ۵ | اتصال آنلاین واقعی (endpoint در اپ تنظیم نشده) و Postgres | بزرگ | تنها مسیر نیمه‌کارهٔ معماری |
| ۶ | جابه‌جایی فایل‌های حجیم از Git به Release/LFS | کوچک | کلون و CI سبک‌تر |

> شاهد خطای CI و متن دقیق ADR و همهٔ اعداد بالا از خود مخزن و API گیت‌هاب گرفته شده‌اند؛
> هیچ‌کدام حدس نیستند.
