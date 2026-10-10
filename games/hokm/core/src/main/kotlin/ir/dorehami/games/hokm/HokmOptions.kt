package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.optInt
import ir.dorehami.gamesdk.optString
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * تنظیماتِ میزبان برای حکم (سؤال ۱ قرارداد).
 *
 * کلیدها ثابت‌اند و در JSONِ اتاق با همین نام‌ها می‌روند. هر مقدار نامعتبر با یک پیامِ **فارسی**
 * رد می‌شود تا میزبان دقیقاً بفهمد چه چیزی را باید درست کند.
 */
data class HokmOptions(
    /** امتیازی که یک تیم با رسیدن به آن برندهٔ کل بازی می‌شود (پیش‌فرض ۷). */
    val targetPoints: Int = DEFAULT_TARGET_POINTS,
    /** کی دست تمام می‌شود: با رسیدنِ یک تیم به ۷ دست یا بعد از همهٔ ۱۳ دست. */
    val handEnd: HandEndMode = HandEndMode.SEVEN,
    /** قانونِ «حکم لازم» (مجبور بودن به بالاتر زدن وقتی حکم روی زمین افتاده). */
    val hokmLazim: HokmLazimMode = HokmLazimMode.OFF,
    /** امتیازِ کوت برای تیمِ حاکم (پیش‌فرض ۲). */
    val kotHakemPoints: Int = DEFAULT_KOT_HAKEM,
    /** امتیازِ کوت برای تیمِ مقابلِ حاکم، یعنی «حاکم‌کُتی» (پیش‌فرض ۳). */
    val kotOpponentPoints: Int = DEFAULT_KOT_OPPONENT,
    /** مهلتِ هر نوبت به ثانیه؛ `0` یعنی بدون محدودیت (پیش‌فرض). */
    val turnSeconds: Int = DEFAULT_TURN_SECONDS,
) {
    /** آیا مهلتِ نوبت فعال است؟ */
    val hasTurnClock: Boolean
        get() = turnSeconds > 0

    fun toJson(): JsonObject = view {
        put(KEY_TARGET_POINTS, JsonPrimitive(targetPoints))
        put(KEY_HAND_END, JsonPrimitive(handEnd.key))
        put(KEY_HOKM_LAZIM, JsonPrimitive(hokmLazim.key))
        put(KEY_KOT_HAKEM, JsonPrimitive(kotHakemPoints))
        put(KEY_KOT_OPPONENT, JsonPrimitive(kotOpponentPoints))
        put(KEY_TURN_SECONDS, JsonPrimitive(turnSeconds))
    }

    companion object {
        const val KEY_TARGET_POINTS = "targetPoints"
        const val KEY_HAND_END = "handEnd"
        const val KEY_HOKM_LAZIM = "hokmLazim"
        const val KEY_KOT_HAKEM = "kotHakemPoints"
        const val KEY_KOT_OPPONENT = "kotOpponentPoints"
        const val KEY_TURN_SECONDS = "turnSeconds"

        const val DEFAULT_TARGET_POINTS = 7
        const val DEFAULT_KOT_HAKEM = 2
        const val DEFAULT_KOT_OPPONENT = 3
        const val DEFAULT_TURN_SECONDS = 0

        /** تنظیماتِ پیشنهادی: همان حکمِ استانداردی که در ایران بازی می‌شود. */
        fun default(): HokmOptions = HokmOptions()

        /** تنظیماتِ پیش‌فرض به صورت JSON (برای `GameMeta.defaultOptions`). */
        fun defaultJson(): JsonObject = default().toJson()

        /**
         * اعتبارسنجی و کامل‌سازیِ تنظیمات.
         *
         * @throws GameConfigException با پیام فارسی اگر مقداری نامعتبر باشد.
         */
        fun parse(options: JsonObject): HokmOptions {
            val targetPoints = options.intOrNull(KEY_TARGET_POINTS) ?: DEFAULT_TARGET_POINTS
            if (targetPoints !in 1..25) {
                throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "امتیازِ هدف باید بین ۱ تا ۲۵ باشد (مقدار فعلی: $targetPoints)",
                )
            }

            val handEndRaw = options.optString(KEY_HAND_END) ?: HandEndMode.SEVEN.key
            val handEnd = HandEndMode.fromKey(handEndRaw)
                ?: throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "پایانِ دست فقط می‌تواند «seven» (رسیدن به ۷ دست) یا «thirteen» (بازیِ همهٔ ۱۳ دست) باشد",
                )

            val lazimRaw = options.optString(KEY_HOKM_LAZIM) ?: HokmLazimMode.OFF.key
            val hokmLazim = HokmLazimMode.fromKey(lazimRaw)
                ?: throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "قانونِ حکمِ لازم فقط می‌تواند «off» یا «overtrump» باشد",
                )

            val kotHakem = options.intOrNull(KEY_KOT_HAKEM) ?: DEFAULT_KOT_HAKEM
            if (kotHakem !in 1..9) {
                throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "امتیازِ کوت باید بین ۱ تا ۹ باشد (مقدار فعلی: $kotHakem)",
                )
            }

            val kotOpponent = options.intOrNull(KEY_KOT_OPPONENT) ?: DEFAULT_KOT_OPPONENT
            if (kotOpponent !in 1..9) {
                throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "امتیازِ حاکم‌کُتی باید بین ۱ تا ۹ باشد (مقدار فعلی: $kotOpponent)",
                )
            }

            val turnSeconds = options.intOrNull(KEY_TURN_SECONDS) ?: DEFAULT_TURN_SECONDS
            if (turnSeconds !in 0..600) {
                throw GameConfigException(
                    ErrCodes.BAD_OPTIONS,
                    "مهلتِ هر نوبت باید بین ۰ (بدون محدودیت) تا ۶۰۰ ثانیه باشد",
                )
            }

            return HokmOptions(
                targetPoints = targetPoints,
                handEnd = handEnd,
                hokmLazim = hokmLazim,
                kotHakemPoints = kotHakem,
                kotOpponentPoints = kotOpponent,
                turnSeconds = turnSeconds,
            )
        }

        private fun JsonObject.intOrNull(key: String): Int? {
            val value = this[key]?.jsonPrimitive ?: return optInt(key)
            return value.intOrNull ?: optInt(key)
        }
    }
}

