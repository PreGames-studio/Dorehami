package ir.dorehami.games.hokm.ui

/**
 * HK-005/HK-006 (trick slice) — trick resolution as **pure, unit-testable** logic.
 */
object HokmTrick {

    private val RANKS = "AKQJT98765432"

    fun rankValue(c: Char): Int = RANKS.indexOf(c).let { if (it < 0) 99 else it }

    /** Whether [card] is legal given [hand] and [leadSuit]. */
    fun isLegal(card: String, hand: List<String>, leadSuit: Char?): Boolean {
        if (card !in hand) return false
        if (leadSuit == null) return true
        val hasLead = hand.any { it.length >= 2 && it.last() == leadSuit }
        if (!hasLead) return true
        return card.length >= 2 && card.last() == leadSuit
    }

    /** Winner index among plays (list of (seat, card)) given trump and lead. */
    fun winner(plays: List<Pair<Int, String>>, trump: Char, lead: Char): Int {
        if (plays.isEmpty()) return -1
        var bestIdx = 0
        var bestCard = plays[0].second
        for (i in 1 until plays.size) {
            val c = plays[i].second
            if (beats(c, bestCard, trump, lead)) {
                bestCard = c
                bestIdx = i
            }
        }
        return plays[bestIdx].first
    }

    fun beats(card: String, best: String, trump: Char, lead: Char): Boolean {
        val cSuit = if (card.length >= 2) card.last() else ' '
        val bSuit = if (best.length >= 2) best.last() else ' '
        if (cSuit == trump && bSuit != trump) return true
        if (bSuit == trump && cSuit != trump) return false
        if (cSuit == bSuit) return rankValue(card.first()) < rankValue(best.first())
        return false
    }

    /** Lead suit of a trick (suit of first card). */
    fun leadSuit(plays: List<Pair<Int, String>>): Char? =
        plays.firstOrNull()?.second?.let { if (it.length >= 2) it.last() else null }
}
