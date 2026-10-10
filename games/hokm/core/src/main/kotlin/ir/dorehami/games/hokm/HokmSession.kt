package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Standing
import ir.dorehami.gamesdk.shuffle
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Sub-phase of a hand, exposed as `handPhase` in the views. */
enum class HandPhase(val id: String) {
    CHOOSING_HOKM("choosing_hokm"),
    PLAYING("playing"),
}

/**
 * The rules of Hokm, Iranian style, four players in two fixed teams (seats 0+2 vs 1+3).
 *
 * * First hakem: cards are dealt face up one at a time from seat 0 until an Ace shows; that seat is hakem.
 * * Deal: hakem gets 5 cards and names hokm (trump); then everybody is filled to 13.
 * * Hakem leads. Players must follow suit if they can; otherwise any card (trumping is allowed).
 * * Highest trump wins the trick, else highest card of the led suit. The winner leads the next trick.
 * * The first team to 7 tricks wins the hand: 1 point; 7–0 ("kot") = [Options.KOT_POINTS];
 *   kot against the hakem's own team ("hakem kot") = [Options.HAKEM_KOT_POINTS].
 * * Hakem stays while their team keeps winning; otherwise hakem moves to the next seat.
 * * First team to [Options.TARGET_POINTS] wins the match.
 *
 * Pure function of `(seed, actions)`: the only randomness is the injected [GameConfig.rng].
 */
class HokmSession(private val config: GameConfig) : GameSession {

    object Options {
        const val TARGET_POINTS = "targetPoints"
        const val KOT_POINTS = "kotPoints"
        const val HAKEM_KOT_POINTS = "hakemKotPoints"
    }

    object Keys {
        const val HAND = "hand"
        const val LEGAL_CARDS = "legalCards"
        const val SEAT = "seat"
    }

    private val players: List<PlayerId> = config.players
    private val targetPoints = config.options.int(Options.TARGET_POINTS, HokmModule.DEFAULT_TARGET_POINTS)
    private val kotPoints = config.options.int(Options.KOT_POINTS, HokmModule.DEFAULT_KOT_POINTS)
    private val hakemKotPoints = config.options.int(Options.HAKEM_KOT_POINTS, HokmModule.DEFAULT_HAKEM_KOT_POINTS)

    override var phase: Phase = Phase.PLAYING
        private set
    override var version: Long = 0L
        private set

    // ---- match state
    private val scores = IntArray(HokmRules.TEAMS)
    private var handNumber = 0
    private var hakem = 0
    private val hakemDraw = ArrayList<Play>()
    private var lastHandResult: HandResult? = null

    // ---- hand state
    private var handPhase = HandPhase.CHOOSING_HOKM
    private var hokm: Suit? = null
    private val hands: Array<MutableList<Card>> = Array(HokmRules.SEATS) { ArrayList(HokmRules.CARDS_PER_PLAYER) }
    private var pending: List<Card> = emptyList()
    private var turn = 0
    private var trickNumber = 0
    private val currentTrick = ArrayList<Play>(HokmRules.SEATS)
    private var lastTrick: CompletedTrick? = null
    private val tricksWon = IntArray(HokmRules.TEAMS)
    private val played = LinkedHashSet<Card>()

    data class CompletedTrick(val number: Int, val winner: Int, val plays: List<Play>)

    data class HandResult(
        val handNumber: Int,
        val winnerTeam: Int,
        val points: Int,
        val kind: HandWinKind,
        val hakem: Int,
        val tricks: List<Int>,
    )

    init {
        require(players.size == HokmRules.SEATS) { "Hokm needs exactly ${HokmRules.SEATS} players" }
        drawFirstHakem()
        startHand()
    }

    // ------------------------------------------------------------------ setup

    /** Classic opening: one card each, face up, from seat 0 around the table until an Ace appears. */
    private fun drawFirstHakem() {
        val deck = config.rng.shuffle(Card.fullDeck())
        var seat = 0
        for (card in deck) {
            hakemDraw += Play(seat, card)
            if (card.rank == Rank.ACE) {
                hakem = seat
                return
            }
            seat = HokmRules.nextSeat(seat)
        }
    }

