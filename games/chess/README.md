# شطرنج — `games/chess`

> **خلاصه:** قواعد کامل شطرنج (حرکت‌ها، کیش، مات، پات، همهٔ نوع تساوی) + ربات قطعی چهارسطحی + میز
> سه‌بعدی OpenGL ES با افکت صوتی. `core` هیچ وابستگی‌ای جز `core/gamesdk` ندارد؛ `ui` فقط `core` همین
> بازی و `core/uikit` را می‌شناسد. هیچ کتابخانهٔ سه‌بعدی خارجی اضافه نشده (GLES 2.0 بخشی از پلتفرم است).

## قواعد پیاده‌شده (`games/chess/core`)

| قاعده | کجا |
|---|---|
| حرکت همهٔ مهره‌ها، پین و سپر (تولید حرکت با تست پادشاهی) | `Board.generatePseudo` + `Board.legalMoves` |
| قلعهٔ کوتاه/بلند با همهٔ شرط‌ها (حق قلعه، خالی‌بودن مسیر، نه کیش، نه عبور از خانهٔ کیش) | `genCastles` |
| گرفتن در عبور (en passant) و ارتقا با انتخاب مهره | `genPawn` / `addPromotions` — حرکت با `promotion:"q|r|b|n"` |
| کیش، کیش‌ودوبعدی، مات و پات | `Board.inCheck` + نبودِ حرکت قانونی ⇒ `evaluateEnd()` |
| تساوی‌ها: تکرار سه‌باره، قانون ۵۰ حرکت، مهرهٔ ناکافی، توافق | `repetitionKey` / `halfmoveClock` / `insufficientMaterial` / `accept_draw` |
| استاندارد SAN برای نوار حرکت‌ها (`exd5`, `Nbd2`, `O-O-O`, `e8=Q`, `+`/`#`) | `Board.san` |
| امتیاز: برد ۲، مساوی ۱ به ۱ | `result()` |

## قرارداد

* **حرکت‌ها:** `move` با `{"from":"e2","to":"e4","promotion":"q"}` (اختیاری) — خانه‌ها با نام جبری؛
  `resign`، `offer_draw`، `accept_draw`، `decline_draw`. هیچ حرکتی در `ui` ساخته نمی‌شود؛ UI همان
  `uci` را از `legalMoves`ِ view خودش برمی‌دارد.
* **گزینه‌های اتاق:** `colors` (`first_white`/`random` با Rng همان seed)، `botLevel` (۱..۴)،
  `startFen` (برای تمرین و تست)، `turnSeconds` (برای تایمرِ لایهٔ session).
* **view عمومی:** `fen, turn, white, black, status, check, ply, botLevel, lastMove, san[], uci[],
  captured{w,b}, drawOfferBy, outcome{reason,winner}` — هیچ‌وقت حرکت قانونی یا نوبتِ پنهان ندارد.
* **view خصوصی:** عمومی + `you { player, color, myTurn, legalMoves[], actions[], drawOfferedToMe }`
  (تست `ChessPrivacyTest` ثابت می‌کند بلوک `you` در view عمومی نیست).
* **انتشارها:** `moved`, `resigned`, `draw_offered`, `draw_agreed`, `draw_declined` + فید و صدا
  (`move`, `capture`, `check`, `castle`, `promote`, `game_end`, `notify`).
* **ربات (`ChessBot`):** negamax با alpha-beta + quiescence + ordering (MVV/LVA، killer، history)،
  ارزیابی مادی + مربع‌های پشتیبانی‌شده + پیادهٔ دورگه/عبوری + ساختار رخ‌ها؛ چهار سطح
  (سطح ۱: عمق ۱ و ۴۰هزار گره … سطح ۴: عمق ۵ و ۹۰۰هزار گره). بدون RNG، بدون دیدن اطلاعات پنهان؛ تکرارِ موقعیت‌های
  دیده‌شده را جریمه می‌کند تا سه‌باره مساوی نشود.

## تست

```bash
./gradlew :games:chess:core:test :games:chess:ui:test
```

* `ChessMoveGenTest` — perft با اعداد مرجع: از شروع تا عمق ۴ (۱۹۷٬۲۸۱)، Kiwipete تا عمق ۳
  (۹۷٬۸۶۲) و سه موقعیت دیگرِ ان‌پاسان/ارتقا/قلعه — یعنی مولدِ حرکت هیچ قانونی را جا نمی‌اندازد.
* `ChessRulesTest` — مات در ۲ حرکت (fool's) و مات در ۴ حرکت (scholar's)، پات، ۵۰ حرکت، تکرار
  سه‌باره، مهرهٔ ناکافی، قلعه از روی خانهٔ کیش‌شده، محدودیت یک‌بارهٔ ان‌پاسان، زیرارتقا، پیام‌های
  خطای فارسی، و SAN.
