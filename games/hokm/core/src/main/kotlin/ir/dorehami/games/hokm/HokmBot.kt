package ir.dorehami.games.hokm

/**
 * Everything the bot is allowed to know — exactly what a human in that seat sees. The session builds
 * this from the player's own hand plus public table state, so the bot can never peek.
 */
data class BotView(
    val seat: Int,
    val players: Int,
    val hand: List<Card>,
    val hokm: Suit?,
    val trick: List<Play>,
    /** All cards already played in this hand (previous tricks + current trick). */
    val played: Set<Card>,
    val tricksWon: List<Int>,
    val hakem: Int,
)

/**
 * Deterministic bot policy (contract question 6). No randomness at all: the same view always yields
 * the same card, so a bot game replays bit-for-bit.
 *
 * The heuristics are those of a sensible club player: pick the longest suit as hokm, cash sure
 * winners, do not over-trump a winning partner, win tricks as cheaply as possible, discard the
 * least valuable card when you cannot win.
 */
object HokmBot {

    /** Choose hokm from the first five cards: longest suit, then strongest, then suit order. */
    fun chooseHokm(firstFive: List<Card>): Suit {
        require(firstFive.isNotEmpty())
        return Suit.entries
            .map { s -> Triple(s, firstFive.count { it.suit == s }, firstFive.filter { it.suit == s }.sumOf { it.rank.value }) }
            .sortedWith(compareByDescending<Triple<Suit, Int, Int>> { it.second }.thenByDescending { it.third }.thenBy { it.first.ordinal })
            .first().first
    }

    /** Two-player burn: throw away the weakest off-trump cards (short suits first). */
    fun discard(view: BotView, count: Int): List<Card> {
        val hokm = requireNotNull(view.hokm)
        return view.hand
            .sortedWith(
                compareBy<Card> { it.suit == hokm }
                    .thenBy { it.rank.value >= Rank.JACK.value }
                    .thenBy { view.hand.count { c -> c.suit == it.suit } }
                    .thenBy { it.rank.value },
            )
            .take(count)
    }

    /**
     * Two-player draw: keep the shown card when it strengthens the hand — any trump, any honour,
     * or a card in a suit we are already long in; otherwise gamble on the blind card.
     */
    fun keepRevealed(view: BotView, shown: Card): Boolean {
        val hokm = requireNotNull(view.hokm)
        if (shown.suit == hokm) return true
        if (shown.rank.value >= Rank.QUEEN.value) return true
        val inSuit = view.hand.count { it.suit == shown.suit }
        if (shown.rank == Rank.JACK && inSuit >= 1) return true
        return inSuit >= 4 && shown.rank.value >= Rank.NINE.value
    }

    /**
     * Beginner-friendly bot: follows the rules but plays naively — lowest legal card unless it is
     * already winning cheaply. Deterministic like the pro bot (no RNG).
     */
    fun playCardEasy(view: BotView): Card {
        val legal = HokmRules.legalCards(view.hand, view.trick)
        require(legal.isNotEmpty()) { "bot has no cards" }
        val hokm = requireNotNull(view.hokm)
        if (view.trick.isEmpty()) return legal.maxByOrNull { if (it.suit == hokm) it.rank.value - 20 else it.rank.value } ?: legal.first()
        // Follow: take the trick with the cheapest winner if one exists, otherwise dump the lowest.
        val winners = legal.filter { c -> HokmRules.trickWinner(view.trick + Play(view.seat, c), hokm, view.players) == view.seat }
        return winners.minByOrNull { if (it.suit == hokm) it.rank.value + 20 else it.rank.value }
            ?: legal.minByOrNull { if (it.suit == hokm) it.rank.value + 20 else it.rank.value }
            ?: legal.first()
    }

    fun playCard(view: BotView): Card {
        val hokm = requireNotNull(view.hokm) { "hokm must be chosen before playing" }
        val legal = HokmRules.legalCards(view.hand, view.trick)
        require(legal.isNotEmpty()) { "bot has no cards" }
        if (legal.size == 1) return legal.first()

        return if (view.trick.isEmpty()) lead(view, legal, hokm) else follow(view, legal, hokm)
    }

