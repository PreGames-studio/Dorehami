package ir.dorehami.games.hokm

/** One card laid on the table during a trick. */
data class Play(val seat: Int, val card: Card)

/** How a hand (a "dast") was won — decides the points. */
enum class HandWinKind(val id: String, val titleFa: String) {
    /** Normal win: 7 tricks against at least one. */
    NORMAL("normal", "برد"),

    /** Kot: the losing side did not take a single trick. */
    KOT("kot", "کُت"),

    /** Hakem kot: the hakem's own side was kotted. */
    HAKEM_KOT("hakem_kot", "حاکم‌کُت"),

    /** Three-player only: all cards played, top scorers tied — nobody gets a point. */
    VOID("void", "بی‌نتیجه"),
}

/**
 * Pure rules of Hokm — no state, no randomness, no I/O. Shared by the session, the bot and the UI.
 *
 * Variants (all first-to-7-tricks, first-to-N-points):
 * * **4 players** — two fixed teams (0+2 vs 1+3), 13 cards each.
 * * **3 players** — everyone for themselves, one deuce removed (51 cards), 17 cards each.
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
     * Points for a finished hand.
     *
     * @param winnerTricks tricks of the winning side
     * @param minLoserTricks fewest tricks among the losing sides (0 ⇒ somebody was kotted)
     * @param hakemTricks tricks of the hakem's side
     * @param hakemLost whether the hakem's side lost
     */
    fun handResult(
        winnerTricks: Int,
        minLoserTricks: Int,
        hakemTricks: Int,
        hakemLost: Boolean,
        kotPoints: Int,
        hakemKotPoints: Int,
    ): Pair<HandWinKind, Int> {
        require(winnerTricks > 0) { "hand is not over" }
        return when {
            hakemLost && hakemTricks == 0 -> HandWinKind.HAKEM_KOT to hakemKotPoints
            minLoserTricks == 0 -> HandWinKind.KOT to kotPoints
            else -> HandWinKind.NORMAL to 1
        }
    }

    /**
     * Winner when all cards are played before anybody reaches [TRICKS_TO_WIN_HAND].
     * `null` means the top scorers are tied and the hand is void (HK-008 contract, three players).
     */
    fun winnerWhenCardsExhausted(tricks: List<Int>): Int? {
        require(tricks.isNotEmpty()) { "empty trick table" }
        val max = tricks.max()
        return tricks.indices.filter { tricks[it] == max }.singleOrNull()
    }

    /** Outcome of one finished hand, expressed without any session state. */
    data class HandOutcome(val winnerTeam: Int?, val kind: HandWinKind, val points: Int)

    /**
     * The complete, pure scoring decision for a finished hand — the approved HK-008 contract v1.0.
     *
     * [reachedSeven] is the side that got 7 tricks, or `null` when the cards ran out (three players).
     * In three-player hands, kot means **any** losing side took zero tricks (7-5-0 is a kot); a tie
     * at exhaustion is [HandWinKind.VOID] with no points for anybody and the hakem seat rotates.
     */
    fun settleHand(
        tricks: List<Int>,
        hakemTeam: Int,
        reachedSeven: Int?,
        kotPoints: Int,
        hakemKotPoints: Int,
    ): HandOutcome {
        require(tricks.size >= 2) { "at least two sides are needed" }
        val winnerTeam = reachedSeven ?: winnerWhenCardsExhausted(tricks)
            ?: return HandOutcome(null, HandWinKind.VOID, 0)
        val losers = tricks.indices.filter { it != winnerTeam }
        val (kind, points) = handResult(
            winnerTricks = tricks[winnerTeam],
            minLoserTricks = losers.minOf { tricks[it] },
            hakemTricks = tricks[hakemTeam],
            hakemLost = hakemTeam != winnerTeam,
            kotPoints = kotPoints,
            hakemKotPoints = hakemKotPoints,
        )
        return HandOutcome(winnerTeam, kind, points)
    }

    /** Who is hakem next hand: the same player if their side won, otherwise the next seat around. */
    fun nextHakem(currentHakem: Int, hakemTeamWon: Boolean, players: Int): Int =
        if (hakemTeamWon) currentHakem else nextSeat(currentHakem, players)
}
