package ir.dorehami.games.hokm.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.view
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmRules
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Which sound a card makes when it hits the table.
 *
 * A trump card gets the same heavy slap the opening ace gets — whether it is a fat lead, a cut on
 * somebody else's suit, or just a trump following a trump — and everything else is the soft
 * card-on-felt tick. Pure so the rule can be tested without a device or a speaker.
 */
internal object TableAudio {
    /**
     * The slab: one heavy slap, one asset (`hokm_card_slam.wav`). The hakem's ace, the trump card
     * landing on the table and the "حکم شد" slap all play *this* string, so there is exactly one
     * slap in the game and no way for the trump to sound like something else.
     */
    const val SLAM = "card_slam"
    const val PLAY = "card_play"
    const val DEAL = "card_deal"

    fun cardLands(trump: Boolean, cut: Boolean): String = if (trump || cut) SLAM else PLAY

    /** The card the opening draw turns face up: an ace gets the slab, anything else a soft tick. */
    fun hakemDraw(rank: Rank): String = if (rank == Rank.ACE) SLAM else DEAL
}

/** A player as drawn on the table. */
data class SeatInfo(val seat: Int, val name: String, val isBot: Boolean)

/** Result of the last finished hand, as the UI needs it. */
data class HandSummary(
    val handNumber: Int,
    /** `null` for a void three-player hand. */
    val winnerTeam: Int?,
    val points: Int,
    val kind: HandWinKind,
    val hakem: Int,
    val tricks: List<Int>,
    val hokm: Suit? = null,
)

/** A sticker shown in a bubble over a seat. */
data class Sticker(val id: String, val emoji: String, val captionFa: String)

/** All stickers available in the tray; ids are what travels over the wire later. */
object Stickers {
    val all = listOf(
        Sticker("clap", "👏", "آفرین!"),
        Sticker("laugh", "😂", "خنده‌دار بود"),
        Sticker("cool", "😎", "حرفه‌ای"),
        Sticker("angry", "😤", "اَه!"),
        Sticker("think", "🤔", "صبر کن…"),
        Sticker("fire", "🔥", "ترکوندی"),
        Sticker("crown", "👑", "حاکم منم"),
        Sticker("heart", "❤️", "دمت گرم"),
        Sticker("cry", "😭", "باختیم…"),
        Sticker("fast", "⏱️", "زود باش!"),
        Sticker("tea", "🍵", "یه چایی بزنیم"),
        Sticker("gg", "🤝", "بازی خوبی بود"),
    )

    fun byId(id: String): Sticker? = all.firstOrNull { it.id == id }
}

/** A sticker currently visible over [seat]; [stamp] makes repeated identical stickers re-animate. */
data class SeatSticker(val seat: Int, val sticker: Sticker, val stamp: Long)

/** Something transient to say on the table (feed line / banner). */
data class Banner(
    val title: String,
    val subtitle: String? = null,
    val big: Boolean = false,
    /** Trump suit of a "حکم شد" banner, drawn as a vector pip (the bundled font has no suit glyphs). */
    val suit: Suit? = null,
)

/** One-shot visual effects the screen renders on top of the table. */
enum class FxKind {
    /** A trump was thrown on a non-trump lead ("بریدن"): golden flash + sparks at that seat's slot. */
    CUT,
    /** Trick swept: a small sparkle at the winner. */
    TRICK_WIN,
    /** Kot / hakem kot: a big burst in the middle. */
    KOT,
    /** Match won by my side: confetti. */
    CONFETTI,
}

data class TableFx(val kind: FxKind, val seat: Int, val stamp: Long)

/**
 * One card in the air during the deal: it left the deck at the centre of the table and is on its way
 * to [seat], where it becomes that hand's card number [slot] (0-based).
 *
 * The deck is a real, visible pile of cards and every card in a hand is one of its cards: the table
 * never grows a hand out of thin air, and the screen never has to invent a card to fly.
 */
data class DealFlight(val id: Int, val seat: Int, val slot: Int)

/**
 * Everything the screen draws. Built **only** from the human player's `privateView()` plus the
 * UI-side animation bookkeeping — the screen never touches the session.
 */
