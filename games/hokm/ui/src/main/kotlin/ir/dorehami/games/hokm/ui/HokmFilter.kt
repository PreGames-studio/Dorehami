package ir.dorehami.games.hokm.ui

/**
 * HK-020 (filter slice) — filter helpers as **pure, unit-testable** logic.
 */
object HokmFilter {

    fun bySuit(cards: List<String>, suit: Char): List<String> = cards.filter { it.length >= 2 && it.last() == suit }

    fun byRank(cards: List<String>, rank: Char): List<String> = cards.filter { it.length >= 2 && it.first() == rank }

    fun nonTrump(cards: List<String>, trump: Char): List<String> = cards.filter { it.length < 2 || it.last() != trump }

    fun trumps(cards: List<String>, trump: Char): List<String> = cards.filter { it.length >= 2 && it.last() == trump }
}
