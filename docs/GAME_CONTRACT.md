# قرارداد بازی — «قطعهٔ لِگو» چه شکلی است؟

> **خلاصهٔ فارسی:** هر بازی دقیقاً به **هفت سؤال** جواب می‌دهد و هیچ سؤال دیگری ندارد. اگر یک بازی
> این هفت جواب را بدهد، لابی، اتاق بلوتوثی، حریف تصادفی، تایمر نوبت، ذخیرهٔ نتیجه و ضدتقلب
> **خودکار** به آن می‌رسند. این سند همان قرارداد است: برای برنامه‌نویس‌ها، برای عامل‌های هوش مصنوعی،
> و برای تست‌ها.

---

## ۱. هفت سؤال (به زبان ساده)

| # | سؤال | جواب در کد |
|---|---|---|
| ۱ | **چند نفر؟** تیمی است یا انفرادی؟ چه قابلیت‌هایی دارد (آفلاین، رندوم، ربات…)؟ | `GameMeta` |
| ۲ | **وقتی نوبت من است چه حرکت‌هایی مجاز است؟** | `legalActions(player)` |
| ۳ | **همه چه چیزی را می‌بینند؟ من چه چیزی را می‌بینم؟** | `publicView()` / `privateView(player)` |
| ۴ | **کدام حرکت را قبول می‌کنی و کدام را رد؟** | `apply(player, action)` |
| ۵ | **بازی کِی تمام می‌شود و رتبه‌بندی نهایی چیست؟** | `phase`, `result()` |
| ۶ | **ربات چطور بازی کند؟** | `GameModule.botAction` |
| ۷ | **وضعیت چطور ذخیره و بعداً از همان‌جا ادامه داده شود؟** | `snapshot()` + موتور (بازپخش از رکورد) |

نکتهٔ حیاتی: **قوانین بازی هیچ‌وقت با اتاق حرف نمی‌زنند.** نه سوکت، نه بلوتوث، نه سرور، نه تایمر.
بازی فقط می‌گوید «این حرکت مجاز است / این نیست»؛ فرستادن و پخش‌کردن کار هسته است.

## ۲. قرارداد در کد

```kotlin
interface GameModule {
    val meta: GameMeta                      // سؤال ۱
    fun defaultOptions(): JsonObject        // تنظیمات پیشنهادی اتاق
    fun validateOptions(options: JsonObject): JsonObject   // تنظیمات نامعتبر ⇒ GameConfigException با پیام فارسی
    fun create(config: GameConfig): GameSession            // ساخت نشست از یک سید
    fun preferredPartySize(): Int = meta.minPlayers        // برای پر کردن اتاق در حریف تصادفی
    val botAction: ((GameSession, PlayerId) -> GameAction?)?  // سؤال ۶ (اختیاری ولی برای رندوم لازم)
}

interface GameSession {
    val phase: Phase                        // SETUP / PLAYING / FINISHED
    val version: Long                       // با هر حرکت +۱ (شمارهٔ وضعیت)
    fun apply(player: PlayerId, action: GameAction): ApplyResult   // سؤال ۴
    fun publicView(): JsonObject            // سؤال ۳ (بدون اطلاعات پنهان دیگران)
    fun privateView(player: PlayerId): JsonObject   // سؤال ۳ (فقط خودِ بازیکن)
    fun legalActions(player: PlayerId): List<String> // سؤال ۲ (رشته‌های ثابت، مثل "play_card")
    fun result(): MatchResult?              // سؤال ۵ (null تا وقتی بازی تمام نشده)
    fun snapshot(): JsonObject              // سؤال ۷ (وضعیت کامل برای ذخیره)
}
```

و نتیجهٔ یک حرکت:

```kotlin
sealed interface ApplyResult {
    data class Ok(val emissions: List<Emission>) : ApplyResult
    data class Rejected(val code: String, val messageFa: String) : ApplyResult
}

sealed interface Emission {                 // «چه چیزی پخش شود»
    data class Public(val type: String, val payload: JsonObject)      // برای همه
    data class Private(val to: PlayerId, val view: JsonObject)        // فقط برای یک نفر
    data class Feed(val key: String, val args: JsonObject, val kind: String)  // خط فید («X حکم را برد»)
    data class Sound(val name: String)                                // افکت صوتی رابط کاربری
}
```

### معنای دقیق چند چیز

* **`version`**: با هر حرکت موفق یکی جلو می‌رود. کلاینت‌ها از آن برای همگام‌سازی استفاده می‌کنند؛
  موتور آن را مدیریت می‌کند، بازی فقط صادقانه افزایشش می‌دهد.
* **`code` در رد**: از فهرست `ErrCodes` (مثل `game.not_your_turn`). رابط کاربری روی کد پیام فارسی
  نشان می‌دهد، پس کدها بخشی از قراردادند و نباید عوض شوند.
* **`Feed.key`**: کلید ترجمه‌پذیر است (`trick_won`, `word_correct`) و `args` دادهٔ آن. متن نهایی در
  رابط کاربری ساخته می‌شود، نه در سرور — تا فید فارسی و یکدست بماند.
* **`Sound.name`**: نام ثابت و از پیش تعیین‌شده (`card_played`, `correct`, `wrong`). بازی فایل صوتی
  نمی‌فرستد؛ فقط نام می‌دهد.
* **`Clock` و `Rng`**: از `GameConfig` می‌آیند. **هیچ بازی‌ای حق ندارد ساعت یا تصادف سیستم را
  صدا بزند.** دلیلش در `docs/adr/ADR-0003` است: بدون این، نه ادامه‌دادن بعد از بسته‌شدن برنامه
  ممکن است، نه اثبات نتیجه.

## ۳. هفت قاعدهٔ تغییرناپذیر هر بازی

1. **قطعیت:** نتیجهٔ `(seed + action log)` همیشه یکی است. بازپخش باید همان `digest` را بدهد.
2. **اطلاعات پنهان ساختاری:** کلید اطلاعات خصوصی در `publicView()` **وجود ندارد** (نه اینکه خالی
   باشد). تست‌ها این را می‌سنجند.
3. **حرکت بی‌اعتبار = رد با کد.** هیچ استثنایی پرت نمی‌شود؛ `Rejected` برمی‌گردد.
4. **بدون وابستگی بیرونی:** بازی فقط `core/gamesdk` و `core/protocol` را می‌شناسد (و در پوشهٔ UI،
   `core/uikit`). نه سرور، نه ترنسپورت، نه بازی دیگر.
5. **ربات هم مثل آدم:** ربات فقط از راه `GameAction` بازی می‌کند و در همان لاگ ثبت می‌شود —
   هیچ میان‌بُری داخل موتور ندارد.
6. **گزینه‌ها JSON ساده‌اند:** کلیدها ثابت (`"targetPoints"`، `"turnSeconds"`…) و اعتبارسنجی در
   `validateOptions` با پیام خطای فارسی.
7. **وضعیت = داده:** هیچ field غیرسریال‌پذیری در نشست نیست؛ هر چیزی که لازم است در
   `snapshot()`/بازپخش قابل تولید است.

## ۴. قالب آماده (کپی کن، پرش کن)