data class HokmUiState(
    val seats: List<SeatInfo> = emptyList(),
    val playerCount: Int = 4,
    val mySeat: Int = 0,
    val phase: Phase = Phase.PLAYING,
    val handPhase: HandPhase = HandPhase.CHOOSING_HOKM,
    val handNumber: Int = 1,
    val hakem: Int = 0,
    val hokm: Suit? = null,
    val turn: Int = 0,
    val trickNumber: Int = 0,
    val tricksWon: List<Int> = listOf(0, 0),
    val scores: List<Int> = listOf(0, 0),
    val targetPoints: Int = 7,
    val handCounts: List<Int> = listOf(0, 0, 0, 0),
    val myHand: List<Card> = emptyList(),
    val legalCards: Set<Card> = emptySet(),
    /** Two-player: the stock card shown to me while I decide keep/pass. */
    val revealed: Card? = null,
    /** Two-player: how many cards I must burn right now (0 = not my burn turn). */
    val discardCount: Int = 0,
    val stockCount: Int = 0,
    /** Cards I have tapped so far while burning. */
    val selectedForDiscard: Set<Card> = emptySet(),
    val stickers: List<SeatSticker> = emptyList(),
    /** Cards on the table right now (kept visible for a moment after a trick closes). */
    val tableCards: List<Play> = emptyList(),
    /** Seat that is sweeping the current trick, while the collect animation runs. */
    val collectingTo: Int? = null,
    /**
     * A closed trick is on screen and waiting to be swept (HK-010).
     *
     * Without this the engine had already moved to the next trick by the time the four cards were
     * shown, so any snapshot read during that window replaced them with an empty table and the cards
     * seemed to vanish instead of being collected.
     */
    val holdingTrick: Boolean = false,
    val lastHand: HandSummary? = null,
    val banner: Banner? = null,
    val hakemDraw: List<Play> = emptyList(),
    /** How many of [hakemDraw] are face-up during the opening animation (`null` = intro finished). */
    val introShown: Int? = null,
    val dealing: Boolean = false,
    /**
     * While a deal animates: how many cards each seat has visibly received (`null` = show the real
     * counts). The engine hands out a whole hand at once, so the table plays the deal itself, one
     * card per seat per beat, exactly like a person would deal around the table.
     */
    val dealReveal: List<Int>? = null,
    /**
     * How many cards each seat is holding on screen while the deal is paused (the hakem's five,
     * before the trump is named). Display only: it does not block input the way [dealReveal] does,
     * so the hakem can pick a trump while the deck waits on the table.
     */
    val handReveal: List<Int>? = null,
    /** Cards still in the deck at the centre of the table (`null` = no deck on screen). */
    val dealStock: Int? = null,
    /** The cards currently in the air, oldest first — only the last few are drawn. */
    val dealFlights: List<DealFlight> = emptyList(),
    /** Trump suit while the "حکم شد" card is being slapped onto the table (short-lived). */
    val trumpReveal: Suit? = null,
    val error: String? = null,
    val finished: Boolean = false,
    val winnerTeam: Int? = null,
    val feed: List<String> = emptyList(),
    val fx: List<TableFx> = emptyList(),
    /** Every finished hand so far (score sheet). */
    val handHistory: List<HandSummary> = emptyList(),
    val lastTrick: List<Play> = emptyList(),
    val lastTrickWinner: Int? = null,
    /** Wall-clock deadline for the current human move in rooms (0 = no timer). */
    val turnDeadlineMs: Long = 0L,
    val turnSeconds: Int = 0,
    /** Short label of the game mode shown in the score bar ("ربات", "بلوتوث", "آنلاین"). */
    val modeLabel: String = "",
    /** Session revision of the snapshot this table currently shows (0 before the first read). */
    val revision: Long = 0L,
    /**
     * I have sent a move and the table has not seen its result yet. A second tap must not send a
     * second action; the flag clears as soon as a newer snapshot or an error arrives.
     */
    val isSendingMove: Boolean = false,
) {
    val myTeam: Int get() = HokmRules.teamOf(mySeat, playerCount)
    fun teamOf(seat: Int): Int = HokmRules.teamOf(seat, playerCount)

    /**
     * Nothing is being animated, so a snapshot may be drawn as-is: no collect, banner, deal or
     * opening draw is on screen (`HokmTable.refresh()` and the event queue both use this gate).
     */
    val isCalm: Boolean get() =
        !finished && introShown == null && collectingTo == null && !holdingTrick && !dealing &&
            banner == null && dealReveal == null
    val isMyTurn: Boolean get() = isCalm && !isSendingMove && handPhase == HandPhase.PLAYING && turn == mySeat
    val iChooseHokm: Boolean get() = isCalm && !isSendingMove && handPhase == HandPhase.CHOOSING_HOKM && hakem == mySeat
    val iDiscard: Boolean get() =
        isCalm && !isSendingMove && handPhase == HandPhase.DISCARDING && turn == mySeat && discardCount > 0
    val iDraw: Boolean get() =
        isCalm && !isSendingMove && handPhase == HandPhase.DRAWING && turn == mySeat && revealed != null
    val isTeamGame: Boolean get() = playerCount == 4

    /** Cards [seat] has visibly picked up (the deal animation hides the rest). */
    fun visibleCount(seat: Int): Int {
        val reveal = dealReveal ?: handReveal ?: return handCounts.getOrElse(seat) { 0 }
        return reveal.getOrNull(seat)?.coerceIn(0, handCounts.getOrElse(seat) { 0 })
            ?: handCounts.getOrElse(seat) { 0 }
    }

    /** My hand as it is visible right now: the deal reveals it card by card. */
    val visibleHand: List<Card>
        get() {
            val reveal = dealReveal ?: handReveal ?: return myHand
            return myHand.take(reveal.getOrElse(mySeat) { 0 }.coerceIn(0, myHand.size))
        }
}

