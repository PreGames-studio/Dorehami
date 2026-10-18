package ir.dorehami.games.hokm.ui

/**
 * GX-03 (pattern slice) — pattern helpers as **pure, unit-testable** logic.
 */
object HokmPattern {

    fun hasPattern(name: String): Boolean = name in listOf("girih", "boteh", "star", "lattice")

    fun patternForBack(back: HokmCardBack.Design): String = when (back) {
        HokmCardBack.Design.EMERALD_GIRIH -> "girih"
        HokmCardBack.Design.MIDNIGHT_BOTEH -> "boteh"
        HokmCardBack.Design.TURQUOISE_STAR -> "star"
        HokmCardBack.Design.CUSTOM_IMAGE -> "custom"
    }
}
