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
import kotlinx.serialization.json.JsonArray
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

    /** Two-player only: hakem burns 3, opponent burns 2. */
    DISCARDING("discarding"),

    /** Two-player only: alternately drawing from the stock until both hold 13. */
    DRAWING("drawing"),
    PLAYING("playing"),
}

/**
 * The rules of Hokm for 2, 3 or 4 players (see [HokmRules] for the variants).
 *
 * * First hakem: cards are dealt face up one at a time from seat 0 until an Ace shows.
 * * Hakem gets 5 cards and names hokm. Then the hand is filled (4p/3p: dealt; 2p: burn + draw).
 * * Hakem leads. Players must follow suit if they can; otherwise any card (trumping is allowed).
 * * Highest trump wins the trick, else highest card of the led suit. The winner leads the next trick.
 * * First side to 7 tricks wins the hand: 1 point; kot = [Options.KOT_POINTS]; hakem kot =
 *   [Options.HAKEM_KOT_POINTS]. Hakem stays while their side wins; otherwise moves to the next seat.
 * * First side to [Options.TARGET_POINTS] wins the match.
 *
 * Pure function of `(seed, actions)`: the only randomness is the injected [GameConfig.rng].
 */
class HokmSession(private val config: GameConfig) : GameSession {

    object Options {
        const val TARGET_POINTS = "targetPoints"
        const val KOT_POINTS = "kotPoints"
        const val HAKEM_KOT_POINTS = "hakemKotPoints"
        /** `"pro"` (default) or `"easy"` — how the bots play. */
        const val BOT_LEVEL = "botLevel"
    }

    object Keys {
        const val HAND = "hand"
        const val LEGAL_CARDS = "legalCards"
        const val SEAT = "seat"
        const val REVEALED = "revealed"
        const val DISCARD_COUNT = "discardCount"
    }

    private val players: List<PlayerId> = config.players
    private val n = players.size
    private val teams = HokmRules.teamCount(n)
    private val targetPoints = config.options.int(Options.TARGET_POINTS, HokmModule.DEFAULT_TARGET_POINTS)
    private val kotPoints = config.options.int(Options.KOT_POINTS, HokmModule.DEFAULT_KOT_POINTS)
    private val easyBots = (config.options[Options.BOT_LEVEL] as? JsonPrimitive)?.content == HokmModule.BOT_EASY
    private val hakemKotPoints = config.options.int(Options.HAKEM_KOT_POINTS, HokmModule.DEFAULT_HAKEM_KOT_POINTS)

    override var phase: Phase = Phase.PLAYING
        private set
    override var version: Long = 0L
        private set

    // ---- match state
    private val scores = IntArray(teams)
    private var handNumber = 0
    private var hakem = 0
    private val hakemDraw = ArrayList<Play>()
    private var lastHandResult: HandResult? = null

    // ---- hand state
    private var handPhase = HandPhase.CHOOSING_HOKM
    private var hokm: Suit? = null
    private val hands: Array<MutableList<Card>> = Array(n) { ArrayList(17) }
    private var pending: List<Card> = emptyList()
    private val stock = ArrayDeque<Card>()
    private var revealed: Card? = null
    private var turn = 0
    private var trickNumber = 0
    private val currentTrick = ArrayList<Play>(n)
    private var lastTrick: CompletedTrick? = null
    private val tricksWon = IntArray(teams)
    private val played = LinkedHashSet<Card>()

    data class CompletedTrick(val number: Int, val winner: Int, val plays: List<Play>)

    data class HandResult(
        val handNumber: Int,
        /** `null` for a void hand. */
        val winnerTeam: Int?,
        val points: Int,
        val kind: HandWinKind,
        val hakem: Int,
        val tricks: List<Int>,
    )

    init {
        require(n in HokmRules.SUPPORTED_PLAYERS) { "Hokm is for 2, 3 or 4 players" }
        drawFirstHakem()
        startHand()
    }

    private fun teamOf(seat: Int) = HokmRules.teamOf(seat, n)
    private fun next(seat: Int) = HokmRules.nextSeat(seat, n)

    // ------------------------------------------------------------------ setup