    private fun startHand() {
        handNumber++
        handPhase = HandPhase.CHOOSING_HOKM
        hokm = null
        hands.forEach { it.clear() }
        currentTrick.clear()
        lastTrick = null
        played.clear()
        tricksWon.fill(0)
        trickNumber = 0
        turn = hakem

        val deck = config.rng.shuffle(Card.fullDeck())
        hands[hakem] += deck.take(HokmRules.HAKEM_FIRST_DEAL)
        hands[hakem].sort()
        pending = deck.drop(HokmRules.HAKEM_FIRST_DEAL)
    }

    /** After hokm is named: the other three get 5, then two rounds of 4 to everybody. */
    private fun dealRest() {
        val deck = ArrayDeque(pending)
        var seat = HokmRules.nextSeat(hakem)
        repeat(HokmRules.SEATS - 1) {
            repeat(HokmRules.HAKEM_FIRST_DEAL) { hands[seat] += deck.removeFirst() }
            seat = HokmRules.nextSeat(seat)
        }
        repeat(2) {
            var s = HokmRules.nextSeat(hakem)
            repeat(HokmRules.SEATS) {
                repeat(4) { hands[s] += deck.removeFirst() }
                s = HokmRules.nextSeat(s)
            }
        }
        check(deck.isEmpty())
        pending = emptyList()
        hands.forEach { it.sort() }
    }

    // ------------------------------------------------------------------ apply

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        if (phase == Phase.FINISHED) return reject(ErrCodes.BAD_STATE, "بازی تمام شده است")
        val seat = config.seatOf(player)
        if (seat < 0) return reject(ErrCodes.UNKNOWN_PLAYER, "این بازیکن سر میز نیست")

