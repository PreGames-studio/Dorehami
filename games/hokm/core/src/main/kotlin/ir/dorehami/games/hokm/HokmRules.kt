package ir.dorehami.games.hokm

/** One card laid on the table during a trick. */
data class Play(val seat: Int, val card: Card)

/** How a hand (a "dast") was won — decides the points. */
enum class HandWinKind(val id: String, val titleFa: String) {
    /** Normal win: 7 tricks against at least one. */
    NORMAL("normal", "برد"),

    /** Kot: the opponents did not take a single trick. */
    KOT("kot", "کُت"),

    /** Hakem kot: the hakem's own team was kotted. */
    HAKEM_KOT("hakem_kot", "حاکم‌کُت"),
}

/**
 * Pure rules of Hokm — no state, no randomness, no I/O. Everything here is unit-testable on its own
 * and reused by the session, the bot and (for highlighting) the UI.
 */
object HokmRules {
    const val SEATS = 4
    const val TEAMS = 2
    const val TRICKS_TO_WIN_HAND = 7
    const val CARDS_PER_PLAYER = 13
    const val HAKEM_FIRST_DEAL = 5

    fun teamOf(seat: Int): Int = seat % TEAMS

    fun partnerOf(seat: Int): Int = (seat + 2) % SEATS

    fun nextSeat(seat: Int): Int = (seat + 1) % SEATS

    /** Cards [seat] is allowed to play: must follow the led suit when possible. */
    fun legalCards(hand: List<Card>, trick: List<Play>): List<Card> {
        if (trick.isEmpty()) return hand
        val lead = trick.first().card.suit
        val followers = hand.filter { it.suit == lead }
        return if (followers.isEmpty()) hand else followers
    }

    /** Returns `true` when [challenger] beats [current] given the led suit and the trump. */
    fun beats(challenger: Card, current: Card, leadSuit: Suit, hokm: Suit): Boolean {
        val cTrump = challenger.suit == hokm
        val wTrump = current.suit == hokm
        return when {
            cTrump && !wTrump -> true
            cTrump && wTrump -> challenger.rank.value > current.rank.value
            !cTrump && wTrump -> false
            else -> challenger.suit == leadSuit && current.suit == leadSuit &&
                challenger.rank.value > current.rank.value
        }
    }

    /** The play currently winning a (possibly incomplete) trick. */
    fun winningPlay(trick: List<Play>, hokm: Suit): Play {
        require(trick.isNotEmpty()) { "empty trick has no winner" }
        val lead = trick.first().card.suit
        var best = trick.first()
        for (i in 1 until trick.size) {
            val p = trick[i]
            if (beats(p.card, best.card, lead, hokm)) best = p
        }
        return best
    }

    /** Seat that takes a complete trick. */
    fun trickWinner(trick: List<Play>, hokm: Suit): Int {
        require(trick.size == SEATS) { "a trick has exactly $SEATS cards" }
        return winningPlay(trick, hokm).seat
    }

    /**
     * Points for a finished hand.
     *
     * @param winnerTricks tricks of the winning team (always [TRICKS_TO_WIN_HAND] when a hand ends normally)
     * @param loserTricks tricks of the losing team
     * @param hakemLost whether the hakem's team is the losing team
     */
    fun handResult(
        winnerTricks: Int,
        loserTricks: Int,
        hakemLost: Boolean,
        kotPoints: Int,
        hakemKotPoints: Int,
    ): Pair<HandWinKind, Int> {
        require(winnerTricks >= TRICKS_TO_WIN_HAND) { "hand is not over" }
        return when {
            loserTricks > 0 -> HandWinKind.NORMAL to 1
            hakemLost -> HandWinKind.HAKEM_KOT to hakemKotPoints
            else -> HandWinKind.KOT to kotPoints
        }
    }

    /** Who is hakem next hand: the same player if their team won, otherwise the next seat around. */
    fun nextHakem(currentHakem: Int, hakemTeamWon: Boolean): Int =
        if (hakemTeamWon) currentHakem else nextSeat(currentHakem)
}
