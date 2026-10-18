package ir.dorehami.games.hokm.ui

/**
 * HK-003 (sort slice) — sort helpers as **pure, unit-testable** logic.
 */
object HokmSort {

    private val RANKS = "AKQJT98765432"

    fun byRank(cards: List<String>): List<String> = cards.sortedBy { RANKS.indexOf(it.firstOrNull() ?: ' ') }

    fun bySuit(cards: List<String>): List<String> = cards.sortedBy { it.lastOrNull() ?: ' ' }

    fun descending(cards: List<String>): List<String> = cards.sortedDescending()
}