* `ChessPrivacyTest` — نشتی `legalMoves`/`you` در view عمومی و در انتشارها.
* `ChessDeterminismTest` — همان seed ⇒ همان digest، بازپخش رکورد، رد رکورد دست‌کاری‌شده
  (`MatchVerifier`)، بی‌تأثیری ساعت، پایان ۲۰ بازی ربات-ربات.
* `ChessScoringTest` — شکل `MatchResult` برای برد/مساوی، حالت «بازی از قبل تمام‌شده» با `startFen`.
* `:games:chess:ui` — `ChessTableViewTest` / `ChessSelectionTest` / `ChessSceneTest` /
  `ChessTextTest` برای پل view→صحنه: تجزیهٔ FEN، معنی هر ضربه (انتخاب/حرکت/پیشنهادهٔ مهره/رد)،
  تبدیل حرکت به انیمیشن (ان‌پاسان و قلعه با رخ)، ترتیب مهره‌های گرفته‌شده و متن‌های فارسی.

## میز سه‌بعدی (`games/chess/ui`)

* `ChessEntry` ← صفحهٔ تنظیم (اسم، سطح حریف، رنگ، حالت دو‌نفره، صدا، قوانین) ← `ChessScreen`.
* `ChessLocalMatch` تنها جایی است که `GameEngine.submit` را صدا می‌زند (تک‌نفره با ربات، یا دو نفر
  روی یک گوشی با چرخش خودکار صفحه). `برگرداندن` هم با `GameEngine.replay` روی log کوتاه‌شده انجام
  می‌شود، پس بازی همیشه از `seed` + رکورد بازسازی می‌شود.
* `gl/Chess3DView` یک `GLSurfaceView` است (RENDERMODE_WHEN_DIRTY؛ فقط وقتی تصویر عوض می‌شود رندر
  می‌گیرد): انتخاب EGL با MSAA×۴ و stencil و سه پلهٔ تنزل برای گوشی‌های قدیمی، ژست‌ها با
  `GestureDetector`/`ScaleGestureDetector` (کشیدن = چرخش، نوچ = زوم، ضربه = انتخاب).
* `gl/Shaders.kt` — چهار برنامهٔ GLES 1.00/2.0: مهره‌ها (Blinn-Phong + نور پرکننده + rim +
  تاریکی لبه‌ها)، صفحهٔ چوبی با بافت، چهارضلعیِ تخت برای هایلایت‌ها، و سایهٔ تخت روی صفحهٔ میز.
* `gl/PieceMeshes.kt` — مهره‌ها از پروفیل‌های چرخشی ساخته می‌شوند (پیاده/رخ/وزیر/شاه تخت‌تر، فیل با
  شکاف، اسب چندوجهی با چشم)؛ `MeshBuilder` نرمال و کانال سایه را می‌سازد. هیچ فایل مدل یا تصویر
  خارجی در مخزن نیست.
* `gl/BoardTexture.kt` — بافت ۱۰۲۴² صفحه با `Canvas` ساخته می‌شود (قاب گردویی، نقرِ طلایی،
  رگهٔ چوب، خانه‌های روشن/تیره و مختصات استاندارد a–h / 1–8 در دو طرف (برای سیاه ۱۸۰° چرخیده).
  چوب و دانه‌های آن با یک مولد شبه‌تصادفی قطعی (`Lcg`) تولید می‌شوند، پس بافت همیشه یکسان است.
* بازتاب مهره‌ها روی صفحهٔ براق با stencil و آینه‌کردن محور Y رسم می‌شود؛ انتخاب‌شده کمی بالا می‌آید و
  می‌درخشد، حرکت‌ها با قوس کوتاه و محو‌شدن مهرهٔ گرفته‌شده انیمیت می‌شوند (منطق انیمیشن در
  `gl/BoardScene.kt` و جدا از رندر، تست‌شدنی است).
* `res/raw/chess_*.wav` صداهای رویه‌ای‌ساخته‌اند (مجوز لازم ندارند) و از
  `core/uikit/UiSoundPlayer` پخش می‌شوند.

## محدودیت‌های فعلی

* تایمر هر حرکت در UI نمایش داده نمی‌شود (`turnSeconds` فقط در گزینه‌هاست) تا `core/session` بیاید.
* حالت تماشاچی و اتاق بلوتوثی/آنلاین به `core/session` (M3/M5/M6) گره خورده‌اند؛ صفحه به همان
  `ChessUiState` نگاه می‌کند و تغییری نمی‌خواهد.
* ربات کتاب‌باز (opening book) ندارد؛ از حرکت اول با جست‌وجو تصمیم می‌گیرد.
