package ir.dorehami.games.hokm.ui

/**
 * HK-044 (title slice) — title helpers as **pure, unit-testable** logic.
 */
object HokmTitle {

    fun forLevel(level: Int): String = HokmLevel.title(level)

    fun forWinRate(rate: Float): String = when {
        rate >= 0.7f -> "قهرمان"
        rate >= 0.5f -> "بازیکن خوب"
        else -> "تازه‌کار"
    }
}