    /** Classic opening: one card each, face up, from seat 0 around the table until an Ace appears. */
    private fun drawFirstHakem() {
        val deck = config.rng.shuffle(HokmRules.deckFor(n))
        var seat = 0
        for (card in deck) {
            hakemDraw += Play(seat, card)
            if (card.rank == Rank.ACE) {
                hakem = seat
                return
            }
            seat = next(seat)
        }
    }

    private fun startHand() {
        handNumber++
        handPhase = HandPhase.CHOOSING_HOKM
        hokm = null
        hands.forEach { it.clear() }
        stock.clear()
        revealed = null
        currentTrick.clear()
        lastTrick = null
        played.clear()
        tricksWon.fill(0)
        trickNumber = 0
        turn = hakem

        val deck = config.rng.shuffle(HokmRules.deckFor(n))
        hands[hakem] += deck.take(HokmRules.HAKEM_FIRST_DEAL)
        hands[hakem].sort()
        pending = deck.drop(HokmRules.HAKEM_FIRST_DEAL)
    }

    /** 4p/3p: the others get 5, then rounds of 4 to everybody until the deck is out. */
    private fun dealRest() {
        val deck = ArrayDeque(pending)
        var seat = next(hakem)
        repeat(n - 1) {
            repeat(HokmRules.HAKEM_FIRST_DEAL) { hands[seat] += deck.removeFirst() }
            seat = next(seat)
        }
        while (deck.isNotEmpty()) {
            var s = next(hakem)
            repeat(n) {
                repeat(4) { hands[s] += deck.removeFirst() }
                s = next(s)
            }
        }
        pending = emptyList()
        hands.forEach { it.sort() }
    }

    /** 2p: the opponent also gets 5; the remaining 42 become the stock. */
    private fun setupTwoPlayer() {
        val deck = ArrayDeque(pending)
        val opp = next(hakem)
        repeat(HokmRules.HAKEM_FIRST_DEAL) { hands[opp] += deck.removeFirst() }
        hands[opp].sort()
        stock.addAll(deck)
        pending = emptyList()
        handPhase = HandPhase.DISCARDING
        turn = hakem
    }

    private fun discardCountFor(seat: Int): Int =
        if (seat == hakem) HokmRules.TWO_PLAYER_HAKEM_BURN else HokmRules.TWO_PLAYER_OPPONENT_BURN

    // ------------------------------------------------------------------ apply

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        if (phase == Phase.FINISHED) return reject(ErrCodes.BAD_STATE, "بازی تمام شده است")
        val seat = config.seatOf(player)
        if (seat < 0) return reject(ErrCodes.UNKNOWN_PLAYER, "این بازیکن سر میز نیست")

