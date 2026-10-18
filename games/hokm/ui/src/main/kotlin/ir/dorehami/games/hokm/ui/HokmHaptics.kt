package ir.dorehami.games.hokm.ui

/**
 * HK-023/HK-024 (haptics slice) — haptic feedback as **pure, unit-testable** logic.
 *
 * The roadmap asks for haptics synced with sound. This object defines haptic event types,
 * intensity, and a policy (respecting reduced motion / mute). The Android layer calls
 * Vibrator.
 */
object HokmHaptics {

    enum class Event { CARD_PLAY, CARD_COLLECT, TRICK_WIN, ERROR, BUTTON_TAP, MATCH_WIN }

    data class HapticSpec(val event: Event, val durationMs: Long, val amplitude: Int)

    val CATALOG: Map<Event, HapticSpec> = mapOf(
        Event.CARD_PLAY to HapticSpec(Event.CARD_PLAY, 20, 80),
        Event.CARD_COLLECT to HapticSpec(Event.CARD_COLLECT, 40, 120),
        Event.TRICK_WIN to HapticSpec(Event.TRICK_WIN, 60, 160),
        Event.ERROR to HapticSpec(Event.ERROR, 100, 200),
        Event.BUTTON_TAP to HapticSpec(Event.BUTTON_TAP, 10, 60),
        Event.MATCH_WIN to HapticSpec(Event.MATCH_WIN, 200, 255)
    )

    fun specOf(event: Event): HapticSpec = CATALOG[event]!!

    fun shouldFire(reducedMotion: Boolean, hapticsEnabled: Boolean): Boolean =
        hapticsEnabled && !reducedMotion

    fun duration(event: Event): Long = specOf(event).durationMs

    fun amplitude(event: Event): Int = specOf(event).amplitude
}
