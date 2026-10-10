package ir.dorehami.gamesdk

import kotlinx.serialization.json.JsonObject

/**
 * ورودیِ ساختِ یک نشست: سید، تنظیمات، بازیکن‌ها، ساعت و تصادف.
 *
 * @property matchId شناسهٔ یکتای دست (در رکورد ذخیره می‌شود).
 * @property seed سیدِ قطعی؛ پخش کارت و هر تصادف دیگر فقط از همین می‌آید.
 * @property options تنظیماتِ تأییدشدهٔ میزبان (خروجیِ [GameModule.validateOptions]).
 * @property players بازیکن‌ها به ترتیبِ صندلی (اندیسِ لیست = شمارهٔ صندلی).
 * @property clock ساعتِ تزریقی؛ بازی هرگز ساعت سیستم را نمی‌خواند.
 * @property rng تصادفِ قطعیِ تزریقی.
 */
data class GameConfig(
    val matchId: String,
    val seed: Long,
    val options: JsonObject,
    val players: List<Player>,
    val clock: Clock,
    val rng: Rng,
) {
    init {
        require(players.isNotEmpty()) { "a match needs at least one player" }
    }

    /** بازیکنِ نشسته روی صندلی [seat]. */
    fun playerAt(seat: Int): Player = players[seat]

    /** شمارهٔ صندلیِ یک بازیکن؛ اگر عضو این دست نیست `null`. */
    fun seatOf(player: PlayerId): Int? = players.indexOfFirst { it.id == player }.takeIf { it >= 0 }
}

/**
 * قراردادِ نشستِ بازی (هفت سؤال، سؤال‌های ۲ تا ۵ و ۷).
 *
 * سه قاعدهٔ تغییرناپذیر:
 * 1. **قطعیت:** خروجیِ `(seed + لاگ حرکت‌ها)` همیشه یکی است — هیچ تصادف و ساعتِ سیستمی در کار نیست.
 * 2. **حرکتِ بی‌اعتبار = رد با کد.** هیچ استثنایی پرت نمی‌شود؛ [ApplyResult.Rejected] برمی‌گردد.
 * 3. **اطلاعات پنهان، پنهان می‌ماند:** کلیدِ دادهٔ خصوصی در [publicView] اصلاً **وجود ندارد**
 *    (نه اینکه خالی باشد).
 */
interface GameSession {
    /** مرحلهٔ فعلی. */
    val phase: Phase

    /** شمارهٔ وضعیت: با هر حرکتِ موفق یکی جلو می‌رود و در رد شدن تغییر نمی‌کند. */
    val version: Long

    /** سؤال ۴: این حرکت را قبول می‌کنی یا رد؟ */
    fun apply(player: PlayerId, action: GameAction): ApplyResult

    /** سؤال ۳ (بخش عمومی): همه چه می‌بینند — بدون دست/نقش/کلمهٔ هیچ‌کس. */
    fun publicView(): JsonObject

    /** سؤال ۳ (بخش خصوصی): فقط چیزی که خودِ این بازیکن حق دارد ببیند. */
    fun privateView(player: PlayerId): JsonObject

    /** سؤال ۲: نوعِ حرکت‌های مجاز برای این بازیکن در همین لحظه (فقط شناسه‌ها). */
    fun legalActions(player: PlayerId): List<String>

    /** سؤال ۵: جدول پایانی؛ تا وقتی بازی تمام نشده `null`. */
    fun result(): MatchResult?

    /** سؤال ۷: وضعیت کامل برای ذخیره‌سازی و ادامه‌دادنِ بعدی. */
    fun snapshot(): JsonObject

    /**
     * رویدادهایی که در همان لحظهٔ ساختِ نشست تولید شده‌اند (مثل پخشِ کارتِ اولیه یا تعیینِ حاکم).
     *
     * نشست نمی‌تواند در سازنده خروجی بدهد، بنابراین این رویدادها را نگه می‌دارد و موتور
     * ([GameEngine]) بلافاصله بعد از ساخت آن‌ها را برمی‌دارد و در `bootEmissions` می‌گذارد.
     * پیاده‌سازی پیش‌فرض خالی است.
     */
    fun drainPendingEmissions(): List<Emission> = emptyList()
}

/**
 * قراردادِ ماژول بازی (سؤال‌های ۱ و ۶).
 *
 * هر بازی یک `object` از همین interface می‌سازد و در [GameRegistry] ثبت می‌کند. از آن لحظه لابی،
 * اتاق بلوتوثی، حریف تصادفی، تایمر و ذخیرهٔ نتیجه خودکار به آن می‌رسند.
 */
interface GameModule {
    /** سؤال ۱: این بازی چیست و چند نفره است. */
    val meta: GameMeta

    /** تنظیماتِ پیشنهادیِ میزبان (همان [GameMeta.defaultOptions] اگر نیاز به محاسبه نباشد). */
    fun defaultOptions(): JsonObject = meta.defaultOptions

    /**
     * اعتبارسنجیِ تنظیماتِ میزبان.
     *
     * @throws GameConfigException با پیامِ **فارسی** اگر تنظیمات نامعتبر باشد.
     * @return تنظیماتِ پاک‌شده و کامل‌شده (مقادیرِ پیش‌فرض پر می‌شوند).
     */
    fun validateOptions(options: JsonObject): JsonObject

    /** ساختِ نشست از یک سید. */
    fun create(config: GameConfig): GameSession

    /** برای پر کردنِ اتاق در حریف تصادفی: چند نفر لازم است تا دست شروع شود. */
    fun preferredPartySize(): Int = meta.minPlayers

    /**
     * سؤال ۶: سیاستِ ربات. `null` یعنی این بازی ربات ندارد و نمی‌تواند در صفِ رندوم شرکت کند.
     *
     * ربات هم **فقط** از راه [GameSession.apply] بازی می‌کند و در همان لاگ ثبت می‌شود؛
     * هیچ میان‌بُری داخل موتور ندارد.
     */
    val botAction: ((GameSession, PlayerId) -> GameAction?)?
}

/** تنظیماتِ نامعتبرِ میزبان. پیام آن مستقیماً به کاربر نشان داده می‌شود، پس فارسی است. */
class GameConfigException(val code: String, messageFa: String) : IllegalArgumentException(messageFa)
