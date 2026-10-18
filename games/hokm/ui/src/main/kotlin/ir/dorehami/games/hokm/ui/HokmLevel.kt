package ir.dorehami.games.hokm.ui

/**
 * HK-044 (level slice) — level helpers as **pure, unit-testable** logic.
 */
object HokmLevel {

    fun levelForXp(xp: Int, xpPerLevel: Int = 100): Int = (xp / xpPerLevel) + 1

    fun xpForNextLevel(currentXp: Int, xpPerLevel: Int = 100): Int = xpPerLevel - (currentXp % xpPerLevel)

    fun title(level: Int): String = when {
        level < 5 -> "تازه‌کار"
        level < 10 -> "بازیکن"
        level < 20 -> "ماهر"
        else -> "استاد"
    }
}