    // ---------------------------------------------------------------- leading

    private fun lead(view: BotView, legal: List<Card>, hokm: Suit): Card {
        // 1) Cash a sure winner outside trump (an Ace-equivalent of a suit).
        val sure = legal.filter { it.suit != hokm && isHighestRemaining(it, view) }
        if (sure.isNotEmpty()) {
            // Prefer the suit where we hold the most cards (opponents less likely to be void).
            return sure.maxWithOrNull(compareBy<Card> { suitCount(view.hand, it.suit) }.thenBy { it.rank.value })!!
        }

        // 2) Draw trumps when we hold the top trump and more than half of the remaining trumps.
        val trumps = legal.filter { it.suit == hokm }
        val outstandingTrumps = Rank.entries.size - view.played.count { it.suit == hokm } - trumps.size
        if (trumps.isNotEmpty() && outstandingTrumps > 0) {
            val top = trumps.maxBy { it.rank.value }
            if (isHighestRemaining(top, view) && trumps.size > outstandingTrumps) return top
        }

        // 3) Otherwise lead the lowest card of our longest non-trump suit (keep trumps for later).
        val nonTrump = legal.filter { it.suit != hokm }
        val pool = nonTrump.ifEmpty { legal }
        val longest = pool.groupBy { it.suit }.maxWith(compareBy<Map.Entry<Suit, List<Card>>> { it.value.size }.thenBy { it.key.ordinal })
        return longest.value.minBy { it.rank.value }
    }

    // --------------------------------------------------------------- following

    private fun follow(view: BotView, legal: List<Card>, hokm: Suit): Card {
        val trick = view.trick
        val lead = trick.first().card.suit
        val winning = HokmRules.winningPlay(trick, hokm)
        val partner = HokmRules.partnerOf(view.seat, view.players)
        val partnerWinning = partner != null && winning.seat == partner
        val lastToPlay = trick.size == view.players - 1
        val canFollow = legal.first().suit == lead && view.hand.any { it.suit == lead }

        val winners = legal.filter { HokmRules.beats(it, winning.card, lead, hokm) }

        if (partnerWinning) {
            val partnerSafe = isHighestRemaining(winning.card, view) || winning.card.suit == hokm && lastToPlay
            if (partnerSafe || lastToPlay) return cheapest(legal, hokm)
            // Partner leads but may be overtaken: take over only if we can do it with a sure card.
            val sureWin = winners.filter { isHighestRemaining(it, view) && it.suit != hokm }
            if (sureWin.isNotEmpty()) return sureWin.minBy { it.rank.value }
            return cheapest(legal, hokm)
        }

        if (winners.isNotEmpty()) {
            return if (lastToPlay) {
                winners.minWith(compareBy<Card> { it.suit == hokm }.thenBy { it.rank.value })
            } else if (canFollow) {
                // Others still to play: play our highest follower if it is the top remaining card,
                // else the cheapest card that currently wins.
                val top = winners.maxBy { it.rank.value }
                if (isHighestRemaining(top, view)) top else winners.minBy { it.rank.value }
            } else {
                // Trumping in: use the lowest trump that wins.
                winners.minBy { it.rank.value }
            }
        }

        return cheapest(legal, hokm)
    }

    // ---------------------------------------------------------------- helpers

    /** Lowest-value card, preferring to keep trumps. */
    private fun cheapest(cards: List<Card>, hokm: Suit): Card =
        cards.minWith(compareBy<Card> { it.suit == hokm }.thenBy { it.rank.value })

    private fun suitCount(hand: List<Card>, suit: Suit): Int = hand.count { it.suit == suit }

    /** True when every higher card of that suit has already been played or is in our own hand. */
    private fun isHighestRemaining(card: Card, view: BotView): Boolean {
        for (r in Rank.entries) {
            if (r.value <= card.rank.value) continue
            val higher = Card(r, card.suit)
            if (higher !in view.played && higher !in view.hand) return false
        }
        return true
    }
}
