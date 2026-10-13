package ir.dorehami.games.hokm

/** One card laid on the table during a trick. */
data class Play(val seat: Int, val card: Card)

/** How a hand (a "dast") was won — decides the points. */
enum class HandWinKind(val id: String, val titleFa: String) {
    /** Normal win: the winner took at least one losing side with them scoring. */
    NORMAL("normal", "برد"),

    /** Kot: no losing side took a single trick (7-0 in 2/4 players, 7-0-0 in 3 players). */
    KOT("kot", "کُت"),

    /** Hakem kot: the hakem's own side took zero tricks and lost (beats kot when both apply). */
    HAKEM_KOT("hakem_kot", "حاکم‌کُت"),

    /** Three-player only: all 17 tricks played, the two leaders tied 6-6-5 — nobody scores. */
    VOID("void", "بی‌نتیجه"),
}

/** Settled outcome of a finished hand: who won (if anybody), how, and how many points. */
data class HandScore(val winnerTeam: Int?, val kind: HandWinKind, val points: Int)

/**
 * Pure rules of Hokm — no state, no randomness, no I/O. Shared by the session, the bot and the UI.
 *
 * Terminology (HK-008): a **trick** (ترفند) is one round of one card each; a **hand** (دست) is the
 * stretch of tricks up to one side reaching 7 tricks (or, in 3 players, up to the deck running out);
 * a **match** (مسابقه) is the whole game up to the target score.
 *
 * Variants (all first-to-7-tricks, first-to-N-points):
 * * **4 players** — two fixed teams (0+2 vs 1+3), 13 cards each.
 * * **3 players** — everyone for themselves, one deuce removed (51 cards), 17 cards each.
 *   When all 17 tricks are played nobody can have more than 6, so the split is always 6-6-5 and
 *   the hand is void: nobody scores and the hakem passes to the next seat.
 * * **2 players** — 5 cards each; hakem burns 3 and the opponent 2; then they alternately draw
 *   from the 42-card stock ("keep the card you see and burn the next blind, or burn it and take
 *   the next blind") until both hold 13.
 */
object HokmRules {
    const val TRICKS_TO_WIN_HAND = 7
    const val HAKEM_FIRST_DEAL = 5
    const val TWO_PLAYER_HAKEM_BURN = 3
    const val TWO_PLAYER_OPPONENT_BURN = 2
    val SUPPORTED_PLAYERS = listOf(2, 3, 4)

    /** The deuce removed in the three-player game so 51 cards split evenly. */
    val THREE_PLAYER_REMOVED = Card(Rank.TWO, Suit.CLUBS)

    fun teamCount(players: Int): Int = if (players == 4) 2 else players

    fun teamOf(seat: Int, players: Int): Int = if (players == 4) seat % 2 else seat

    /** Partner seat in the four-player game; `null` when everybody plays alone. */
    fun partnerOf(seat: Int, players: Int): Int? = if (players == 4) (seat + 2) % 4 else null

    fun nextSeat(seat: Int, players: Int): Int = (seat + 1) % players

    fun cardsPerPlayer(players: Int): Int = when (players) {
        3 -> 17
        else -> 13
    }

    fun deckFor(players: Int): List<Card> =
        if (players == 3) Card.fullDeck().filter { it != THREE_PLAYER_REMOVED } else Card.fullDeck()

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
    fun trickWinner(trick: List<Play>, hokm: Suit, players: Int): Int {
        require(trick.size == players) { "a trick has exactly $players cards" }
        return winningPlay(trick, hokm).seat
    }

    /**
     * Points for a finished hand. The approved contract (HK-008):
     *
     * * **Kot** — *no* losing side took a single trick (7-0 in 2/4 players; 7-0-0 in 3 players).
     *   One scoring loser is enough to make the hand normal: 7-0-3 in 3 players is **not** a kot.
     * * **Hakem kot** — the hakem (their team in 4 players) took zero tricks and lost, whatever
     *   the other sides scored. When both kot and hakem-kot apply, hakem-kot wins.
     * * Anything else is a normal win, worth 1 point.
     *
     * @param winnerTricks tricks of the winning side
     * @param loserTricks tricks of each losing side (all-zero ⇒ kot)
     * @param hakemTricks tricks of the hakem's side
     * @param hakemLost whether the hakem's side lost
     */
    fun handResult(
        winnerTricks: Int,
        loserTricks: List<Int>,
        hakemTricks: Int,
        hakemLost: Boolean,
        kotPoints: Int,
        hakemKotPoints: Int,
    ): Pair<HandWinKind, Int> {
        require(winnerTricks > 0) { "hand is not over" }
        require(loserTricks.isNotEmpty()) { "there must be a losing side" }
        return when {
            hakemLost && hakemTricks == 0 -> HandWinKind.HAKEM_KOT to hakemKotPoints
            loserTricks.all { it == 0 } -> HandWinKind.KOT to kotPoints
            else -> HandWinKind.NORMAL to 1
        }
    }

    /**
     * Settles a finished hand from final trick counts.
     *
     * @param reachedSeven the side that reached 7 tricks while playing, or `null` when all cards
     *   were played instead (3-player exhaustion)
     */
    fun settleHand(
        tricks: List<Int>,
        reachedSeven: Int?,
        hakem: Int,
        players: Int,
        kotPoints: Int,
        hakemKotPoints: Int,
    ): HandScore {
        require(tricks.size == teamCount(players)) { "one trick count per side" }
        val winner: Int? = reachedSeven ?: run {
            val best = tricks.max()
            val leaders = tricks.indices.filter { tricks[it] == best }
            if (leaders.size == 1) leaders.first() else null
        }
        if (winner == null) return HandScore(null, HandWinKind.VOID, 0)
        val hakemTeam = teamOf(hakem, players)
        val losers = tricks.indices.filter { it != winner }
        val (kind, points) = handResult(
            winnerTricks = tricks[winner],
            loserTricks = losers.map { tricks[it] },
            hakemTricks = tricks[hakemTeam],
            hakemLost = hakemTeam != winner,
            kotPoints = kotPoints,
            hakemKotPoints = hakemKotPoints,
        )
        return HandScore(winner, kind, points)
    }

    /** Who is hakem next hand: the same player if their side won, otherwise the next seat around. */
    fun nextHakem(currentHakem: Int, hakemTeamWon: Boolean, players: Int): Int =
        if (hakemTeamWon) currentHakem else nextSeat(currentHakem, players)
}
