package ir.dorehami.games.hokm.ui

/**
 * HK-022 (first slice) — sound bank as **pure, unit-testable** logic.
 *
 * The roadmap asks for a complete sound bank with mapping, preload, and no duplicate
 * sounds after reconnect. This object defines the catalog of game sound events, their
 * priority (for interruption), and a lookup. The Android layer maps each [SoundEvent] to
 * a raw resource id and preloads them.
 */
object HokmSoundBank {

    enum class SoundEvent {
        CARD_DEAL, CARD_PLAY, CARD_SLIDE, CARD_FLIP, CARD_COLLECT,
        TRUMP_REVEAL, TRICK_WIN, HAND_WIN, MATCH_WIN, MATCH_LOSS,
        BUTTON_TAP, ERROR, NOTIFICATION, SHUFFLE, STICKER
    }

    data class SoundSpec(
        val event: SoundEvent,
        val fileName: String,
        val priority: Int,
        val cooldownMs: Long
    )

    val CATALOG: Map<SoundEvent, SoundSpec> = mapOf(
        SoundEvent.CARD_DEAL to SoundSpec(SoundEvent.CARD_DEAL, "card_deal", HokmAudio.PRIORITY_NORMAL, 60),
        SoundEvent.CARD_PLAY to SoundSpec(SoundEvent.CARD_PLAY, "card_play", HokmAudio.PRIORITY_NORMAL, 40),
        SoundEvent.CARD_SLIDE to SoundSpec(SoundEvent.CARD_SLIDE, "card_slide", HokmAudio.PRIORITY_LOW, 80),
        SoundEvent.CARD_FLIP to SoundSpec(SoundEvent.CARD_FLIP, "card_flip", HokmAudio.PRIORITY_LOW, 80),
        SoundEvent.CARD_COLLECT to SoundSpec(SoundEvent.CARD_COLLECT, "card_collect", HokmAudio.PRIORITY_NORMAL, 100),
        SoundEvent.TRUMP_REVEAL to SoundSpec(SoundEvent.TRUMP_REVEAL, "trump_reveal", HokmAudio.PRIORITY_HIGH, 200),
        SoundEvent.TRICK_WIN to SoundSpec(SoundEvent.TRICK_WIN, "trick_win", HokmAudio.PRIORITY_NORMAL, 150),
        SoundEvent.HAND_WIN to SoundSpec(SoundEvent.HAND_WIN, "hand_win", HokmAudio.PRIORITY_HIGH, 300),
        SoundEvent.MATCH_WIN to SoundSpec(SoundEvent.MATCH_WIN, "match_win", HokmAudio.PRIORITY_CRITICAL, 500),
        SoundEvent.MATCH_LOSS to SoundSpec(SoundEvent.MATCH_LOSS, "match_loss", HokmAudio.PRIORITY_HIGH, 500),
        SoundEvent.BUTTON_TAP to SoundSpec(SoundEvent.BUTTON_TAP, "button_tap", HokmAudio.PRIORITY_LOW, 50),
        SoundEvent.ERROR to SoundSpec(SoundEvent.ERROR, "error", HokmAudio.PRIORITY_NORMAL, 200),
        SoundEvent.NOTIFICATION to SoundSpec(SoundEvent.NOTIFICATION, "notification", HokmAudio.PRIORITY_NORMAL, 200),
        SoundEvent.SHUFFLE to SoundSpec(SoundEvent.SHUFFLE, "shuffle", HokmAudio.PRIORITY_NORMAL, 300),
        SoundEvent.STICKER to SoundSpec(SoundEvent.STICKER, "sticker", HokmAudio.PRIORITY_LOW, 100)
    )

    fun specOf(event: SoundEvent): SoundSpec = CATALOG[event] ?: CATALOG[SoundEvent.BUTTON_TAP]!!

    fun fileName(event: SoundEvent): String = specOf(event).fileName

    fun priority(event: SoundEvent): Int = specOf(event).priority

    fun cooldownMs(event: SoundEvent): Long = specOf(event).cooldownMs

    /** All events that should be preloaded at startup. */
    fun preloadSet(): Set<SoundEvent> = SoundEvent.values().toSet()

    /** Whether an event is "critical" (should not be dropped under load). */
    fun isCritical(event: SoundEvent): Boolean = specOf(event).priority >= HokmAudio.PRIORITY_HIGH
}
