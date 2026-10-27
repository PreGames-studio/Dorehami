# HK-006 — «استفادهٔ واقعی از آدرس سرور تازه» (بستن مرحله، 2026-10-27)

> شاخه: `arena/a2beae36-dorehami` · مبنای کد: `402b22a` · کامیت کد: `e4c0d28`.
> معیار نقشهٔ راه: «تغییر آدرس قبل از اتصال در همان صفحه به endpoint جدید برسد؛ ورودی بد خطای روشن بدهد.»

## ۱. شکافی که بسته شد

پیادهسازی اولیه HK-006 از قبل روی `main` وجود داشت: `OnlineServerAddress.update()`،
`RoomFlow.updateServerUrl()`، پیام فارسی `INVALID_ADDRESS_MESSAGE_FA`، `isError` و نمایش خطا در
`OnlineChoose`، بهعلاوهٔ دو تست `OnlineServerAddressTest`. مشکل، **شکاف سند و کد** بود: داشبور
مخزن HK-006 را همچنان `ACTIVE` نگه داشته بود و تست موجود فقط مقدار `validatedBaseUrl()` را چک
میکرد — یعنی ثابت نمیکرد که آدرس تازه واقعاً به مسیر اتصال (HTTP و سوکت) برسد. ریسک قدیمی
`R-04` دقیقاً همین «آدرس بهجامانده از `remember`» را نشان کرده بود.

## ۲. چه تغییری کرد

1. **یک تابع خالص مشترک** `socketUrlFor(baseUrl, code, token)` در `transport/online/OnlineApi.kt`.
   `OnlineApi.roomSocketUrl()` حالا به همان تابع واگذار میکند؛ فرمول `http→ws` / `https→wss` و
   مسیر `/v1/ws/room/{code}?ticket=…` (بخش ۷ `PROTOCOL.md`) بدون تغییر ماند.
   تغییر مشترک فقط یک تابع خالص + تست سازگاری است، بدون عوضکردن پروتکل، API موجود، سرور یا وابستگی.
2. **یک نقطهٔ واحد تصمیمگیری** `OnlineConnectionPlan` در `games/hokm/ui/.../room/`:
   `update()` از همان `OnlineServerAddress` اعتبارسنجی میگیرد، ولی `resolve()` یک `Endpoint`
   میدهد که `httpBase` و `socketUrl()` اش از **همان** رشته ساخته میشوند.
3. **مصرف واقعی در `RoomFlow.online()`**: پیش از ساختن `HttpClient`، `connectionPlan.resolve()`
   یکبار خوانده میشود؛ همان `Endpoint` هم به `OnlineApi(http, endpoint.httpBase)` و هم به
   `WebSocketTransport(http, endpoint.socketUrl(code, token), …)` داده میشود. بنابراین HTTP و WS
   نمیتوانند به دو میزبان متفاوت بروند. اگر `resolve()` تهی باشد، پیام فارسی روشن تنظیم و تابع
   **قبل از ایجاد هر منبع شبکهای** برمیگردد.
4. رابط کاربری لابی دستنخورده ماند: همان فیلد قابلویرایش `OutlinedTextField` در همان صفحه، همان
   `isError` و همان متن خطا.

## ۳. تستها (همان لایهای که رفتار در آن زندگی میکند)

| فایل | تعداد | پوشش |
|---|---|---|
| `games/hokm/ui/.../room/OnlineConnectionPlanTest.kt` (جدید) | ۷ | آخرین آدرس تایپشده برنده است؛ old→new→new2؛ `http→ws`/`https→wss` همراه پورت/مسیر/کد/توکن؛ ورودیهای بد (۸ مورد) بدون endpoint و با پیام فارسی؛ آدرس بد قبلی را نگه نمیدارد و آدرس خوب بعدی بازمیگردد؛ فرمول سوکت دقیقاً همان `socketUrlFor` مشترک؛ قطعیت `resolve()` |
| `transport/online/src/test/.../OnlineSocketUrlTest.kt` (جدید) | ۲ | نگاشت خالص URL سوکت + اینکه `OnlineApi.roomSocketUrl()` همچنان به همان تابع خالص واگذار میکند (سازگاری تغییر مشترک) |
| `OnlineServerAddressTest.kt` (موجود) | ۲ | بدون تغییر، نگهبان اعتبارسنجی |

چون `RoomFlow` به `Context`، Ktor و coroutine وابسته است، تست JVM روی **لبهٔ تصمیمگیری خالص**
(همان شیئای که `RoomFlow` مصرف میکند) نوشته شد؛ خود مسیر `RoomFlow.online()` با خواندن کد
بررسی شد. آزمون دستگاه/امولاتور و Compose UI انجام نشد.

## ۴. شواهد

- محیط محلی فاقد Java/Android SDK است؛ `./gradlew checkAll` و `:app:assembleDebug` محلی اجرا نشدند.
- [CI run `40427520378`](https://github.com/PreGames-studio/Dorehami/actions/runs/40427520378)
  روی `e4c0d28`: هر دو job سبز؛ `checkAll: 191 tests, 0 failing, 13 module(s) with results`
  و artifact `dorehami-debug-apk` بارگذاری شد.
- خط پایهٔ نوبت قبل ([run `40388067654`](https://github.com/PreGames-studio/Dorehami/actions/runs/40388067654)
  روی `402b22a`): ۱۸۲ تست / ۰ شکست / ۱۲ ماژول. تفاضل دقیقاً **۹ تست تازه** و یک ماژول بیشتر
  (اولین تست تاریخ `transport/online`) — یعنی تستهای جدید اجرا و پاس شدند و تست قبلی حذف نشد.
- لاگ کامل job بهعلت خطای `EOF` از `results-receiver.actions.githubusercontent.com` دانلود نشد؛
  شاهد، annotation رسمی step «Report test results» و نتیجهٔ jobهاست.

## ۵. وضعیت و موارد بیرون از دامنه

`HK-006` → **DONE** با شواهد بالا. مشابه HK-002/004/005، آزمون انسانی روی گوشی بهعنوان
**کار باقیماندهٔ صریح** ثابت ماند؛ این مرحله مدعی تست دستگاه نیست.

حین بررسی دو گپ بیرون از دامنهٔ تأییدشده پیدا شد و **پنهانی رفع نشد** (به صندوق پیشنهاد رفت):

- `IMP-005`: پیام خطای آدرس سرور برای TalkBack اعلام نمیشود (فقط `isError` بصری و یک `Text`
  معمولی؛ `semantics { error(...) }` / `liveRegion` ندارد).
- `IMP-006`: پس از شکست اتصال، صفحهٔ `Failed` فقط «بازگشت به منو» دارد؛ کاربر نمیتواند بدون
  خروج از اتاق، آدرس سرور را اصلاح و دوباره تلاش کند.

مرحلهٔ بعد پیشنهادی (بدون شروع خودکار): `HK-007` — بستن قطعی منابع اتاق، سپس `HK-008`؛ و در
کنار آنها رسیدگی به IMP-005/006 در HK-020 (منو و خطاهای قابل اقدام).
