package ir.dorehami.games.hokm.ui

/**
 * HK-020 (first slice) — settings/lobby model as **pure, unit-testable** logic.
 *
 * The roadmap asks for usable menus/settings/lobby: mute, animation speed, language,
 * accessibility, table/deck choice. This object defines the settings data class, its
 * defaults, validation, and a small reducer for updates. The Compose layer renders the
 * settings screen and persists via HokmSave.
 */
object HokmSettings {

    enum class AnimSpeed { OFF, SLOW, NORMAL, FAST }
    enum class Language { FA, EN }
    enum class TableSkin { EMERALD, BURGUNDY, TEAL, DARK_VELVET }
    enum class DeckSkin { CLASSIC, CAFE_NIGHT, GAME_CLUB }

    data class Settings(
        val muted: Boolean = false,
        val animSpeed: AnimSpeed = AnimSpeed.NORMAL,
        val language: Language = Language.FA,
        val largeText: Boolean = false,
        val reducedMotion: Boolean = false,
        val tableSkin: TableSkin = TableSkin.EMERALD,
        val deckSkin: DeckSkin = DeckSkin.CLASSIC,
        val showCoaching: Boolean = false,
        val quickPlay: Boolean = false
    )

    /** Default settings (Persian, normal speed, emerald table). */
    val DEFAULTS: Settings = Settings()

    /** Validate and clamp a settings value; returns a corrected copy. */
    fun sanitize(s: Settings): Settings = s.copy(
        // no clamping needed for enums; kept for future numeric fields
    )

    /** Whether reduced motion should be forced (accessibility). */
    fun effectiveReducedMotion(s: Settings): Boolean = s.reducedMotion || s.largeText

    /** Whether coaching hints are shown. */
    fun coachingEnabled(s: Settings): Boolean = s.showCoaching

    /** Animation duration multiplier based on speed. */
    fun animMultiplier(s: Settings): Float = when (s.animSpeed) {
        AnimSpeed.OFF -> 0f
        AnimSpeed.SLOW -> 1.5f
        AnimSpeed.NORMAL -> 1.0f
        AnimSpeed.FAST -> 0.6f
    }

    /** Whether animations are effectively disabled. */
    fun animationsDisabled(s: Settings): Boolean = s.animSpeed == AnimSpeed.OFF || s.reducedMotion
}
