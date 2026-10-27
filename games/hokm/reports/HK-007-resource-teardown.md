# HK-007 — «بستن قطعی منابع اتاق» (2026-10-27)

> شاخه: `arena/a2beae36-dorehami` · مبنای کد: `f51ec3b` · کامیت کد: `2819b15`.
> معیار نقشهٔ راه: «خروج/خطا/cancel اتصال، coroutine، HTTP و صدا را رها نکند؛ آزمون تکرار ورود/خروج.»
> ریسک مرتبط: `R-05`.

## ۱. باگ واقعی (پیش از این مرحله)

`HokmRoom` در `HokmEntry.kt` هنگام dispose این کار را میکرد:

```kotlin
onDispose {
    flow.leave()     // scope.launch { client?.leave(); nearby?.close(); ws?.close(); http?.close() }
    scope.cancel()   // بلافاصله بعدش
}
```

`SessionClient.leave()`، `NearbyTransport.close()` و `WebSocketTransport.close()` همگی **suspend**اند؛
پس کوروتین پاکسازی در همان `scope`ای ساخته میشد که خط بعدی لغو میشد. نتیجه: پاکسازی در اولین
نقطهٔ تعلیق قطع میشد و اتصال/HTTP/client باز میماند. علاوه بر آن:

- در مسیرهای `Failed` (شکست `advertise`، `discover`، `connectTo`، تایمآوت `lobby.ack`، استثناهای
  `online`) هیچ پاکسازیای انجام نمیشد.
- در `connectBluetooth()`، job موقت «منتظر پذیرش» فقط در یکی از دو شاخهٔ شکست `cancel()` میشد؛
  در تایمآوت ۲۰ ثانیهای زنده میماند.
- `watcher` (منتظر شروع بازی) فقط در `leave()` لغو میشد و شکست اتصال آن را رها میکرد.

**صدا:** `rememberSoundPlayer` در `core/uikit` پخشکننده را با `DisposableEffect` مخصوص خودش
(`player.release()`) آزاد میکند؛ این افکت مستقل از `scope` اتاق است، پس `scope.cancel()` جلوی
آزادسازی `SoundPool` را نمیگیرد. این مورد با خواندن کد تأیید شد و نظمش در کامنت `onDispose`
مستند شد — بدون تغییر `core/uikit`.

## ۲. تصمیم و پیادهسازی

1. **`RoomResources`** (جدید، خالص JVM، بدون Android/Compose/شبکه): دفتر منابع یک اتاق. ثبت به
   ترتیب ساخت، آزادسازی **LIFO** (watcher → session → transport → HTTP). هر بستن با سقف ۲ ثانیه
   و `try/catch` جداگانه محافظت میشود؛ پس استثنا یا هنگکردن یک منبع، بقیه را متوقف نمیکند.
   `releaseAll()` کل دسته را بهصورت اتمیک برمیدارد و پاک میکند ⇒ فراخوانی دوباره هیچ منبعی را
   دوبار نمیبندد و دفتر برای چرخهٔ بعدی قابلاستفاده است (`generation`).
2. **`RoomFlow.teardown()`**: پاکسازی روی یک `Job` مستقل و کوتاهعمر اجرا میشود که پس از اتمام
   خودش را `complete()` میکند؛ بنابراین لغو `scope` رابط کاربری نمیتواند آن را نیمهکاره بگذارد و
   `Job` باقیمانده هم رها نمیشود. عمداً از `launch(NonCancellable)` استفاده نشد: KDoc رسمی
   [NonCancellable.kt](https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/NonCancellable.kt)
   صریحاً هشدار میدهد که این الگو رابطهٔ والد/فرزند را قطع میکند و برای `launch` طراحی نشده است.
3. **همهٔ مسیرها**: `leave()` و چهار تابع اتصال (`hostBluetooth`، `joinBluetooth`،
   `connectBluetooth`، `online`) با الگوی `var connected = false` + `try/finally` محافظت شدند؛
   هر `return` زودهنگام، استثنا یا cancel به `teardown()` میرسد. منابع هنگام ساخت در دفتر ثبت
   میشوند، job موقت پذیرش و `watcher` هم ثبت میشوند، و فیلدها بلافاصله `null` میشوند تا تلاش
   بعدی از یک client نیمهبسته استفاده نکند.
