package ir.dorehami.games.hokm.ui

/**
 * HK-027/HK-028 (hand strength slice) — hand strength as **pure, unit-testable** logic.
 */
object HokmHandStrength {

    private val RANKS = "AKQJT98765432"

    fun rankValue(c: Char): Int = RANKS.indexOf(c).let { if (it < 0) 99 else it }

    /** Count of trumps in hand. */
    fun trumpCount(hand: List<String>, trump: Char): Int = hand.count { it.length >= 2 && it.last() == trump }

    /** Count of aces in hand. */
    fun aceCount(hand: List<String>): Int = hand.count { it.startsWith('A') }

    /** Highest rank index (lowest = strongest). */
    fun strongestRank(hand: List<String>): Int = hand.minOfOrNull { rankValue(it.first()) } ?: 99

    /** Whether hand has a void in a suit. */
    fun hasVoid(hand: List<String>, suit: Char): Boolean = hand.none { it.length >= 2 && it.last() == suit }

    /** Number of void suits. */
    fun voidCount(hand: List<String>, allSuits: List<Char>): Int = allSuits.count { hasVoid(hand, it) }

    /** Simple strength score 0..100. */
    fun score(hand: List<String>, trump: Char): Int {
        var s = trumpCount(hand, trump) * 5
        s += aceCount(hand) * 8
        s += (13 - strongestRank(hand)) * 2
        return s.coerceIn(0, 100)
    }
}
