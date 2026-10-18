package ir.dorehami.games.hokm.ui

/**
 * GX-07 (theme slice) — theme model as **pure, unit-testable** logic.
 *
 * The roadmap asks for consistent screens + brand identity (dark mode). This object
 * defines the theme mode and a resolver. The Android layer applies.
 */
object HokmTheme {

    enum class Mode { LIGHT, DARK, SYSTEM }

    data class Theme(val mode: Mode = Mode.SYSTEM, val accentColor: String = "emerald")

    fun isDark(mode: Mode, systemDark: Boolean): Boolean = when (mode) {
        Mode.LIGHT -> false
        Mode.DARK -> true
        Mode.SYSTEM -> systemDark
    }

    fun resolve(mode: Mode, systemDark: Boolean): Mode =
        if (isDark(mode, systemDark)) Mode.DARK else Mode.LIGHT

    fun toggle(mode: Mode): Mode = when (mode) {
        Mode.LIGHT -> Mode.DARK
        Mode.DARK -> Mode.LIGHT
        Mode.SYSTEM -> Mode.DARK
    }
}
