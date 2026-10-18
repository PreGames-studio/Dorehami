package ir.dorehami.games.hokm.ui

/**
 * HK-028 (first slice) — explainable coaching hints as **pure, unit-testable** logic.
 *
 * The roadmap asks for smart, educational move suggestions (H-core/H-ui). This object is the
 * deterministic core: given the cards you may legally play plus a little context about the
 * trick, it returns a single suggested card and a Persian reason string. The Compose layer
 * later renders the hint and the player decides whether to follow it.
 *
 * Heuristics (kept simple and explainable, matching the bot's level):
 *  1. If you cannot win the trick and your partner is already winning, **dump the lowest**
 *     card of the lead suit (save trumps) — reason "هم‌تیمی در حال بردن است".
 *  2. If you can win, play the **lowest winning card** — reason "کمترین کارتِ برنده".
 *  3. If you cannot win and partner is not winning, **dump the lowest card** of the lead
 *     suit (or any off-suit if void) — reason "نمی‌توان برد؛ کمترین کارت".
 *  4. With a single legal card there is no choice — reason "تنها کارتِ مجاز".
 *
 * Card encoding: a 2-char-ish string "RANKSUIT" where rank in {A,K,Q,J,T,9..2} and suit in
 * {♠,♥,♦,♣}. Ranks are ordered high→low by [RANK_ORDER].
 */
object HokmCoaching {

    private val RANK_ORDER = listOf('A', 'K', 'Q', 'J', 'T', '9', '8', '7', '6', '5', '4', '3', '2')

    data class Hint(val card: String, val reason: String)

    /** Rank index (lower = stronger). */
    fun rankIndex(card: String): Int {
        val r = card.firstOrNull() ?: return RANK_ORDER.size
        val idx = RANK_ORDER.indexOf(r)
        return if (idx < 0) RANK_ORDER.size else idx
    }

    /** Suit of a card (last char). */
    fun suitOf(card: String): Char = card.lastOrNull() ?: ' '

    /** Whether [card] can beat [currentBest] given [trump] and [lead]. */
    fun beats(card: String, currentBest: String?, trump: Char, lead: Char): Boolean {
        if (currentBest == null) return true
        val cSuit = suitOf(card)
        val bSuit = suitOf(currentBest)
        // trump beats non-trump
        if (cSuit == trump && bSuit != trump) return true
        if (bSuit == trump && cSuit != trump) return false
        // same suit: lower rank index wins
        if (cSuit == bSuit) return rankIndex(card) < rankIndex(currentBest)
        // different non-trump suits: cannot beat
        return false
    }

    /**
     * Suggests a card from [legal] given context.
     * @param legal cards the player may play.
     * @param trump trump suit char.
     * @param lead lead suit char (of current trick).
     * @param currentBest best card played so far in this trick (null if leading).
     * @param partnerWinning whether the partner is currently winning the trick.
     */
    fun suggest(
        legal: List<String>,
        trump: Char,
        lead: Char,
        currentBest: String? = null,
        partnerWinning: Boolean = false
    ): Hint {
        if (legal.isEmpty()) return Hint("", "کارتِ مجازی وجود ندارد")
        if (legal.size == 1) return Hint(legal.first(), "تنها کارتِ مجاز")

        // leading: play lowest card (conservative) unless we want to win — keep simple.
        if (currentBest == null) {
            val lowest = legal.maxByOrNull { rankIndex(it) } ?: legal.first()
            // if we hold trump and no lead constraint, prefer a low non-trump? keep lowest overall.
            return Hint(lowest, "شروعِ دست: کمترین کارت")
        }

        val winning = legal.filter { beats(it, currentBest, trump, lead) }
        return when {
            partnerWinning -> {
                // dump lowest of lead suit if possible, else lowest overall
                val leadSuitCards = legal.filter { suitOf(it) == lead }
                val pool = if (leadSuitCards.isNotEmpty()) leadSuitCards else legal
                val dump = pool.maxByOrNull { rankIndex(it) } ?: legal.last() // highest rankIndex = weakest
                Hint(dump, "هم‌تیمی در حال بردن است؛ کمترین کارت را بینداز")
            }
            winning.isNotEmpty() -> {
                val lowestWin = winning.maxByOrNull { rankIndex(it) } ?: winning.first()
                Hint(lowestWin, "کمترین کارتِ برنده")
            }
            else -> {
                val leadSuitCards = legal.filter { suitOf(it) == lead }
                val pool = if (leadSuitCards.isNotEmpty()) leadSuitCards else legal
                val dump = pool.maxByOrNull { rankIndex(it) } ?: pool.last()
                Hint(dump, "نمی‌توان برد؛ کمترین کارت را بینداز")
            }
        }
    }
}