        return when (action.type) {
            HokmModule.ACTION_CHOOSE_HOKM -> chooseHokm(seat, action.payload)
            HokmModule.ACTION_DISCARD -> discard(seat, action.payload)
            HokmModule.ACTION_KEEP_CARD -> draw(seat, keep = true)
            HokmModule.ACTION_PASS_CARD -> draw(seat, keep = false)
            HokmModule.ACTION_PLAY_CARD -> playCard(seat, action.payload)
            else -> reject(ErrCodes.ILLEGAL_ACTION, "حرکت «${action.type}» در حکم وجود ندارد")
        }
    }

    private fun chooseHokm(seat: Int, payload: JsonObject): ApplyResult {
        if (handPhase != HandPhase.CHOOSING_HOKM) return reject(ErrCodes.BAD_STATE, "حکم این دست قبلاً انتخاب شده")
        if (seat != hakem) return reject(ErrCodes.NOT_YOUR_TURN, "فقط حاکم حکم را انتخاب می‌کند")
        val suitText = payload.string(HokmModule.PAYLOAD_SUIT) ?: return reject(ErrCodes.BAD_PAYLOAD, "خال حکم مشخص نشده")
        val suit = Suit.parse(suitText) ?: return reject(ErrCodes.BAD_PAYLOAD, "خال «$suitText» معتبر نیست")

        hokm = suit
        version++
        val emissions = ArrayList<Emission>()
        emissions += Emission.Public("hokm_chosen", view { put("hakem", hakem); put("hokm", suit.id); put("handNumber", handNumber) })
        emissions += Emission.Feed("hokm_chosen", view { put("seat", hakem); put("suit", suit.id) })
        emissions += Emission.Sound("hokm_chosen")

        if (n == 2) {
            setupTwoPlayer()
        } else {
            dealRest()
            handPhase = HandPhase.PLAYING
            turn = hakem
            trickNumber = 1
        }
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    private fun discard(seat: Int, payload: JsonObject): ApplyResult {
        if (handPhase != HandPhase.DISCARDING) return reject(ErrCodes.BAD_STATE, "الان وقت سوزاندن کارت نیست")
        if (seat != turn) return reject(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        val codes = payload[HokmModule.PAYLOAD_CARDS] as? JsonArray
            ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت‌های سوزاندنی مشخص نشده")
        val cards = codes.map { el ->
            Card.parse(runCatching { el.jsonPrimitive.content }.getOrDefault(""))
                ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت نامعتبر")
        }
        val need = discardCountFor(seat)
        if (cards.size != need || cards.toSet().size != need) return reject(ErrCodes.ILLEGAL_ACTION, "باید دقیقاً ${need.toFaWord()} کارت بسوزانی")
        if (cards.any { it !in hands[seat] }) return reject(ErrCodes.ILLEGAL_ACTION, "این کارت در دست شما نیست")

        hands[seat].removeAll(cards)
        version++
        val emissions = ArrayList<Emission>()
        emissions += Emission.Public("discarded", view { put("seat", seat); put("count", need) })
        emissions += Emission.Sound("card_played")

        val opp = next(seat)
        if (seat == hakem) {
            turn = opp
        } else {
            handPhase = HandPhase.DRAWING
            turn = hakem
            revealed = stock.removeFirst()
            emissions += Emission.Public("drawing_started", view { put("stockCount", stock.size + 1) })
        }
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    private fun draw(seat: Int, keep: Boolean): ApplyResult {
        if (handPhase != HandPhase.DRAWING) return reject(ErrCodes.BAD_STATE, "الان وقت برداشتن کارت نیست")
        if (seat != turn) return reject(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        val shown = revealed ?: return reject(ErrCodes.BAD_STATE, "کارتی رو نشده")
        val blind = stock.removeFirst()
        val taken = if (keep) shown else blind
        hands[seat] += taken
        hands[seat].sort()
        revealed = null
        version++

        val emissions = ArrayList<Emission>()
        emissions += Emission.Public("card_drawn", view { put("seat", seat); put("kept", keep); put("stockCount", stock.size) })
        emissions += Emission.Sound("card_deal")

        if (stock.isEmpty()) {
            check(hands.all { it.size == HokmRules.cardsPerPlayer(2) }) { "two-player draw must end with 13 cards each" }
            handPhase = HandPhase.PLAYING
            turn = hakem
            trickNumber = 1
            emissions += Emission.Public("drawing_finished", view { })
        } else {
            turn = next(seat)
            revealed = stock.removeFirst()
        }
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    private fun playCard(seat: Int, payload: JsonObject): ApplyResult {
        if (handPhase != HandPhase.PLAYING) {
            return reject(ErrCodes.BAD_STATE, if (handPhase == HandPhase.CHOOSING_HOKM) "اول باید حاکم حکم را انتخاب کند" else "هنوز دست‌ها کامل نشده")
        }
        if (seat != turn) return reject(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        val cardText = payload.string(HokmModule.PAYLOAD_CARD) ?: return reject(ErrCodes.BAD_PAYLOAD, "کارت مشخص نشده")
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
        emissions += Emission.Public("card_played", view { put("seat", seat); put("card", card.code); put("trickNumber", trickNumber) })
        emissions += Emission.Sound("card_played")

        if (currentTrick.size < n) {
            turn = next(seat)
            emissions += Emission.Private(players[seat], privateView(players[seat]))
            return ApplyResult.Ok(emissions)
        }

        // ---- trick complete
        val winner = HokmRules.trickWinner(currentTrick, trump, n)
        val winnerTeam = teamOf(winner)
        tricksWon[winnerTeam]++
        lastTrick = CompletedTrick(trickNumber, winner, currentTrick.toList())
        currentTrick.clear()
        turn = winner
        emissions += Emission.Public(
            "trick_won",
            view { put("winner", winner); put("team", winnerTeam); put("trickNumber", trickNumber); put("tricksWon", intArray(tricksWon)) },
        )
        emissions += Emission.Feed("trick_won", view { put("seat", winner); put("trickNumber", trickNumber) })
        emissions += Emission.Sound("trick_won")

        val someoneReachedSeven = tricksWon[winnerTeam] >= HokmRules.TRICKS_TO_WIN_HAND
        val cardsExhausted = hands.all { it.isEmpty() }
        if (!someoneReachedSeven && !cardsExhausted) {
            trickNumber++
            emissions += Emission.Private(players[seat], privateView(players[seat]))
            return ApplyResult.Ok(emissions)
        }

        // ---- hand complete
        val result = scoreHand(if (someoneReachedSeven) winnerTeam else null)
        lastHandResult = result
        emissions += Emission.Public("hand_finished", handResultJson(result))
        emissions += Emission.Feed(
            "hand_finished",
            view { put("team", result.winnerTeam); put("points", result.points); put("kind", result.kind.id) },
            kind = if (result.kind == HandWinKind.NORMAL) "info" else "highlight",
        )
        emissions += Emission.Sound(
            when (result.kind) {
                HandWinKind.NORMAL, HandWinKind.VOID -> "hand_won"
                HandWinKind.KOT -> "kot"
                HandWinKind.HAKEM_KOT -> "hakem_kot"
            },
        )

        val wt = result.winnerTeam
        if (wt != null && scores[wt] >= targetPoints) {
            phase = Phase.FINISHED
            emissions += Emission.Public("game_finished", view { put("winnerTeam", wt); put("scores", intArray(scores)) })
            emissions += Emission.Feed("game_finished", view { put("team", wt) }, kind = "highlight")
            emissions += Emission.Sound("game_won")
            return ApplyResult.Ok(emissions)
        }

        val hakemTeamWon = wt == teamOf(hakem)
        hakem = HokmRules.nextHakem(hakem, hakemTeamWon, n)
        startHand()
        emissions += Emission.Public("hand_started", view { put("handNumber", handNumber); put("hakem", hakem) })
        emissions += Emission.Feed("hand_started", view { put("handNumber", handNumber); put("hakem", hakem) })
        emissions += Emission.Sound("shuffle")
        emissions += privateEmissions()
        return ApplyResult.Ok(emissions)
    }

    /** Settles a finished hand. [reachedSeven] is the side that got 7, or `null` when cards ran out (3p). */
    private fun scoreHand(reachedSeven: Int?): HandResult {
        val hakemTeam = teamOf(hakem)
        val winnerTeam: Int? = reachedSeven ?: run {
            val max = tricksWon.max()
            val leaders = tricksWon.indices.filter { tricksWon[it] == max }
            if (leaders.size == 1) leaders.first() else null
        }
        if (winnerTeam == null) {
            return HandResult(handNumber, null, 0, HandWinKind.VOID, hakem, tricksWon.toList())
        }
        val losers = tricksWon.indices.filter { it != winnerTeam }
        val (kind, points) = HokmRules.handResult(
            winnerTricks = tricksWon[winnerTeam],
            minLoserTricks = losers.minOf { tricksWon[it] },
            hakemTricks = tricksWon[hakemTeam],
            hakemLost = hakemTeam != winnerTeam,
            kotPoints = kotPoints,
            hakemKotPoints = hakemKotPoints,
        )
        scores[winnerTeam] += points
        return HandResult(handNumber, winnerTeam, points, kind, hakem, tricksWon.toList())
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
            HandPhase.DISCARDING -> if (seat == turn) listOf(HokmModule.ACTION_DISCARD) else emptyList()
            HandPhase.DRAWING -> if (seat == turn) listOf(HokmModule.ACTION_KEEP_CARD, HokmModule.ACTION_PASS_CARD) else emptyList()
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
                val legal = if (handPhase == HandPhase.PLAYING && seat == turn) HokmRules.legalCards(hands[seat], currentTrick) else emptyList()
                put(Keys.LEGAL_CARDS, cards(legal))
                if (handPhase == HandPhase.DRAWING && seat == turn) put(Keys.REVEALED, revealed?.code)
                if (handPhase == HandPhase.DISCARDING && seat == turn) put(Keys.DISCARD_COUNT, discardCountFor(seat))
            }
        }
    }

    override fun snapshot(): JsonObject = buildJsonObject {
        publicFields()
        put("hands", buildJsonArray { hands.forEach { add(cards(it)) } })
        put("pending", cards(pending))
        put("stock", cards(stock.toList()))
        put("revealedCard", revealed?.code)
        put("played", cards(played.toList()))
        put("rngState", config.rng.state)
    }

    private fun JsonObjectBuilder.publicFields() {
        put("game", HokmModule.ID)
        put("version", version)
        put("phase", phase.name)
        put("playerCount", n)
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
        put("stockCount", stock.size + (if (revealed != null) 1 else 0))
        put("handCounts", buildJsonArray { hands.forEach { add(JsonPrimitive(it.size)) } })
        put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
        put("hakemDraw", plays(hakemDraw))
        lastTrick?.let { t -> put("lastTrick", view { put("number", t.number); put("winner", t.winner); put("cards", plays(t.plays)) }) }
        lastHandResult?.let { put("lastHandResult", handResultJson(it)) }
    }

    private fun handResultJson(r: HandResult) = view {
        put("handNumber", r.handNumber)
        put("winnerTeam", r.winnerTeam)
        put("points", r.points)
        put("kind", r.kind.id)
        put("hakem", r.hakem)
        put("hokm", hokm?.id)
        put("tricks", intArray(r.tricks.toIntArray()))
        put("scores", intArray(scores))
    }

    override fun result(): MatchResult? {
        if (phase != Phase.FINISHED) return null
        val order = scores.indices.sortedByDescending { scores[it] }
        val standings = ArrayList<Standing>()
        var rank = 0
        var prev = Int.MIN_VALUE
        order.forEachIndexed { i, team ->
            if (scores[team] != prev) {
                rank = i + 1
                prev = scores[team]
            }
            val members = players.indices.filter { teamOf(it) == team }.map { players[it] }
            standings += Standing(members, rank, scores[team])
        }
        return MatchResult(standings)
    }

    // ------------------------------------------------------------------ bot

    /** Deterministic bot move for [player], using only what that seat is allowed to see. */
    fun botAction(player: PlayerId): GameAction? {
        if (phase == Phase.FINISHED) return null
        val seat = config.seatOf(player)
        if (seat < 0) return null
        val view = BotView(
            seat = seat,
            players = n,
            hand = hands[seat].toList(),
            hokm = hokm,
            trick = currentTrick.toList(),
            played = played.toSet(),
            tricksWon = tricksWon.toList(),
            hakem = hakem,
        )
        return when (handPhase) {
            HandPhase.CHOOSING_HOKM -> {
                if (seat != hakem) return null
                GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put(HokmModule.PAYLOAD_SUIT, HokmBot.chooseHokm(hands[seat]).id) })
            }

            HandPhase.DISCARDING -> {
                if (seat != turn) return null
                val burn = HokmBot.discard(view, discardCountFor(seat))
                GameAction(HokmModule.ACTION_DISCARD, view { put(HokmModule.PAYLOAD_CARDS, cards(burn)) })
            }

            HandPhase.DRAWING -> {
                if (seat != turn) return null
                val keep = HokmBot.keepRevealed(view, requireNotNull(revealed))
                GameAction(if (keep) HokmModule.ACTION_KEEP_CARD else HokmModule.ACTION_PASS_CARD)
            }

            HandPhase.PLAYING -> {
                if (seat != turn) return null
                val card = if (easyBots) HokmBot.playCardEasy(view) else HokmBot.playCard(view)
                GameAction(HokmModule.ACTION_PLAY_CARD, view { put(HokmModule.PAYLOAD_CARD, card.code) })
            }
        }
    }

    // ------------------------------------------------------------------ json helpers

    private fun cards(list: List<Card>) = buildJsonArray { list.forEach { add(JsonPrimitive(it.code)) } }

    private fun plays(list: List<Play>) = buildJsonArray { list.forEach { p -> add(view { put("seat", p.seat); put("card", p.card.code) }) } }

    private fun intArray(values: IntArray) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

    private fun JsonObject.int(key: String, default: Int): Int =
        this[key]?.let { runCatching { it.jsonPrimitive.content.toInt() }.getOrNull() } ?: default

    private fun JsonObject.string(key: String): String? = this[key]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

    private fun Int.toFaWord(): String = when (this) {
        2 -> "دو"
        3 -> "سه"
        else -> toString()
    }
}
