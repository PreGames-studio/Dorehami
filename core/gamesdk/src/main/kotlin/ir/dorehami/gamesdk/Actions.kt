package ir.dorehami.gamesdk

import kotlinx.serialization.json.JsonObject

/**
 * یک حرکتِ بازیکن: «من می‌خواهم این کار را بکنم».
 *
 * حرکت فقط دو چیز دارد: یک **نوعِ ثابت** (مثل `"play_card"`) و یک **بدنهٔ JSONِ ساده**. قواعد بازی
 * خودش تصمیم می‌گیرد مجاز است یا نه؛ کلاینت هیچ‌وقت وضعیتِ بعدی را محلی حساب نمی‌کند.
 */
data class GameAction(
    val type: String,
    val payload: JsonObject = JsonObject(emptyMap()),
)

/** نتیجهٔ اعمال یک حرکت روی نشست. */
sealed interface ApplyResult {
    /** حرکت پذیرفته شد؛ این رویدادها باید پخش شوند. */
    data class Ok(val emissions: List<Emission>) : ApplyResult

    /** حرکت رد شد. `code` از [ir.dorehami.protocol.ErrCodes] و `messageFa` آمادهٔ نمایش است. */
    data class Rejected(val code: String, val messageFa: String) : ApplyResult
}

/**
 * «چه چیزی پخش شود» بعد از یک حرکت موفق.
 *
 * چهار نوع انتشار داریم و بس: عمومی، خصوصی (فقط یک نفر)، خطِ فید (جملهٔ خوانا برای همه) و افکت صوتی.
 * بازی هرگز فایل صوتی یا متنِ نهایی نمی‌فرستد — فقط کلید می‌دهد و رابط کاربری ترجمه و پخش می‌کند.
 */
sealed interface Emission {
    /** به‌روزرسانیِ وضعیتِ عمومی برای همه (بدون هیچ دادهٔ پنهان). */
    data class Public(val type: String, val payload: JsonObject) : Emission

    /** وضعیتِ فقط-برای-یک-نفر (مثل دستِ خودش). هرگز برای بقیه فرستاده نمی‌شود. */
    data class Private(val to: PlayerId, val view: JsonObject) : Emission

    /**
     * خطِ خوانای فید.
     *
     * @param key کلید ترجمه‌پذیر (`trick_won`, `trump_declared`…)
     * @param args دادهٔ لازم برای ساخت جمله (`{"player":"p1"}`)
     * @param kind نوع خط برای رنگ/شدت نمایش: `info` | `good` | `bad` | `big`
     */
    data class Feed(val key: String, val args: JsonObject, val kind: String = "info") : Emission

    /** نامِ ثابت افکت صوتی (`card_played`, `kot`…). */
    data class Sound(val name: String) : Emission
}

/** یک بازیکنِ نشسته پشت میز. */
data class Player(
    val id: PlayerId,
    val displayName: String,
    val seat: Int,
    val isBot: Boolean = false,
    val team: Int = -1,
)

/** جایگاه یک بازیکن در جدول پایانی. */
data class Standing(
    val player: PlayerId,
    val rank: Int,
    val score: Int,
    val detail: JsonObject = JsonObject(emptyMap()),
)

/** امتیاز نهایی یک تیم (برای بازی‌های تیمی). */
data class TeamScore(
    val team: Int,
    val rank: Int,
    val score: Int,
    val members: List<PlayerId>,
    val detail: JsonObject = JsonObject(emptyMap()),
)

/**
 * پاسخِ «سؤال ۵»: بازی کِی تمام می‌شود و رتبه‌بندی نهایی چیست.
 *
 * @property finishedAtMs زمان پایان از نگاه [Clock] (برای نمایشِ «چقدر طول کشید»).
 * @property standings رتبه‌بندیِ بازیکن‌ها (رتبهٔ ۱ = برنده).
 * @property teams رتبه‌بندی تیمی اگر بازی تیمی باشد، وگرنه `null`.
 * @property summary دادهٔ آزاد برای صفحهٔ پایان بازی (مثل تعداد کوت و دست‌های برده).
 */
data class MatchResult(
    val finishedAtMs: Long,
    val standings: List<Standing>,
    val teams: List<TeamScore>? = null,
    val summary: JsonObject = JsonObject(emptyMap()),
) {
    /** برندهٔ بازی به صورت شناسهٔ بازیکن (در بازی تیمی: عضو اول تیمِ اول). */
    val winnerPlayerId: PlayerId?
        get() = standings.firstOrNull { it.rank == 1 }?.player

    /** برندهٔ بازی به صورت شمارهٔ تیم اگر بازی تیمی باشد. */
    val winnerTeam: Int?
        get() = teams?.firstOrNull { it.rank == 1 }?.team
}
