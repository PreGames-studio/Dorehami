package ir.dorehami.games.hokm.ui

/**
 * HK-003 (hand slice) — hand helpers as **pure, unit-testable** logic.
 */
object HokmHand {

    fun size(hand: List<String>): Int = hand.size

    fun isEmpty(hand: List<String>): Boolean = hand.isEmpty()

    fun contains(hand: List<String>, card: String): Boolean = card in hand

    fun remove(hand: List<String>, card: String): List<String> = hand.filter { it != card }

    fun add(hand: List<String>, card: String): List<String> = hand + card

    fun suits(hand: List<String>): Set<Char> = hand.map { if (it.length >= 2) it.last() else ' ' }.toSet()

    fun hasSuit(hand: List<String>, suit: Char): Boolean = hand.any { it.length >= 2 && it.last() == suit }
}
