package ir.dorehami.games.hokm.ui

/**
 * HK-047 (first slice) — sticker/reaction catalog as **pure, unit-testable** logic.
 *
 * The roadmap mentions stickers/reactions (Hokm Online, Hokm Plus). This object defines
 * the sticker catalog, cooldown, and a sender. The Android layer renders and sends.
 */
object HokmStickers {

    enum class Sticker(val code: String, val labelFa: String) {
        THUMBS_UP("thumbs_up", "👍"),
        CLAP("clap", "👏"),
        LAUGH("laugh", "😂"),
        THINK("think", "🤔"),
        SAD("sad", "😢"),
        ANGRY("angry", "😠"),
        FIRE("fire", "🔥"),
        HEART("heart", "❤️"),
        LUCK("luck", "🍀"),
        WOW("wow", "😮"),
        SLEEP("sleep", "😴"),
        COOL("cool", "😎")
    }

    const val COOLDOWN_MS: Long = 2000L
    const val MAX_STICKERS_PER_MATCH: Int = 50

    data class SendResult(val allowed: Boolean, val reason: String)

    fun canSend(lastSentMs: Long, nowMs: Long, sentCount: Int): SendResult {
        if (sentCount >= MAX_STICKERS_PER_MATCH) return SendResult(false, "حداکثر استیکر در یک بازی")
        if (nowMs - lastSentMs < COOLDOWN_MS) return SendResult(false, "کمی صبر کنید")
        return SendResult(true, "")
    }

    fun byCode(code: String): Sticker? = Sticker.values().firstOrNull { it.code == code }

    fun allCodes(): List<String> = Sticker.values().map { it.code }

    fun count(): Int = Sticker.values().size
}
