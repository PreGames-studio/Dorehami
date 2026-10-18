package ir.dorehami.games.hokm.ui

/**
 * HK-023 (first slice) — reliable audio execution as **pure, unit-testable** logic.
 *
 * The roadmap asks that sounds play at the right moment, that audio focus / mute /
 * lifecycle are respected, and that sounds do not overlap chaotically. This object is the
 * deterministic policy core: it decides whether a given sound should play now, whether the
 * volume should be ducked, and whether a new sound may interrupt the current one. The
 * Compose/Android layer calls into the platform AudioManager and feeds the results here.
 */
object HokmAudio {

    enum class Focus { GAINED, LOST, DUCK, NONE }
    enum class Lifecycle { FOREGROUND, BACKGROUND, DESTROYED }

    data class Policy(
        val play: Boolean,
        val ducked: Boolean,
        val reason: String
    )

    /** Whether a sound may play given mute/focus/lifecycle. */
    fun policy(muted: Boolean, focus: Focus, lifecycle: Lifecycle): Policy = when {
        muted -> Policy(false, false, "بی‌صدا")
        lifecycle == Lifecycle.DESTROYED -> Policy(false, false, "صفحه بسته شد")
        lifecycle == Lifecycle.BACKGROUND -> Policy(false, false, "برنامه در پس‌زمینه")
        focus == Focus.LOST -> Policy(false, false, "فوکوسِ صدا از دست رفت")
        focus == Focus.DUCK -> Policy(true, true, "صدای پس‌زمینه؛ کم‌حجم")
        focus == Focus.GAINED || focus == Focus.NONE -> Policy(true, false, "پخش مجاز")
        else -> Policy(false, false, "وضعیت نامشخص")
    }

    /** Whether a new sound may interrupt the currently playing one. */
    fun mayInterrupt(currentPlaying: Boolean, newPriority: Int, currentPriority: Int): Boolean {
        if (!currentPlaying) return true
        return newPriority > currentPriority
    }

    /** Cooldown: minimum ms between two sounds of the same kind. */
    const val SAME_KIND_COOLDOWN_MS: Long = 80L

    fun cooldownOk(lastPlayedMs: Long, nowMs: Long): Boolean =
        (nowMs - lastPlayedMs) >= SAME_KIND_COOLDOWN_MS

    /** Volume multiplier for ducked playback. */
    const val DUCKED_VOLUME: Float = 0.3f

    fun volumeFor(ducked: Boolean, baseVolume: Float = 1.0f): Float =
        if (ducked) baseVolume * DUCKED_VOLUME else baseVolume

    /** Priority levels (higher = more important). */
    const val PRIORITY_LOW: Int = 1
    const val PRIORITY_NORMAL: Int = 2
    const val PRIORITY_HIGH: Int = 3
    const val PRIORITY_CRITICAL: Int = 4
}