/** کی یک دست تمام می‌شود. */
enum class HandEndMode(val key: String, val faName: String) {
    /** استانداردِ حکم: به محض اینکه یک تیم به ۷ دست برسد، دست تمام می‌شود. */
    SEVEN("seven", "با رسیدن به ۷ دست"),

    /** سخت‌گیرانه‌تر: همهٔ ۱۳ دست بازی می‌شود و تیمی که دستِ بیشتری گرفته برنده است. */
    THIRTEEN("thirteen", "بازی تا آخرِ ۱۳ دست"),
    ;

    companion object {
        fun fromKey(key: String): HandEndMode? = entries.firstOrNull { it.key == key }
    }
}

/** قانونِ «حکم لازم». */
enum class HokmLazimMode(val key: String, val faName: String) {
    /** قانونِ استاندارد (pagat): فقط باید خالِ زمین را جواب بدهی. */
    OFF("off", "غیرفعال"),

    /** اگر حکمی روی زمین افتاده و می‌توانی بالاتر بزنی، باید بالاتر بزنی. */
    OVERTRUMP("overtrump", "باید بالاتر بزنی"),
    ;

    companion object {
        fun fromKey(key: String): HokmLazimMode? = entries.firstOrNull { it.key == key }
    }
}

/** کلیدهای ترجمه‌پذیرِ خطِ فید (رابط کاربری جملهٔ فارسی را از این کلیدها می‌سازد). */
object HokmFeed {
    const val HAKEM_CHOSEN = "hokm.hakem_chosen"
    const val TRUMP_DECLARED = "hokm.trump_declared"
    const val CARD_PLAYED = "hokm.card_played"
    const val TRICK_WON = "hokm.trick_won"
    const val TRICK_RUFFED = "hokm.trick_ruffed"
    const val HAND_SCORED = "hokm.hand_scored"
    const val KOT = "hokm.kot"
    const val HAKEM_KOTI = "hokm.hakem_koti"
    const val HAND_LOST = "hokm.hand_lost"
    const val MATCH_FINISHED = "hokm.match_finished"
    const val NEW_HAND = "hokm.new_hand"
}

/** نام‌های ثابتِ افکت صوتی (بازی فقط نام می‌دهد؛ فایل صوتی در ماژول UI است). */
object HokmSounds {
    const val DEAL = "card_deal"
    const val PLAY = "card_played"
    const val TRICK_WON = "trick_won"
    /** وقتی کسی خالِ زمین را با حکم می‌بُرد. */
    const val RUFF = "trick_ruffed"
    const val TRICK_LOST = "trick_lost"
    const val TRUMP_SET = "trump_set"
    const val HAND_WON = "hand_won"
    const val HAND_LOST = "hand_lost"
    const val KOT = "kot"
    const val HAKEM_KOTI = "hakem_koti"
    const val MATCH_WON = "match_won"
    const val MATCH_LOST = "match_lost"
    const val YOUR_TURN = "your_turn"
    const val INVALID = "invalid_move"
}

/** نوعِ رویدادهای عمومی‌ای که نشست منتشر می‌کند (برای انیمیشن و همگام‌سازی). */
object HokmEvents {
    const val HAKEM_FLIP = "hokm.hakem_flip"
    const val HAND_DEALT = "hokm.hand_dealt"
    const val TRUMP_SET = "hokm.trump_set"
    const val CARD_PLAYED = "hokm.card_played"
    const val TRICK_WON = "hokm.trick_won"
    const val HAND_SCORED = "hokm.hand_scored"
    const val MATCH_FINISHED = "hokm.match_finished"
}
