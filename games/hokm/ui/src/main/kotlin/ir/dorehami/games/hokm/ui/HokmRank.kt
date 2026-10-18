package ir.dorehami.games.hokm.ui

/**
 * HK-003 (rank slice) — rank helpers as **pure, unit-testable** logic.
 */
object HokmRank {

    val ALL = listOf('A', 'K', 'Q', 'J', 'T', '9', '8', '7', '6', '5', '4', '3', '2')

    fun labelFa(rank: Char): String = when (rank) {
        'A' -> "آس"
        'K' -> "شاه"
        'Q' -> "بی‌بی"
        'J' -> "سرباز"
        'T' -> "۱۰"
        else -> rank.toString()
    }

    fun value(rank: Char): Int = ALL.indexOf(rank).let { if (it < 0) 99 else it }

    fun isValid(rank: Char): Boolean = rank in ALL

    fun isFace(rank: Char): Boolean = rank in listOf('K', 'Q', 'J')

    fun isAce(rank: Char): Boolean = rank == 'A'
}
