package ir.dorehami.games.hokm.ui

/**
 * HK-007/HK-008 (scoring slice) — scoring as **pure, unit-testable** logic.
 */
object HokmScoring {

    const val TARGET_POINTS: Int = 7

    /** Kot: one team wins all 7 tricks in a hand (7-0). */
    fun isKot(ourTricks: Int, theirTricks: Int): Boolean = ourTricks == 7 && theirTricks == 0

    /** Hakem-Koti: hakem's team wins all 7. */
    fun isHakemKoti(ourTricks: Int, theirTricks: Int, hakemIsOurs: Boolean): Boolean =
        isKot(ourTricks, theirTricks) && hakemIsOurs

    /** Points awarded for a hand. */
    fun handPoints(ourTricks: Int, theirTricks: Int, hakemIsOurs: Boolean): Pair<Int, Int> {
        return if (ourTricks > theirTricks) {
            val pts = if (isKot(ourTricks, theirTricks)) 2 else 1
            if (isHakemKoti(ourTricks, theirTricks, hakemIsOurs)) (pts + 1) to 0 else pts to 0
        } else if (theirTricks > ourTricks) {
            val pts = if (isKot(theirTricks, ourTricks)) 2 else 1
            0 to pts
        } else {
            0 to 0
        }
    }

    /** Whether match is over. */
    fun matchOver(ourScore: Int, theirScore: Int): Boolean =
        ourScore >= TARGET_POINTS || theirScore >= TARGET_POINTS

    /** Winner: 0 = us, 1 = them, -1 = none. */
    fun winner(ourScore: Int, theirScore: Int): Int = when {
        ourScore >= TARGET_POINTS -> 0
        theirScore >= TARGET_POINTS -> 1
        else -> -1
    }
}
