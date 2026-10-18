package ir.dorehami.games.hokm.ui

/**
 * HK-053 (maintenance slice) — maintenance mode as **pure, unit-testable** logic.
 *
 * The roadmap asks for release gates / maintenance. This object defines the maintenance
 * state and a message policy. The Android layer fetches remote config.
 */
object HokmMaintenance {

    data class Status(
        val maintenance: Boolean,
        val messageFa: String,
        val estimatedEndMs: Long?
    )

    fun isActive(s: Status): Boolean = s.maintenance

    fun defaultMessage(): String = "سرور در حال تعمیر است. لطفاً بعداً تلاش کنید."

    fun remainingMs(s: Status, nowMs: Long): Long? =
        s.estimatedEndMs?.let { (it - nowMs).coerceAtLeast(0) }

    fun shouldBlockPlay(s: Status): Boolean = s.maintenance
}