        return when (action.type) {
            HokmModule.ACTION_CHOOSE_HOKM -> chooseHokm(seat, action.payload)
            HokmModule.ACTION_PLAY_CARD -> playCard(seat, action.payload)
            else -> reject(ErrCodes.ILLEGAL_ACTION, "حرکت «${action.type}» در حکم وجود ندارد")
        }
    }

    private fun chooseHokm(seat: Int, payload: JsonObject): ApplyResult {
        if (handPhase != HandPhase.CHOOSING_HOKM) return reject(ErrCodes.BAD_STATE, "حکم این دست قبلاً انتخاب شده")
        if (seat != hakem) return reject(ErrCodes.NOT_YOUR_TURN, "فقط حاکم حکم را انتخاب می‌کند")
        val suitText = payload[HokmModule.PAYLOAD_SUIT]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?: return reject(ErrCodes.BAD_PAYLOAD, "خال حکم مشخص نشده")
        val suit = Suit.parse(suitText) ?: return reject(ErrCodes.BAD_PAYLOAD, "خال «$suitText» معتبر نیست")

        hokm = suit
        dealRest()
        handPhase = HandPhase.PLAYING
        turn = hakem
        trickNumber = 1
        version++

        val emissions = ArrayList<Emission>()
        emissions += Emission.Public(
            "hokm_chosen",
            view { put("hakem", hakem); put("hokm", suit.id); put("handNumber", handNumber) },
        )
        emissions += Emission.Feed("hokm_chosen", view { put("seat", hakem); put("suit", suit.id) })
        emissions += Emission.Sound("hokm_chosen")
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    private fun playCard(seat: Int, payload: JsonObject): ApplyResult {
        if (handPhase != HandPhase.PLAYING) return reject(ErrCodes.BAD_STATE, "اول باید حاکم حکم را انتخاب کند")
        if (seat != turn) return reject(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        val cardText = payload[HokmModule.PAYLOAD_CARD]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت مشخص نشده")
        val card = Card.parse(cardText) ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت «$cardText» معتبر نیست")
        val hand = hands[seat]
        if (card !in hand) return reject(ErrCodes.ILLEGAL_ACTION, "این کارت در دست شما نیست")
        if (card !in HokmRules.legalCards(hand, currentTrick)) {
            val lead = currentTrick.first().card.suit
            return reject(ErrCodes.ILLEGAL_ACTION, "باید ${lead.titleFa} بازی کنید")
        }

        val trump = requireNotNull(hokm)
        hand.remove(card)
        played += card
        currentTrick += Play(seat, card)
        version++

        val emissions = ArrayList<Emission>()
        emissions += Emission.Public(
            "card_played",
            view { put("seat", seat); put("card", card.code); put("trickNumber", trickNumber) },
        )
        emissions += Emission.Sound("card_played")

        if (currentTrick.size < HokmRules.SEATS) {
            turn = HokmRules.nextSeat(seat)
            emissions += Emission.Private(players[seat], privateView(players[seat]))
            return ApplyResult.Ok(emissions)
        }

        // ---- trick complete
        val winner = HokmRules.trickWinner(currentTrick, trump)
        val winnerTeam = HokmRules.teamOf(winner)
        tricksWon[winnerTeam]++
        lastTrick = CompletedTrick(trickNumber, winner, currentTrick.toList())
        currentTrick.clear()
        turn = winner
        emissions += Emission.Public(
            "trick_won",
            view {
                put("winner", winner)
                put("team", winnerTeam)
                put("trickNumber", trickNumber)
                put("tricksWon", intArray(tricksWon))
            },
        )
        emissions += Emission.Feed("trick_won", view { put("seat", winner); put("trickNumber", trickNumber) })
        emissions += Emission.Sound("trick_won")

        if (tricksWon[winnerTeam] < HokmRules.TRICKS_TO_WIN_HAND) {
            trickNumber++
            emissions += Emission.Private(players[seat], privateView(players[seat]))
            return ApplyResult.Ok(emissions)
        }

        // ---- hand complete
        val loserTeam = 1 - winnerTeam
        val hakemLost = HokmRules.teamOf(hakem) == loserTeam
        val (kind, points) = HokmRules.handResult(
            winnerTricks = tricksWon[winnerTeam],
            loserTricks = tricksWon[loserTeam],
            hakemLost = hakemLost,
            kotPoints = kotPoints,
            hakemKotPoints = hakemKotPoints,
        )
        scores[winnerTeam] += points
        val result = HandResult(handNumber, winnerTeam, points, kind, hakem, tricksWon.toList())
        lastHandResult = result
        emissions += Emission.Public("hand_finished", handResultJson(result))
        emissions += Emission.Feed(
            "hand_finished",
            view { put("team", winnerTeam); put("points", points); put("kind", kind.id) },
            kind = if (kind == HandWinKind.NORMAL) "info" else "highlight",
        )
        emissions += Emission.Sound(
            when (kind) {
                HandWinKind.NORMAL -> "hand_won"
                HandWinKind.KOT -> "kot"
                HandWinKind.HAKEM_KOT -> "hakem_kot"
            },
        )

        if (scores[winnerTeam] >= targetPoints) {
            phase = Phase.FINISHED
            emissions += Emission.Public(
                "game_finished",
                view { put("winnerTeam", winnerTeam); put("scores", intArray(scores)) },
            )
            emissions += Emission.Feed("game_finished", view { put("team", winnerTeam) }, kind = "highlight")
            emissions += Emission.Sound("game_won")
            return ApplyResult.Ok(emissions)
        }

        hakem = HokmRules.nextHakem(hakem, hakemTeamWon = !hakemLost)
        startHand()
        emissions += Emission.Public(
            "hand_started",
            view { put("handNumber", handNumber); put("hakem", hakem) },
        )
        emissions += Emission.Feed("hand_started", view { put("handNumber", handNumber); put("hakem", hakem) })
        emissions += Emission.Sound("shuffle")
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    private fun reject(code: String, messageFa: String) = ApplyResult.Rejected(code, messageFa)

    private fun privateEmissions(): List<Emission> = players.map { Emission.Private(it, privateView(it)) }

    // ------------------------------------------------------------------ views

    override fun legalActions(player: PlayerId): List<String> {
        if (phase == Phase.FINISHED) return emptyList()
        val seat = config.seatOf(player)
        if (seat < 0) return emptyList()
        return when (handPhase) {
            HandPhase.CHOOSING_HOKM -> if (seat == hakem) listOf(HokmModule.ACTION_CHOOSE_HOKM) else emptyList()
            HandPhase.PLAYING -> if (seat == turn) listOf(HokmModule.ACTION_PLAY_CARD) else emptyList()
        }
    }

    override fun publicView(): JsonObject = buildJsonObject { publicFields() }

    override fun privateView(player: PlayerId): JsonObject {
        val seat = config.seatOf(player)
        return buildJsonObject {
            publicFields()
            if (seat >= 0) {
                put(Keys.SEAT, seat)
                put(Keys.HAND, cards(hands[seat]))
                val legal = if (handPhase == HandPhase.PLAYING && seat == turn) {
                    HokmRules.legalCards(hands[seat], currentTrick)
                } else {
                    emptyList()
                }
                put(Keys.LEGAL_CARDS, cards(legal))
            }
        }
    }

    override fun snapshot(): JsonObject = buildJsonObject {
        publicFields()
        put("hands", buildJsonArray { hands.forEach { add(cards(it)) } })
        put("pending", cards(pending))
        put("played", cards(played.toList()))
        put("rngState", config.rng.state)
    }

    private fun JsonObjectBuilder.publicFields() {
        put("game", HokmModule.ID)
        put("version", version)
        put("phase", phase.name)
        put("handPhase", handPhase.id)
        put("handNumber", handNumber)
        put("hakem", hakem)
        put("hokm", hokm?.id)
        put("turn", turn)
        put("trickNumber", trickNumber)
        put("leadSuit", currentTrick.firstOrNull()?.card?.suit?.id)
        put("currentTrick", plays(currentTrick))
        put("tricksWon", intArray(tricksWon))
        put("scores", intArray(scores))
        put("targetPoints", targetPoints)
        put("handCounts", buildJsonArray { hands.forEach { add(JsonPrimitive(it.size)) } })
        put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
        put("hakemDraw", plays(hakemDraw))
        lastTrick?.let { t ->
            put(
                "lastTrick",
                view { put("number", t.number); put("winner", t.winner); put("cards", plays(t.plays)) },
            )
        }
        lastHandResult?.let { put("lastHandResult", handResultJson(it)) }
    }

    private fun handResultJson(r: HandResult) = view {
        put("handNumber", r.handNumber)
        put("winnerTeam", r.winnerTeam)
        put("points", r.points)
        put("kind", r.kind.id)
        put("hakem", r.hakem)
        put("tricks", intArray(r.tricks.toIntArray()))
        put("scores", intArray(scores))
    }

    override fun result(): MatchResult? {
        if (phase != Phase.FINISHED) return null
        val teamA = listOf(players[0], players[2])
        val teamB = listOf(players[1], players[3])
        val aWins = scores[0] >= scores[1]
        return MatchResult(
            standings = listOf(
                Standing(teamA, if (aWins) 1 else 2, scores[0]),
                Standing(teamB, if (aWins) 2 else 1, scores[1]),
            ).sortedBy { it.rank },
        )
    }

    // ------------------------------------------------------------------ bot

    /** Deterministic bot move for [player], using only what that seat is allowed to see. */
    fun botAction(player: PlayerId): GameAction? {
        if (phase == Phase.FINISHED) return null
        val seat = config.seatOf(player)
        if (seat < 0) return null
        return when (handPhase) {
            HandPhase.CHOOSING_HOKM -> {
                if (seat != hakem) return null
                val suit = HokmBot.chooseHokm(hands[seat])
                GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put(HokmModule.PAYLOAD_SUIT, suit.id) })
            }

            HandPhase.PLAYING -> {
                if (seat != turn) return null
                val card = HokmBot.playCard(
                    BotView(
                        seat = seat,
                        hand = hands[seat].toList(),
                        hokm = hokm,
                        trick = currentTrick.toList(),
                        played = played.toSet(),
                        tricksWon = tricksWon.toList(),
                    ),
                )
                GameAction(HokmModule.ACTION_PLAY_CARD, view { put(HokmModule.PAYLOAD_CARD, card.code) })
            }
        }
    }

    // ------------------------------------------------------------------ json helpers

    private fun cards(list: List<Card>) = buildJsonArray { list.forEach { add(JsonPrimitive(it.code)) } }

    private fun plays(list: List<Play>) = buildJsonArray {
        list.forEach { p -> add(view { put("seat", p.seat); put("card", p.card.code) }) }
    }

    private fun intArray(values: IntArray) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() } ?: default
}