```kotlin
// games/mygame/core/src/main/kotlin/ir/dorehami/games/mygame/MyGameModule.kt
object MyGameModule : GameModule {

    const val ACTION_MOVE = "move"          // شناسه‌های حرکت: ثابت و snake_case

    object Options { const val TURN_SECONDS = "turnSeconds" }

    override val meta = GameMeta(
        id = GameId("mygame"),
        titleFa = "نام بازی",
        titleEn = "My Game",
        taglineFa = "یک خط توضیح برای کارت بازی",
        minPlayers = 2, maxPlayers = 4,
        teamCount = 0, playersPerTeam = 0,       // تیمی بود پر کن
        iconKey = "mygame",
        estimatedMinutes = 10,
        capabilities = setOf(                     // سؤال ۱
            Capability.BLUETOOTH_OFFLINE.name, Capability.ONLINE.name,
            Capability.RANDOM_MATCH.name, Capability.BOTS.name,
        ),
        defaultOptions = buildJsonObject { put(Options.TURN_SECONDS, JsonPrimitive(30)) },
    )

    override fun validateOptions(options: JsonObject): JsonObject = options   // + بررسی‌های لازم

    override fun create(config: GameConfig): GameSession = MyGameSession(config)

    override val botAction: ((GameSession, PlayerId) -> GameAction?)? = { session, player ->
        (session as? MyGameSession)?.botAction(player)     // قطعی، بدون RNG
    }
}
```

```kotlin
// games/mygame/core/src/main/kotlin/ir/dorehami/games/mygame/MyGameSession.kt
class MyGameSession(private val config: GameConfig) : GameSession {

    override var phase: Phase = Phase.PLAYING
        private set
    override var version: Long = 0L
        private set

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        // ۱) نوبتش است؟ ۲) حرکت مجاز است؟ ۳) اعمال کن ۴) version++
        return ApplyResult.Rejected(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
    }

    override fun publicView(): JsonObject = view { /* فقط چیزهای عمومی */ }
    override fun privateView(player: PlayerId): JsonObject = view { /* + دست/نقش خودش */ }
    override fun legalActions(player: PlayerId): List<String> = emptyList()
    override fun result(): MatchResult? = null
    override fun snapshot(): JsonObject = view { /* وضعیت کامل */ }

    /** قطعی: فقط از وضعیت فعلی + بدون تصادفِ اضافه. */
    fun botAction(player: PlayerId): GameAction? = null
}
```

(توابع کمکی `view { … }` برای ساختن JSON بدون دردسر در `core/gamesdk` هستند.)

## ۵. چهار تستی که هر بازی باید داشته باشد

| تست | چه چیزی را ثابت می‌کند |
|---|---|
| **قواعد** | حرکت غیرمجاز با کد درست رد می‌شود؛ جریان بازی تا پایان درست است |
| **نشت اطلاعات** | دادهٔ خصوصی بازیکن دیگر در `publicView()` نیست؛ `privateView` هرکس فقط مال خودش است |
| **قطعیت** | یک بازی کامل با ربات‌ها اجرا، لاگ ذخیره، بازپخش ⇒ همان `digest` و همان جدول |
| **امتیاز و پایان** | حالت‌های مرزی (مساوی، برد زودهنگام، تساوی امتیاز) و شکل `result()` |

قالب تست‌ها در `games/<بازی>/core/src/test/kotlin/...`. از فیک‌های `core/testkit` استفاده کن؛
هیچ تستی به شبکه یا امولاتور نیاز ندارد.

## ۶. چک‌لیست «قرارداد رعایت شد»

- [ ] هفت سؤال جواب داده شده و همه در کد جا افتاده‌اند (نه فقط در ذهن من).
- [ ] هیچ importی از بازی دیگر، سرور یا ترنسپورت وجود ندارد.
- [ ] داخل پوشهٔ بازی هیچ `System.currentTimeMillis()` و هیچ `Random()` نیست.
- [ ] چهار تست بخش ۵ سبز است.
- [ ] `version` با هر حرکت موفق یکی جلو می‌رود و در `Rejected` تغییری نمی‌کند.
- [ ] پیام‌های خطا فارسی و کدها از `ErrCodes` هستند.
- [ ] بازی در دو جا ثبت شده: `GameRegistry` اپ و `ServerGames` سرور.
- [ ] ربات بدون مصرف RNG بازی می‌کند و در لاگ مثل هر بازیکن دیگری ثبت می‌شود.