/**
 * The table's state machine: turns the private view + emissions into [HokmUiState] with the pacing
 * of a real table (trick stays visible, collect animation, banners, deal animation, stickers).
 *
 * It does not know where moves come from: [HokmLocalMatch] plugs a local [ir.dorehami.gamesdk.GameEngine]
 * with bots, [HokmSessionMatch] plugs a [ir.dorehami.session.SessionClient] (Bluetooth / online).
 * The screen only ever reads [state].
 */
abstract class HokmTable(
    protected val scope: CoroutineScope,
    protected val seats: List<SeatInfo>,
    protected val mySeat: Int,
    protected val onSound: (String) -> Unit,
) {
    internal companion object {
        const val BOT_THINK_MS = 650L
        const val BOT_LEAD_MS = 900L
        const val TRICK_SHOW_MS = 1150L
        const val COLLECT_MS = 820L
        const val INTRO_CARD_MS = 420L
        const val DEAL_MS = 1400L

        /** One card per beat while dealing — fast enough to feel like a dealer, slow enough to see. */
        const val DEAL_CARD_MS = 120L

        /** How long a dealt card spends travelling from the deck to its seat. */
        const val DEAL_FLIGHT_MS = 300L

        /**
         * How many cards leave the deck before the first one lands. Two or three are always in the
         * air, so the deal reads as a stream of cards instead of one slow card at a time.
         */
        const val DEAL_LAND_LAG = 3
        const val HAND_BANNER_MS = 2600L
    }

    protected val playerCount: Int = seats.size

    /** My private view right now (the only thing the table reads). `null` = not available yet. */
    protected abstract fun privateView(): JsonObject?

    protected abstract fun isFinished(): Boolean

    /** Sends one of *my* actions. Rejections come back through [onResult] / [setError]. */
    protected abstract fun sendMine(action: GameAction)

    /**
     * Local tables move the bot in [actor] here and return `true`. Session tables return `false`:
     * the host moves bots and the result arrives as events.
     */
    protected abstract suspend fun botStep(s: HokmUiState, actor: Int): Boolean

    /** Session tables: a sticker from a remote seat. */
    fun showRemoteSticker(seat: Int, stickerId: String) {
        Stickers.byId(stickerId)?.let { showSticker(seat, it) }
    }

    /** Session tables: send my sticker to the room as well. */
    protected open fun onStickerSent(sticker: Sticker) = Unit

    protected val hasBots: Boolean get() = seats.any { it.isBot }

    protected val _state = mutableStateOf(HokmUiState(seats = seats, playerCount = playerCount, mySeat = mySeat))
    private var stickerStamp = 0L
    private var fxStamp = 0L
    val state: State<HokmUiState> get() = _state

    protected fun pushFx(kind: FxKind, seat: Int, ttlMs: Long = 1400) {
        val stamp = ++fxStamp
        _state.value = _state.value.copy(fx = _state.value.fx + TableFx(kind, seat, stamp))
        scope.launch {
            delay(ttlMs)
            _state.value = _state.value.copy(fx = _state.value.fx.filter { it.stamp != stamp })
        }
    }

    protected val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Deferred animations, in the order the events arrived (HK-010). A single slot per animation kind
     * used to drop steps on a burst; the queue keeps them, deduplicates duplicates and forgets the
     * hand a snapshot already replaced.
     */
    private val animQueue = TableAnimQueue()
    private var started = false

    /** `true` while an animation step is still queued; the loop drains them one by one. */
    protected val hasQueuedAnimation: Boolean get() = !animQueue.isEmpty

    /** Starts the opening animation and the table loop. Safe to call once. */
    fun start() {
        if (started) return
        started = true
        refresh()
        _state.value = _state.value.copy(introShown = 0)
        scope.launch { runIntro() }
        scope.launch { loop() }
    }


    // ------------------------------------------------------------------ human input

    fun chooseHokm(suit: Suit) {
        val s = _state.value
        if (!s.iChooseHokm) return
        send(GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put(HokmModule.PAYLOAD_SUIT, suit.id) }))
    }

    /**
     * Sends one of my moves exactly once.
     *
     * The table marks itself as "sending" before the action leaves, so a double tap (or a tap while
     * the host is still answering) cannot create a second move; [HokmUiState.isSendingMove] is
     * cleared by the answer (a newer snapshot, an accepted local result, or an error).
     */
    private fun send(action: GameAction) {
        if (_state.value.isSendingMove) return
        _state.value = _state.value.copy(isSendingMove = true)
        sendMine(action)
        wake.trySend(Unit)
    }

    fun playCard(card: Card) {
        val s = _state.value
        if (!s.isMyTurn) return
        if (card !in s.legalCards) {
            val lead = s.tableCards.firstOrNull()?.card?.suit
            setError(if (lead != null) "باید ${lead.titleFa} بازی کنی" else "این کارت مجاز نیست")
            onSound("tap")
            return
        }
        send(GameAction(HokmModule.ACTION_PLAY_CARD, view { put(HokmModule.PAYLOAD_CARD, card.code) }))
    }

    /** Two-player burn: toggle a card; when enough are selected call [confirmDiscard]. */
    fun toggleDiscard(card: Card) {
        val s = _state.value
        if (!s.iDiscard || card !in s.myHand) return
        val sel = if (card in s.selectedForDiscard) s.selectedForDiscard - card else {
            if (s.selectedForDiscard.size >= s.discardCount) return
            s.selectedForDiscard + card
        }
        onSound("tap")
        _state.value = s.copy(selectedForDiscard = sel)
    }

    fun confirmDiscard() {
        val s = _state.value
        if (!s.iDiscard || s.selectedForDiscard.size != s.discardCount) return
        val payload = buildJsonObject {
            put(HokmModule.PAYLOAD_CARDS, kotlinx.serialization.json.buildJsonArray { s.selectedForDiscard.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.code)) } })
        }
        _state.value = s.copy(selectedForDiscard = emptySet())
        send(GameAction(HokmModule.ACTION_DISCARD, payload))
    }

    fun keepCard() = drawDecision(keep = true)

    fun passCard() = drawDecision(keep = false)

    private fun drawDecision(keep: Boolean) {
        if (!_state.value.iDraw) return
        send(GameAction(if (keep) HokmModule.ACTION_KEEP_CARD else HokmModule.ACTION_PASS_CARD))
    }

    /** Show a sticker over my seat (bots may answer). Chat is UI-only: it never enters the rules. */
    fun sendSticker(sticker: Sticker) {
        showSticker(mySeat, sticker)
        onStickerSent(sticker)
        if (!hasBots) return
        // A light-hearted reply from a bot now and then — deterministic in the id so it never spams.
        val bots = seats.filter { it.isBot }.map { it.seat }
        val replyFrom = bots[(sticker.id.hashCode() and 0x7fffffff) % bots.size]
        val reply = when (sticker.id) {
            "angry", "cry" -> Stickers.byId("laugh")
            "clap", "heart", "gg" -> Stickers.byId("heart")
            "fast" -> Stickers.byId("think")
            "tea" -> Stickers.byId("tea")
            else -> null
        }
        if (reply != null) scope.launch { delay(1400); showSticker(replyFrom, reply) }
    }

    protected fun showSticker(seat: Int, sticker: Sticker) {
        val stamp = ++stickerStamp
        onSound("sticker")
        _state.value = _state.value.copy(stickers = _state.value.stickers.filter { it.seat != seat } + SeatSticker(seat, sticker, stamp))
        scope.launch {
            delay(2600)
            _state.value = _state.value.copy(stickers = _state.value.stickers.filter { it.stamp != stamp })
        }
    }

    /** Bots "talk" on big moments: kot, hakem kot, winning the match. */
    private fun botReactions(summary: HandSummary) {
        if (!hasBots) return
        val humanTeam = HokmRules.teamOf(mySeat, playerCount)
        val winnerTeam = summary.winnerTeam ?: return
        val botSeats = seats.filter { it.isBot }.map { it.seat }
        val botsOnWinningSide = botSeats.filter { HokmRules.teamOf(it, playerCount) == winnerTeam }
        val botsOnLosingSide = botSeats.filter { HokmRules.teamOf(it, playerCount) != winnerTeam }
        scope.launch {
            delay(TRICK_SHOW_MS + COLLECT_MS + 300)
            when (summary.kind) {
                HandWinKind.KOT, HandWinKind.HAKEM_KOT -> {
                    botsOnWinningSide.firstOrNull()?.let { showSticker(it, Stickers.byId("fire")!!) }
                    if (winnerTeam != humanTeam) {
                        delay(900)
                        botsOnLosingSide.firstOrNull { it != mySeat }?.let { showSticker(it, Stickers.byId("cry")!!) }
                    }
                }
                HandWinKind.NORMAL -> if (summary.handNumber % 3 == 0) botsOnWinningSide.firstOrNull()?.let { showSticker(it, Stickers.byId("cool")!!) }
                HandWinKind.VOID -> botsOnWinningSide.firstOrNull()?.let { showSticker(it, Stickers.byId("think")!!) }
            }
        }
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    // ------------------------------------------------------------------ loop

    protected open suspend fun runIntro() {
        val draw = _state.value.hakemDraw
        for (i in 1..draw.size) {
            _state.value = _state.value.copy(introShown = i)
            val card = draw[i - 1].card
            // The first ace decides the hakem: give it the heavy slap instead of a soft deal tick.
            onSound(TableAudio.hakemDraw(card.rank))
            if (card.rank == Rank.ACE) {
                pushFx(FxKind.CUT, draw[i - 1].seat, 900)
                delay(240)
            }
            delay(INTRO_CARD_MS)
        }
        val hakem = _state.value.hakem
        _state.value = _state.value.copy(
            banner = Banner("${seats[hakem].name} حاکم شد", "اولین آس را آورد", big = true),
        )
        onSound("hokm_chosen")
        delay(1500)
        // Real tables deal the hakem their first five cards before the trump is named — one card at
        // a time, not a hand that pops into existence.
        _state.value = _state.value.copy(introShown = null, banner = null)
        refresh()
        dealFirstFive(hakem)
        wake.trySend(Unit)
    }

    /** Every card in the deck this table deals: 52 in a four-player game, 51 with three. */
    private fun deckTotal(): Int = playerCount * HokmRules.cardsPerPlayer(playerCount)

    private var dealFlightId = 0

    /** Puts one card in the air between the deck and [seat]; older flights are already gone. */
    private fun pushDealFlight(seat: Int, slot: Int) {
        val flight = DealFlight(++dealFlightId, seat, slot)
        _state.value = _state.value.copy(dealFlights = (_state.value.dealFlights + flight).takeLast(DEAL_LAND_LAG))
    }

    /**
     * Publishes the deal as it stands: [landed] is what each hand shows, [launched] is how many
     * cards have left the deck. The two differ by the cards still in the air, so a hand grows the
     * moment its card arrives rather than the moment it was thrown.
     */
    private fun publishDeal(landed: List<Int>, launched: Int, animating: Boolean) {
        _state.value = _state.value.copy(
            dealReveal = if (animating) landed else null,
            handReveal = landed,
            dealStock = if (playerCount >= 3) (deckTotal() - launched).coerceAtLeast(0) else null,
        )
    }

    /**
     * Flies every card in [order] out of the deck, one beat each, and grows the hands as the cards
     * land. [counts] is updated in place so the caller keeps the hands it dealt.
     */
    private suspend fun flyDeal(order: List<Int>, counts: MutableList<Int>) {
        val inFlight = ArrayDeque<Int>()
        var launched = 0
        fun landOne() {
            val seat = inFlight.removeFirst()
            counts[seat] = counts.getOrElse(seat) { 0 } + 1
            onSound("card_deal")
        }
        for (seat in order) {
            // The card being dealt is the next one in that seat's fan, on top of what is in the air.
            pushDealFlight(seat, counts.getOrElse(seat) { 0 } + inFlight.count { it == seat })
            inFlight.addLast(seat)
            launched++
            if (inFlight.size > DEAL_LAND_LAG) landOne()
            publishDeal(counts.toList(), launched, animating = true)
            delay(DEAL_CARD_MS)
        }
        // Let the last cards land before anybody plays.
        while (inFlight.isNotEmpty()) {
            landOne()
            publishDeal(counts.toList(), launched, animating = true)
            delay(DEAL_CARD_MS)
        }
    }

    /**
     * Hands the hakem five cards from a deck that is visibly on the table, one card at a time. The
     * five stay on the table (and the rest of the deck with them) while the trump is named.
     */
    private suspend fun dealFirstFive(hakem: Int) {
        val counts = MutableList(playerCount) { 0 }
        _state.value = _state.value.copy(dealFlights = emptyList())
        publishDeal(counts.toList(), 0, animating = true)
        // A beat with the whole deck sitting in the middle, before the first card moves.
        delay(260)
        flyDeal(List(HokmRules.HAKEM_FIRST_DEAL) { hakem }, counts)
        // Not animating any more: the hakem must be able to choose while the deck waits.
        val keepDeck = playerCount >= 3
        _state.value = _state.value.copy(
            dealReveal = null,
            dealFlights = emptyList(),
            handReveal = if (keepDeck) counts.toList() else null,
            dealStock = if (keepDeck) (deckTotal() - counts.sum()).coerceAtLeast(0) else null,
        )
    }

    /** Waits (briefly) until the view shows the fully dealt hand, then gives up quietly. */
    private suspend fun awaitDealtHands() {
        if (playerCount < 3) return
        var waited = 0
        while (_state.value.handPhase == HandPhase.CHOOSING_HOKM && waited < 1_500) {
            delay(50)
            waited += 50
            refresh()
        }
    }

    /**
     * Deals everything the engine handed out at once, card by card, in the real order.
     *
     * The snapshot arrives with thirteen cards per seat; this walks [HokmRules.dealOrder] and lifts
     * a per-seat curtain one card per beat, so the table shows five to everybody else, then rounds
     * of four — the same order a person would deal.
     */
    private suspend fun dealTheRest() {
        val n = playerCount
        if (n < 3) return
        val hakem = _state.value.hakem
        val order = HokmRules.dealOrder(players = n, hakem = hakem)
        if (order.isEmpty()) return
        // Continue from the five the hakem already picked up: hands only ever grow, never shrink
        // back to zero when the trump is named.
        val start = _state.value.handReveal?.takeIf { it.size == n }
            ?: List(n) { seat -> if (seat == hakem) HokmRules.HAKEM_FIRST_DEAL else 0 }
        val counts = start.toMutableList()
        onSound("shuffle")
        _state.value = _state.value.copy(dealFlights = emptyList())
        publishDeal(counts.toList(), deckTotal() - counts.sum(), animating = true)
        delay(240)
        flyDeal(order, counts)
        // The deck is empty and every hand is full.
        _state.value = _state.value.copy(
            dealReveal = null,
            handReveal = null,
            dealStock = null,
            dealFlights = emptyList(),
        )
        delay(160)
    }

    protected suspend fun loop() {
        while (scope.isActive) {
            // 1) Queued animations run before anything else moves, one step at a time (HK-010).
            //    A burst therefore plays in order instead of overwriting a single pending slot.
            animQueue.poll()?.let { step ->
                runStep(step)
                continue
            }
            if (isFinished()) {
                refresh()
                return
            }
            if (_state.value.introShown != null || _state.value.dealing) {
                wake.receive()
                continue
            }

            // 2) Whose move?
            val s = _state.value
            val actor = if (s.handPhase == HandPhase.CHOOSING_HOKM) s.hakem else s.turn
            if (actor !in 0 until playerCount) {
                wake.receive()
                continue
            }
            if (actor == mySeat) {
                if (s.handPhase == HandPhase.PLAYING) onSound("your_turn")
                wake.receive()
                continue
            }

            // 3) Someone else's move: locally that is a bot (with a human-like pause); in a room the
            //    host drives it and we simply wait for the next event.
            if (!botStep(s, actor)) {
                wake.receive()
            }
        }
    }

    /**
     * Plays one queued presentation step and returns when it is over. The steps are deliberately
     * sequential: a trick is shown, swept, then the hand banner appears — never two at once.
     */
    private suspend fun runStep(step: TableStep) {
        when (step) {
            is TableStep.CollectTrick -> {
                // Hold the four cards on screen before the sweep: a snapshot read must not empty
                // the table while the player is still looking at the finished trick.
                _state.value = _state.value.copy(holdingTrick = true)
                delay(TRICK_SHOW_MS)
                _state.value = _state.value.copy(collectingTo = step.winner)
                onSound("whoosh")
                val mine = HokmRules.teamOf(step.winner, playerCount) == HokmRules.teamOf(mySeat, playerCount)
                onSound(if (mine) "trick_won" else "trick_lost")
                pushFx(FxKind.TRICK_WIN, step.winner, 900)
                delay(COLLECT_MS)
                _state.value = _state.value.copy(
                    tableCards = emptyList(),
                    collectingTo = null,
                    holdingTrick = false,
                )
            }
            is TableStep.ShowBanner -> {
                _state.value = _state.value.copy(banner = step.banner)
                delay(step.holdMs)
                _state.value = _state.value.copy(banner = null)
            }
            is TableStep.Deal -> {
                _state.value = _state.value.copy(dealing = true)
                // The hands are already in the snapshot: read it first, then reveal it card by card.
                // In a room the full hands arrive with the same burst as the trump; give that
                // snapshot a beat so the deal is not animated against the five-card view.
                awaitDealtHands()
                refresh()
                dealTheRest()
                _state.value = _state.value.copy(dealing = false)
            }
        }
        refresh()
    }

    /** Queues a presentation step for the hand the table is showing right now. */
    private fun enqueue(keyHand: Int = _state.value.handNumber, block: (Int, Int, Long) -> TableStep) {
        val s = _state.value
        animQueue.offer(block(keyHand, s.trickNumber, s.revision))
    }

    protected fun onResult(r: ApplyResult, actor: Int) {
        when (r) {
            is ApplyResult.Rejected -> {
                // My move never happened: clear the sending gate so I can try again.
                _state.value = _state.value.copy(isSendingMove = false)
                setError(r.messageFa)
            }
            is ApplyResult.Ok -> {
                handleEmissions(r.emissions, actor)
                // The engine answered (accepted) — no longer waiting on this move. The snapshot that
                // carries the new revision is read below when the table is calm.
                _state.value = _state.value.copy(isSendingMove = false)
                // While a trick/hand animation is queued the table keeps showing the old state;
                // the loop refreshes once the animation is over.
                if (!hasQueuedAnimation) refresh()
            }
        }
    }

    protected fun handleEmissions(emissions: List<Emission>, actor: Int) {
        val feed = ArrayList<String>()
        for (e in emissions) {
            when (e) {
                is Emission.Sound -> when (e.name) {
                    // Voiced from the card itself, in the Public branch below: the sound event may
                    // arrive in a different batch from the card it belongs to (rooms deliver them
                    // one by one), so a flag carried over from an earlier card decided the wrong
                    // sound and a trump could land as a quiet tick instead of the slab.
                    "card_played" -> Unit
                    "hokm_chosen" -> onSound("hokm_chosen")
                    "trick_won" -> Unit // voiced when the collect animation starts
                    "shuffle" -> Unit // voiced when the deal animation starts
                    "hand_won", "kot", "hakem_kot", "game_won" -> Unit // voiced with the banner below
                    else -> onSound(e.name)
                }

                is Emission.Public -> when (e.type) {
                    "card_played" -> {
                        val seat = e.payload["seat"]!!.jsonPrimitive.int
                        val card = Card.parse(e.payload["card"]!!.jsonPrimitive.content)!!
                        val cur = _state.value
                        // A duplicated delivery must not put the same card on the table twice.
                        val update = TableEvents.appendCard(cur.tableCards, Play(seat, card))
                        if (!update.duplicate) {
                            val lead = cur.tableCards.firstOrNull()?.card?.suit
                            val hokm = cur.hokm
                            val trump = hokm != null && card.suit == hokm
                            val cut = trump && lead != null && lead != hokm
                            if (cut) {
                                pushFx(FxKind.CUT, seat)
                                feed += "${seats[seat].name} با حکم برید!"
                            }
                            // Every trump — led, cut, or following a trump — makes the ace's slab,
                            // whoever plays it and whichever batch the event arrived in.
                            onSound(TableAudio.cardLands(trump = trump, cut = cut))
                            _state.value = cur.copy(
                                tableCards = update.plays,
                                myHand = if (seat == mySeat) cur.myHand - card else cur.myHand,
                                legalCards = if (seat == mySeat) emptySet() else cur.legalCards,
                                // While a closed trick sits on the table nobody is "on turn".
                                turn = TableEvents.turnAfter(update, Play(seat, card), playerCount) ?: cur.turn,
                            )
                        }
                    }

                    "trick_won" -> {
                        val winner = e.payload["winner"]!!.jsonPrimitive.int
                        val trick = (e.payload["trickNumber"] as? JsonPrimitive)?.intOrNull ?: _state.value.trickNumber
                        val hand = _state.value.handNumber
                        val revision = _state.value.revision
                        animQueue.offer(TableStep.CollectTrick(winner, hand, trick, revision))
                        feed += "${seats[winner].name} دست را برد"
                    }

                    "hand_finished" -> {
                        TableSnapshotMapper.parseHand(e.payload)?.let { summary ->
                            // Presentation only: the summary says what to show, never what the match
                            // result is. Scores/winner still come from a validated snapshot.
                            val myTeam = HokmRules.teamOf(mySeat, playerCount)
                            val banner = TableEvents.handBanner(summary, playerCount, myTeam, ::teamName)
                            // The banner reports the hand that just ended, but it is shown while the
                            // next one is already on the table: tag it with the new hand number, or
                            // `dropBeforeHand` in refresh() throws it away before it is ever played.
                            val hand = TableEvents.bannerHand(summary)
                            val revision = _state.value.revision
                            animQueue.offer(
                                TableStep.ShowBanner(
                                    banner = banner,
                                    holdMs = HAND_BANNER_MS,
                                    handNumber = hand,
                                    trickNumber = _state.value.trickNumber,
                                    revision = revision,
                                ),
                            )
                            // A repeated hand_finished delivery must not list the hand twice.
                            val history = _state.value.handHistory
                            if (history.none { it.handNumber == summary.handNumber }) {
                                _state.value = _state.value.copy(handHistory = history + summary)
                            }
                            if (summary.kind == HandWinKind.KOT || summary.kind == HandWinKind.HAKEM_KOT) {
                                scope.launch { delay(TRICK_SHOW_MS + COLLECT_MS); pushFx(FxKind.KOT, -1, 2600) }
                            }
                            val sound = TableEvents.handSound(summary, myTeam)
                            scope.launch { delay(TRICK_SHOW_MS + COLLECT_MS); onSound(sound) }
                            botReactions(summary)
                            feed += banner.title
                        }
                    }

                    "card_drawn" -> {
                        val seat = e.payload["seat"]!!.jsonPrimitive.int
                        val kept = e.payload["kept"]!!.jsonPrimitive.content.toBoolean()
                        if (seat != mySeat) feed += "${seats[seat].name} کارت را ${if (kept) "برداشت" else "رد کرد"}"
                    }

                    "discarded" -> {
                        val seat = e.payload["seat"]!!.jsonPrimitive.int
                        onSound(TableAudio.PLAY)
                        feed += "${seats[seat].name} ${e.payload["count"]!!.jsonPrimitive.int.toFaDigitsLocal()} کارت سوزاند"
                    }

                    "drawing_finished" -> {
                        enqueue { hand, trick, revision ->
                            TableStep.ShowBanner(
                                Banner("دست‌ها کامل شد", "حاکم شروع می‌کند"),
                                holdMs = 1300L,
                                handNumber = hand,
                                trickNumber = trick,
                                revision = revision,
                            )
                        }
                    }

                    "hand_started" -> Unit // the new hakem's five cards simply appear with the next refresh

                    "game_finished" -> {
                        val winner = e.payload["winnerTeam"]!!.jsonPrimitive.int
                        val mine = winner == HokmRules.teamOf(mySeat, playerCount)
                        scope.launch {
                            delay(TRICK_SHOW_MS + COLLECT_MS + 600)
                            onSound(if (mine) "game_won" else "game_lost")
                            if (mine) pushFx(FxKind.CONFETTI, -1, 4000)
                        }
                    }

                    "hokm_chosen" -> {
                        val hakem = e.payload["hakem"]!!.jsonPrimitive.int
                        val suit = Suit.parse(e.payload["hokm"]!!.jsonPrimitive.content)!!
                        feed += "${seats[hakem].name} حکم را ${suit.titleFa} کرد"
                        if (actor != mySeat) {
                            // HK-010: the banner goes through the queue (burst-safe, duplicate-safe)
                            // and keeps our vector suit pip instead of a font-dependent glyph.
                            enqueue { hand, trick, revision ->
                                TableStep.ShowBanner(
                                    banner = Banner(
                                        title = "حکم: ${suit.titleFa}",
                                        subtitle = "به انتخاب ${seats[hakem].name}",
                                        suit = suit,
                                    ),
                                    holdMs = 1300L,
                                    handNumber = hand,
                                    trickNumber = trick,
                                    revision = revision,
                                )
                            }
                        }
                        // The trump card lands on the baize: the same slab the ace makes.
                        onSound(TableAudio.SLAM)
                        _state.value = _state.value.copy(trumpReveal = suit)
                        scope.launch {
                            delay(1600)
                            if (_state.value.trumpReveal == suit) _state.value = _state.value.copy(trumpReveal = null)
                        }
                        // 3p/4p: the remaining cards are dealt out — animate it before showing them.
                        if (playerCount > 2) {
                            enqueue { hand, trick, revision ->
                                TableStep.Deal(handNumber = hand, trickNumber = trick, revision = revision)
                            }
                        }
                    }
                }

                is Emission.Feed, is Emission.Private -> Unit
            }
        }
        if (feed.isNotEmpty()) {
            _state.value = _state.value.copy(feed = (_state.value.feed + feed).takeLast(6))
        }
    }

    private fun teamName(team: Int): String = when {
        playerCount == 4 -> if (team == HokmRules.teamOf(mySeat, playerCount)) "تیم ما" else "تیم حریف"
        team == mySeat -> "شما"
        else -> seats[team].name
    }

    private fun Int.toFaDigitsLocal(): String = this.toFa()

    protected fun setError(message: String) {
        _state.value = _state.value.copy(error = message, isSendingMove = false)
    }

    // ------------------------------------------------------------------ view → state

    /**
     * Re-reads the human's private view through the typed mapper (HK-009).
     *
     * The screen never sees anything beyond this. Refusals are explicit: an absent view, a stale
     * revision or a view this build cannot trust leaves the table exactly as it was, instead of
     * crashing on `!!` or drawing a half-parsed table. While the table is calm the snapshot also
     * restores the cards of a half-played trick (risk R-06).
     */
    protected fun refresh() {
        val old = _state.value
        val decision = TableSnapshotMapper.decide(
            view = privateView(),
            currentRevision = old.revision,
            expectedSeat = old.mySeat,
            expectedPlayers = if (seats.isNotEmpty()) seats.size else null,
        )
        val snapshot = (decision as? TableDecision.Apply)?.snapshot ?: return
        // A newer snapshot is the authoritative answer to whatever I sent, and it also tells the
        // queue that an older hand is history: never animate two hands at once (HK-010).
        animQueue.dropBeforeHand(snapshot.handNumber)
        val finished = snapshot.finished
        val next = old.copy(
            revision = snapshot.revision,
            playerCount = snapshot.playerCount,
            mySeat = if (snapshot.mySeat >= 0) snapshot.mySeat else old.mySeat,
            phase = if (finished) Phase.FINISHED else Phase.PLAYING,
            handPhase = snapshot.handPhase,
            handNumber = snapshot.handNumber,
            hakem = snapshot.hakem,
            hokm = snapshot.hokm,
            turn = snapshot.turn,
            trickNumber = snapshot.trickNumber,
            tricksWon = snapshot.tricksWon,
            scores = snapshot.scores,
            targetPoints = snapshot.targetPoints,
            handCounts = snapshot.handCounts,
            myHand = snapshot.myHand,
            legalCards = snapshot.legalCards,
            revealed = snapshot.revealed,
            discardCount = snapshot.discardCount,
            stockCount = snapshot.stockCount,
            hakemDraw = snapshot.hakemDraw,
            lastHand = snapshot.lastHand,
            lastTrick = snapshot.lastTrick,
            lastTrickWinner = snapshot.lastTrickWinner,
            finished = finished,
            winnerTeam = if (finished) snapshot.scores.indexOf(snapshot.scores.max()) else null,
            selectedForDiscard = if (snapshot.handPhase == HandPhase.DISCARDING) old.selectedForDiscard else emptySet(),
            // Only a newer revision is the answer to my move; re-reading the same view is not.
            isSendingMove = TableEvents.sendingAfterSnapshot(old.revision, snapshot.revision, old.isSendingMove),
        )
        // A fresh hand starts the way a real one does: five cards to the hakem, the rest of the
        // deck still on the table. Without this the chairs flash a full thirteen before the deal
        // runs and the same cards are dealt a second time.
        val dealt = if (next.handNumber != old.handNumber) {
            val opening = next.playerCount >= 3 && next.handPhase == HandPhase.CHOOSING_HOKM
            next.copy(
                dealFlights = emptyList(),
                handReveal = if (opening) {
                    List(next.playerCount) { seat -> if (seat == next.hakem) HokmRules.HAKEM_FIRST_DEAL else 0 }
                } else null,
                dealStock = if (opening) {
                    (next.playerCount * HokmRules.cardsPerPlayer(next.playerCount) -
                        HokmRules.HAKEM_FIRST_DEAL).coerceAtLeast(0)
                } else null,
            )
        } else {
            next
        }
        _state.value = dealt.copy(tableCards = TableSync.tableCards(snapshot, dealt.tableCards, dealt.isCalm))
    }
}
