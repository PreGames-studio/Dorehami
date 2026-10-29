# HK-010 — «صف رویداد/انیمیشن و همگامی view» (2026-10-28)

> شاخه: `arena/dea50bc7-dorehami` · مبنای کد: `02b824b` (merge #37) · پیش‌نیاز: HK-009 (`DONE`).
> معیار نقشهٔ راه: «burst رویداد، skip animation و resync دور ناقص، کارت یا صدا را تکرار/حذف
> نکنند؛ presentation نتیجه را ننویسد.»

## ۱. مشکلات واقعی که پیدا شد

میز برای هر نوع انیمیشن **یک اسلات تک‌ظرفیتی** داشت: `pendingCollect`، `pendingHandBanner`،
`pendingDeal` و یک `pendingPauseMs`. سه نقص ملموس داشت:

| نقص | رفتار قبلی | پیامد |
|---|---|---|
| burst رویداد | `pendingCollect = winner` و `pendingHandBanner = banner` هر بار **بازنویسی** می‌شدند | در پخش سریع (ربات‌های پشت‌سرهم یا میزبان آنلاین که دو دور را زودتر تمام می‌کند) جمع‌کردن یک دور یا بنر «دست تمام شد» گم می‌شد |
| رویداد تکراری | `tableCards + Play(seat, card)` بی‌قید اضافه می‌کرد؛ `handHistory + summary` هم همین‌طور | تحویل دوبارهٔ یک رویداد ⇒ یک کارت دوبار روی میز یا یک دست دوبار در برگهٔ امتیاز |
| انیمیشن دست کهنه | هیچ چیزی انیمیشن را به «دستِ» خودش وصل نمی‌کرد | پس از resync، می‌شد بنر/جمع‌کردن دست قبلی را روی میزِ دست جدید دید |
| دوبار ارسال حرکت | `playCard`/`chooseHokm` بی‌گارد `sendMine` می‌زدند | دو لمس سریع = دو حرکت؛ دومی با پیام خطای «نوبت تو نیست» به کاربر برمی‌گشت |
| پاک‌شدن گارد ارسال | — | اگر گارد را به «هر snapshot» وصل می‌کردیم، همان نمای تکراری (تغییر deadline) آن را زود پاک می‌کرد |

## ۲. پیاده‌سازی

**`TableAnimationQueue.kt` (خالص، بدون Compose، قابل تست JVM):**

- `TableStep` = `CollectTrick` / `ShowBanner` / `Deal`؛ هر گام `handNumber`، `trickNumber`، `revision`
  و یک `key` یکتا دارد.
- `TableAnimQueue`: FIFO **محدود** (`DEFAULT_ANIM_QUEUE_CAPACITY = 8`) که
  - گام تکراری (`key` یکسان) را **نمی‌پذیرد**؛
  - در سرریز، **قدیمی‌ترین** گام را می‌اندازد (کهنه‌ترین چیز روی صفحه) و شمارش `droppedCount` دارد؛
  - `dropBeforeHand(handNumber)`: گام‌های دست‌های قدیمی‌تر را کنار می‌گذارد ⇒ «snapshot معتبر
    انیمیشن‌های عقب‌مانده را کنار می‌گذارد، نه اینکه چند دست هم‌زمان نشان داده شود».
- `TableEvents` (توابع خالص رویداد): `appendCard` (ضدتکرار)، `turnAfter` (نوبت پس از کارت واقعی؛
  برای تکراری تغییر نمی‌کند و دورِ بسته `-1` می‌شود)، `handBanner`/`handSound` (تنها جایی که متن و
  صدای پایان دست ساخته می‌شود)، و `sendingAfterSnapshot` (گارد ارسال فقط با **revision جدیدتر** پاک می‌شود).

**`HokmTable`:**

- اسلات‌های تک‌ظرفیتی حذف و با صف جایگزین شدند؛ `loop()` هر بار **یک** گام را با `runStep()` اجرا
  می‌کند (`CollectTrick` = نمایش ۱۱۵۰ms → جمع‌کردن ۵۲۰ms؛ `ShowBanner` = نگه‌داشتن تا پایان؛
  `Deal` = ۱۴۰۰ms) و سپس `refresh()`.
- `card_played` از `TableEvents.appendCard` می‌گذرد؛ تکراری هیچ کارتی اضافه نمی‌کند و «بریدن با حکم»
  و افکت فقط برای کارت واقعی رخ می‌دهد.
- `hand_finished` بنر/صدای خود را از `TableEvents` می‌گیرد و در `handHistory` فقط اگر همان
  `handNumber` قبلاً ثبت نشده باشد اضافه می‌شود.
- `refresh()` پس از اعمال snapshot، `animQueue.dropBeforeHand(snapshot.handNumber)` را صدا می‌زند.
- `send()`: پیش از `sendMine` مقدار `isSendingMove` را روشن می‌کند و اگر روشن باشد حرکت دوم را
  رد می‌کند؛ `isMyTurn`/`iChooseHokm`/`iDiscard`/`iDraw` هم آن را در گیت دارند. پاک‌سازی:
  نتیجهٔ پذیرفته‌شدهٔ محلی، خطا/رد، یا snapshot با **revision بالاتر**.
- `HokmSessionMatch.onState()` بر `hasQueuedAnimation` تکیه می‌کند (نه `pendingPauseMs`).

**presentation نتیجه را نمی‌نویسد:** مسیر رویداد فقط `Banner`، فهرست `Play`، `feed` و `handHistory`
تولید می‌کند؛ `scores`، `winnerTeam` و `finished` منحصراً از `TableSnapshotMapper` (HK-009) می‌آیند.

## ۳. تست‌ها — `TableAnimationQueueTest` (۱۷ تست JVM)

- burst سه رویداد به ترتیب پخش می‌شود (قبلاً فقط آخری می‌ماند)؛ ظرفیت محدود، قدیمی‌ترین می‌افتد.
- رویداد یکسان یک‌بار صف می‌شود؛ دو بنر با عنوان یکسان در یک دور = یک گام؛ عنوان متفاوت = گام تازه.
- `dropBeforeHand`: گام دست قدیمی می‌افتد، گام دست جاری می‌ماند، `hasStepsBeforeHand` درست پاسخ می‌دهد.
- کارت تکراری میز را تغییر نمی‌دهد؛ نوبت فقط با کارت واقعی جلو می‌رود و دورِ چهارکارتی بسته می‌شود.
- بنر کُت/حاکم‌کُت/بی‌نتیجه درست است و `void` هیچ امتیازی ادعا نمی‌کند؛ صدا با همان summary هم‌خوان است.
- گارد ارسال: لمس دوم در حالت ارسال رد می‌شود و فقط revision جدیدتر آن را پاک می‌کند.

## ۴. شواهد و محدودیت‌ها

- محیط محلی فاقد Java/Android SDK است؛ شاهد فقط CI.
- **run نهایی سبز:** [CI run `40795929565`](https://github.com/PreGames-studio/Dorehami/actions/runs/40795929565)
  روی `47d4e8c`: هر دو job (`checkAll`، `Build debug APK`) موفق،
  `checkAll: 239 tests, 0 failing, 13 module(s) with results` ⇒ دقیقاً **+۱۷ تست** نسبت به خط پایهٔ
  ۲۲۲ ([run `40736002665`](https://github.com/PreGames-studio/Dorehami/actions/runs/40736002665)).
- **دو run قرمز میانی (ثبت صادقانه):**
  1. [run `40795210538`](https://github.com/PreGames-studio/Dorehami/actions/runs/40795210538) روی `0cf8c30`:
     `Unresolved reference 'DEFAULT_CAPACITY'` — مقدار پیش‌فرض سازنده به نام ثابتی اشاره می‌کرد که
     وجود نداشت (`DEFAULT_ANIM_QUEUE_CAPACITY`). در `226ca58` رفع شد.
  2. [run `40795503257`](https://github.com/PreGames-studio/Dorehami/actions/runs/40795503257) روی `226ca58`:
     دو تست خودِ HK-010 با انتظار غلط شکستند — `dropBeforeHand` درست **۲** گام دست قبلی را حذف
     می‌کرد (تست ۱ انتظار داشت) و برچسب تیم در زیرنویس همان چیزی است که فراخوان می‌دهد
     («تیم حریف»). در `47d4e8c` انتظارها اصلاح شدند؛ کد محصول تغییر نکرد.
- آزمون‌نشده روی دستگاه: حس واقعی زمان‌بندی انیمیشن‌ها، پخش صدا، burst واقعی در اتاق بلوتوث/آنلاین،
  TalkBack و فریم‌ریت. تست‌ها منطق را اثبات می‌کنند نه تجربهٔ روی گوشی.
- عمداً تغییر نکرد: سیاست مهلت نوبت (HK-036/038)، همگام‌سازی نقشهٔ صندلی‌ها در reconnect (HK-033)،
  عمر state در چرخش صفحه (HK-011)، و گیت ناپایدار سرور (IMP-009).

**وضعیت:** `HK-010` → پس از ثبت شاهد CI، `DONE`. مرحلهٔ بعدی پیشنهادی بدون شروع خودکار: `HK-011`.
