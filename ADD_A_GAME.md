# افزودن یک بازی جدید — قدم‌به‌قدم

> **خلاصهٔ فارسی:** برای بازی جدید یک پوشه در `games/` می‌سازی، هفت سؤال قرارداد را جواب می‌دهی
> (`docs/GAME_CONTRACT.md`)، چهار تست می‌نویسی، و بازی را در دو جا ثبت می‌کنی. تمام. لابی، اتاق
> بلوتوثی، حریف تصادفی، تایمر و ذخیرهٔ نتیجه خودکار می‌آیند. این کار **یک نفر** است و به بقیهٔ تیم
> دست نمی‌زند.

---

## ۰. قبل از شروع

* `docs/GAME_CONTRACT.md` را بخوان (هفت سؤال + قواعد تغییرناپذیر).
* یک ایشو با قالب «بازی جدید» باز کن (نام، تعداد بازیکن، تیمی/انفرادی، اطلاعات پنهان چیست).
* مطمئن شو بازی در فهرست `docs/ROADMAP.md` یا حداقل با تیم هماهنگ است.

## ۱. ساخت پوشه‌ها

```
games/mygame/
  core/
    build.gradle.kts            // فقط :core:gamesdk (و :core:protocol اگر لازم شد)
    src/main/kotlin/ir/dorehami/games/mygame/
      MyGameModule.kt           // object MyGameModule : GameModule
      MyGameSession.kt          // قواعد
      MyGameBots.kt              // سیاست ربات (قطعی)
    src/test/kotlin/ir/dorehami/games/mygame/
      MyGameRulesTest.kt
      MyGamePrivacyTest.kt
      MyGameDeterminismTest.kt
      MyGameScoringTest.kt
  ui/
    build.gradle.kts            // :games:mygame:core + :core:uikit
    src/main/kotlin/ir/dorehami/games/mygame/ui/
      MyGameScreen.kt
```

`s settings.gradle.kts<` دو خط اضافه کن: `":games:mygame:core"` و `":games:mygame:ui"`.

## ۲. قواعد را بنویس

۱. `MyGameModule`: `meta` (سؤال ۱)، `validateOptions`، `create`، و اگر بازی رندوم می‌خواهد
   `botAction`.
۲. `MyGameSession`: `apply` (سؤال ۴)، `publicView`/`privateView` (سؤال ۳)، `legalActions`
   (سؤال ۲)، `result` (سؤال ۵)، `snapshot` (سؤال ۷)، و `botAction` (سؤال ۶).
۳. از `config.clock` و `config.rng` استفاده کن؛ **هرگز** ساعت/تصادف سیستم.
۴. حرکت نامعتبر ⇒ `ApplyResult.Rejected(ErrCodes.…, "پیام فارسی")`.

## ۳. چهار تست بخش ۵ قرارداد را بنویس

* **قواعد:** جریان بازی + ردهای درست.
* **نشت اطلاعات:** دادهٔ خصوصی بقیه در `publicView()` نیست (تست ساختاری، نه «خالی بودن»).
* **قطعیت:** یک بازی کامل با ربات‌ها ⇒ لاگ ⇒ بازپخش ⇒ همان `digest` و همان جدول.
* **امتیاز/پایان:** حالت‌های مرزی و شکل `result()`.

اجرای تست: `./gradlew :games:mygame:core:test`

## ۴. در دو جا ثبت کن (تنها تغییر بیرون از پوشهٔ بازی)

```kotlin
// app: ir.dorehami.app.di.GameCatalog   (یا هرجایی که GameRegistry ساخته می‌شود)
val games = GameRegistry(HokmModule, PantoModule, MyGameModule)

// server/src/main/kotlin/ir/dorehami/server/games/ServerGames.kt
val registry = GameRegistry(HokmModule, PantoModule, MyGameModule)
```

از این لحظه بازی در «فهرست بازی‌ها» (`GET /v1/games`)، صف حریف تصادفی، آمار و تورنمنت‌ها هست.

## ۵. صفحهٔ بازی (Compose)

* با اجزای `core/uikit` بساز (کارت بازیکن‌ها، تایمر نوبت، دست‌ها، دیالوگ پایان).
* صفحه فقط `SessionUiState` را می‌خواند و `controller.submit(...)` صدا می‌زند؛ هیچ‌وقت به موتور یا
  ترنسپورت مستقیم وصل نمی‌شود.
* رشته‌ها فارسی در `strings.xml`؛ اعداد با ارقام فارسی در رابط کاربری.
* تست دستی: یک اتاق بلوتوثی با دو گوشی + یک اتاق آنلاین با کد.

## ۶. چک‌لیست نهایی (همان که در PR می‌آید)

- [ ] هفت سؤال قرارداد در کد جواب داده شده.
- [ ] چهار تست سبز و `./gradlew checkAll` سبز.
- [ ] هیچ import بیرونی (سرور/ترنسپورت/بازی دیگر) در `core` بازی نیست.
- [ ] سرصفحهٔ `session.private` هیچ‌وقت اطلاعات بقیه را نمی‌فرستد (تست دارد).
- [ ] بازی در `GameRegistry` و `ServerGames` ثبت شده.
- [ ] `docs/ROADMAP.md` تیک خورده و در `README` (اگر لازم بود) ذکر شده.
- [ ] یک بار روی گوشی واقعی تست شده: اتاق آفلاین + آنلاین + بازی با ربات.