4. **بدون تغییر بیرونی**: پروتکل، سرور، قواعد، بازیهای دیگر، `core/uikit` و وابستگیها دستنخورده
   ماندند؛ تنها تغییر `HokmEntry.kt` یک کامنت دربارهٔ ترتیب dispose است.

## ۳. آزمونها

`RoomResourcesTest.kt` — **۸ تست JVM**:

| تست | پوشش |
|---|---|
| ترتیب معکوس | بستهشدن watcher→client→ws→http دقیقاً مثل یک اتاق واقعی |
| استثنا | یک `close()` پرتابکننده ثبت میشود و بقیه همچنان بسته میشوند |
| هنگکردن | سقف زمانی (۳۰ms در تست) جلوی قفلبقیه را میگیرد |
| ایدمپوتنسی | `leave()` دوبار ⇒ دقیقاً یک بار بستهشدن |
| تکرار ورود/خروج | ۳ چرخه، هرکدام ۳ منبع، هر نسل دقیقاً یکبار؛ `leave()` دوم بیکار |
| jobها | job ثبتشده هنگام teardown لغو میشود |
| **R-05** | وقتی scope رابط کاربری وسط پاکسازی `cancel()` میشود، بستن suspend باز هم کامل میشود و `Job` تکمیل میشود |
| دفتر خالی | `releaseAll()` روی دفتر خالی بیکار است |

`RoomFlow` به `Context`، Ktor و `Dispatchers.Main` وابسته است و در تست JVM نمونهسازی نمیشود؛
اتصالش به دفتر (ثبت هنگام ساخت، `finally` در چهار مسیر، `leave()`) با خواندن کد بررسی شد.

## ۴. شواهد

- محیط محلی فاقد Java/Android SDK است؛ `checkAll` و `:app:assembleDebug` محلی اجرا نشدند.
- [CI run `40438491546`](https://github.com/PreGames-studio/Dorehami/actions/runs/40438491546)
  روی `2819b15`: هر دو job سبز، `checkAll: 199 tests, 0 failing, 13 module(s) with results`،
  artifact APK بارگذاری شد.
- خط پایهٔ نوبت قبل ([run `40428298018`](https://github.com/PreGames-studio/Dorehami/actions/runs/40428298018)
  روی `f51ec3b`): ۱۹۱ تست / ۰ شکست / ۱۳ ماژول ⇒ دقیقاً **۸ تست تازه** اجرا و پاس شدند.
- لاگ کامل job بهعلت محدودیت `results-receiver.actions.githubusercontent.com` دانلود نشد؛ شاهد،
  annotation رسمی step «Report test results» و نتیجهٔ jobهاست.

## ۵. محدودیتها و کارهای باقیمانده

- **آزمون دستگاه انجام نشد**: ورود/خروج مکرر واقعی، لمس، Compose render، TalkBack، قطعی شبکه،
  بلوتوث و صدا روی گوشی. تستهای JVM فقط لایهٔ تصمیم پاکسازی را پوشش میدهند.
- اگر تلاش اتصال همزمان با `leave()` همچنان منبع تازهای بسازد، `finally` همان تلاش دوباره
  `teardown()` میکند؛ ولی «اتصال تازه بعد از leave روی همان نمونه» پشتیبانی نشده، چون صفحهٔ
  `Failed` فعلاً دکمهٔ تلاش مجدد ندارد (IMP-006).
- `start()` و `setReady()` استثناها را به `CoroutineExceptionHandler` سراسری میسپارند؛ منبع تازهای
  نمیسازند، ولی مدیریت خطایشان خارج از دامنهٔ این مرحله است و بهصورت IMP-008 ثبت شد.
- `UiSoundPlayer.release()` صریحاً ایدمپوتنت نیست و مجموعهٔ `loaded` از لیسنر `SoundPool` نوشته
  میشود؛ امروز فقط یک فراخوان وجود دارد، ولی محافظت و تست رشتهایاش به IMP-007 سپرده شد.

**وضعیت:** `HK-007` → **DONE** با شواهد JVM/CI بالا و ثبت صریح آزمونهای اجرانشدهٔ دستگاه.
مرحلهٔ بعدی پیشنهادی (بدون شروع خودکار): `HK-008` (قرارداد قواعد ۲/۳/۴ نفره)؛ IMP-005/006/007/008
در `HK-020` و کار جداگانهٔ `core/uikit`.
