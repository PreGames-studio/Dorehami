package ir.dorehami.games.hokm.ui

/**
 * HK-003/HK-004 (deal slice) — deal logic as **pure, unit-testable** logic.
 */
object HokmDeal {

    /** First player with an Ace becomes hakem. */
    fun findHakem(hands: List<List<String>>): Int {
        for (i in hands.indices) {
            if (hands[i].any { it.startsWith('A') }) return i
        }
        return 0
    }

    /** Deal 5 cards first, then trump selection, then rest. */
    fun firstDealCount(): Int = 5

    /** Total cards per player. */
    fun cardsPerPlayer(): Int = 13

    /** Whether all players have full hands. */
    fun isDealComplete(hands: List<List<String>>): Boolean =
        hands.all { it.size == cardsPerPlayer() }
}
