# حکم — `games/hokm`

> **خلاصه:** قواعد کامل حکم چهارنفره (دو تیم)، ربات قطعی، چهار تست قرارداد، و میز بازی Compose با
> گرافیک برداری و افکت صوتی. `core` هیچ وابستگی‌ای جز `core/gamesdk` ندارد؛ `ui` فقط `core` همین
> بازی و `core/uikit` را می‌شناسد.

## قواعد پیاده‌شده (`HokmSession`)

| قاعده | کجا |
|---|---|
| تعیین حاکم اول: کارت‌ها یکی‌یکی از صندلی ۰ رو می‌شوند تا اولین آس | `drawFirstHakem()` — در view عمومی با کلید `hakemDraw` |
| حاکم ۵ کارت می‌گیرد و خال حکم را اعلام می‌کند؛ بعد بقیه تا ۱۳ برگ پخش می‌شود | `startHand()` / `chooseHokm()` / `dealRest()` |
| حاکم دست اول را شروع می‌کند؛ برندهٔ هر دست، دست بعد را | `playCard()` |
| پیروی از خال اجباری است؛ در نبودِ خال هر کارتی (از جمله حکم) | `HokmRules.legalCards` — رد با `game.illegal_action` |
| بالاترین حکم، وگرنه بالاترین کارتِ خالِ زمین | `HokmRules.trickWinner` |
| ۷ دست = برد آن دست: ۱ امتیاز؛ کُت (۷–۰) = ۲؛ حاکم‌کُت = ۳ | `HokmRules.handResult` (قابل تنظیم با گزینه‌ها) |
| حاکم تا وقتی تیمش می‌برد ثابت است؛ وگرنه نفر بعدی | `HokmRules.nextHakem` |
| اولین تیم به امتیاز هدف (پیش‌فرض ۷؛ ۱/۳/۵/۷ مجاز) برنده است | `result()` |

## قرارداد

* **حرکت‌ها:** `choose_hokm` با `{"suit":"spades|hearts|diamonds|clubs"}` و `play_card` با `{"card":"AS"}`
  (کد رتبه + کد خال؛ `T` برای ۱۰).
* **view عمومی:** `phase, handPhase, handNumber, hakem, hokm, turn, trickNumber, leadSuit, currentTrick,
  tricksWon, scores, targetPoints, handCounts, players, hakemDraw, lastTrick, lastHandResult` — **هیچ‌وقت** کلید
  `hand`/`hands` ندارد (تست `HokmPrivacyTest`).
* **view خصوصی:** عمومی + `seat`, `hand`, `legalCards`.
* **انتشارها:** `card_played`, `trick_won`, `hokm_chosen`, `hand_finished`, `hand_started`, `game_finished`
  + فید و نام صداها (`card_played`, `trick_won`, `hokm_chosen`, `hand_won`, `kot`, `hakem_kot`, `game_won`, `shuffle`).
* **ربات (`HokmBot`):** فقط از `BotView` (دست خودش + میز) تصمیم می‌گیرد؛ بدون RNG. انتخاب حکم = بلندترین خال؛
  بازی = نقد کردن برنده‌های قطعی، بردن ارزان، حکم نزدن روی یارِ برنده، دور ریختن کم‌ارزش‌ترین کارت.

## تست

```bash
./gradlew :games:hokm:core:test
```

چهار تست قرارداد: `HokmRulesTest`، `HokmPrivacyTest`، `HokmDeterminismTest` (بازپخش ⇒ همان digest + رد رکورد
دست‌کاری‌شده با `MatchVerifier`)، `HokmScoringTest` (۲۰ بازی کامل ربات، هر دست با قواعد چک می‌شود).

## میز بازی (`games/hokm/ui`)

* `HokmEntry` ← صفحهٔ تنظیم (اسم، امتیاز هدف، صدا، قوانین) ← `HokmScreen`.
* `HokmLocalMatch` تنها جایی است که `GameEngine.submit` را صدا می‌زند (حالت تک‌نفره با ۳ ربات). صفحه فقط
  `HokmUiState` می‌خواند که از `privateView()` بازیکن ساخته می‌شود. با آمدن `core/session` همین صفحه بدون
  تغییر به اتاق بلوتوثی/آنلاین وصل می‌شود.
* کارت‌ها کاملاً برداری‌اند (`PlayingCardView`): نقش‌های واقعی ۲ تا ۱۰، کارت‌های شاه/بی‌بی/سرباز قاب‌دار دو‌سر،
  پشت کارت با گره‌چینی ایرانی. هیچ فونت یا تصویر خارجی لازم نیست (♥ هرگز ایموجی نمی‌شود).
* صداها در `res/raw` به‌صورت رویه‌ای تولید شده‌اند (بدون مسئلهٔ مجوز) و از `core/uikit/UiSoundPlayer` پخش می‌شوند.
