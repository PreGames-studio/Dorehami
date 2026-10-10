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
import ir.dorehami.games.hokm.HokmSession
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
data class Banner(val title: String, val subtitle: String? = null, val big: Boolean = false)

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
    val lastHand: HandSummary? = null,
    val banner: Banner? = null,
    val hakemDraw: List<Play> = emptyList(),
    /** How many of [hakemDraw] are face-up during the opening animation (`null` = intro finished). */
    val introShown: Int? = null,
    val dealing: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
    val winnerTeam: Int? = null,
    val feed: List<String> = emptyList(),
) {
    val myTeam: Int get() = HokmRules.teamOf(mySeat, playerCount)
    fun teamOf(seat: Int): Int = HokmRules.teamOf(seat, playerCount)
    private val calm: Boolean get() = !finished && introShown == null && collectingTo == null && !dealing && banner == null
    val isMyTurn: Boolean get() = calm && handPhase == HandPhase.PLAYING && turn == mySeat
    val iChooseHokm: Boolean get() = calm && handPhase == HandPhase.CHOOSING_HOKM && hakem == mySeat
    val iDiscard: Boolean get() = calm && handPhase == HandPhase.DISCARDING && turn == mySeat && discardCount > 0
    val iDraw: Boolean get() = calm && handPhase == HandPhase.DRAWING && turn == mySeat && revealed != null
    val isTeamGame: Boolean get() = playerCount == 4
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
    protected companion object {
        const val BOT_THINK_MS = 650L
        const val BOT_LEAD_MS = 900L
        const val TRICK_SHOW_MS = 1150L
        const val COLLECT_MS = 520L
        const val INTRO_CARD_MS = 420L
        const val DEAL_MS = 1400L
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
    val state: State<HokmUiState> get() = _state

    protected val wake = Channel<Unit>(Channel.CONFLATED)
    protected var pendingPauseMs = 0L
    private var pendingCollect: Int? = null
    private var pendingHandBanner: Banner? = null
    private var pendingDeal = false
    private var started = false

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
        sendMine(GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put(HokmModule.PAYLOAD_SUIT, suit.id) }))
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
        sendMine(GameAction(HokmModule.ACTION_PLAY_CARD, view { put(HokmModule.PAYLOAD_CARD, card.code) }))
        wake.trySend(Unit)
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
        sendMine(GameAction(HokmModule.ACTION_DISCARD, payload))
        wake.trySend(Unit)
    }

    fun keepCard() = drawDecision(keep = true)

    fun passCard() = drawDecision(keep = false)

    private fun drawDecision(keep: Boolean) {
        if (!_state.value.iDraw) return
        sendMine(GameAction(if (keep) HokmModule.ACTION_KEEP_CARD else HokmModule.ACTION_PASS_CARD))
        wake.trySend(Unit)
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
            onSound("card_deal")
            delay(INTRO_CARD_MS)
        }
        val hakem = _state.value.hakem
        _state.value = _state.value.copy(
            banner = Banner("${seats[hakem].name} حاکم شد", "اولین آس را آورد", big = true),
        )
        onSound("hokm_chosen")
        delay(1500)
        // Hakem receives the first five cards; the rest is dealt after hokm is named.
        onSound("card_deal")
        _state.value = _state.value.copy(introShown = null, banner = null)
        refresh()
        wake.trySend(Unit)
    }

    protected suspend fun loop() {
        while (scope.isActive) {
            // 1) Animations requested by the last action run before anything else moves.
            if (pendingPauseMs > 0L) {
                val ms = pendingPauseMs
                pendingPauseMs = 0L
                delay(ms)
                pendingCollect?.let { winner ->
                    pendingCollect = null
                    _state.value = _state.value.copy(collectingTo = winner)
                    onSound(if (HokmRules.teamOf(winner, playerCount) == HokmRules.teamOf(mySeat, playerCount)) "trick_won" else "trick_lost")
                    delay(COLLECT_MS)
                    _state.value = _state.value.copy(tableCards = emptyList(), collectingTo = null)
                    refresh()
                }
                pendingHandBanner?.let { banner ->
                    pendingHandBanner = null
                    _state.value = _state.value.copy(banner = banner)
                    delay(if (banner.big) HAND_BANNER_MS else 1300L)
                    _state.value = _state.value.copy(banner = null)
                }
                if (pendingDeal) {
                    pendingDeal = false
                    _state.value = _state.value.copy(dealing = true)
                    onSound("shuffle")
                    delay(DEAL_MS)
                    _state.value = _state.value.copy(dealing = false)
                }
                refresh()
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

    protected fun onResult(r: ApplyResult, actor: Int) {
        when (r) {
            is ApplyResult.Rejected -> setError(r.messageFa)
            is ApplyResult.Ok -> {
                handleEmissions(r.emissions, actor)
                // While a trick/hand animation is pending the table keeps showing the old state;
                // the loop refreshes once the animation is over.
                if (pendingPauseMs == 0L) refresh()
            }
        }
    }

    protected fun handleEmissions(emissions: List<Emission>, actor: Int) {
        val feed = ArrayList<String>()
        for (e in emissions) {
            when (e) {
                is Emission.Sound -> when (e.name) {
                    "card_played" -> onSound("card_play")
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
                        _state.value = cur.copy(
                            tableCards = cur.tableCards + Play(seat, card),
                            myHand = if (seat == mySeat) cur.myHand - card else cur.myHand,
                            legalCards = if (seat == mySeat) emptySet() else cur.legalCards,
                            // While a closed trick sits on the table nobody is "on turn".
                            turn = if (cur.tableCards.size + 1 >= playerCount) -1 else HokmRules.nextSeat(seat, playerCount),
                        )
                    }

                    "trick_won" -> {
                        val winner = e.payload["winner"]!!.jsonPrimitive.int
                        pendingCollect = winner
                        pendingPauseMs = maxOf(pendingPauseMs, TRICK_SHOW_MS)
                        feed += "${seats[winner].name} دست را برد"
                    }

                    "hand_finished" -> {
                        val summary = parseHand(e.payload)
                        val myTeam = HokmRules.teamOf(mySeat, playerCount)
                        val mine = summary.winnerTeam == myTeam
                        val we = if (playerCount == 4) "بردیم" else "بردی"
                        val lost = if (playerCount == 4) "باختیم" else "باختی"
                        val title = when (summary.kind) {
                            HandWinKind.NORMAL -> if (mine) "این دست را $we!" else "این دست را $lost"
                            HandWinKind.KOT -> if (mine) "کُت!" else "کُت ${if (playerCount == 4) "شدیم" else "شدی"}!"
                            HandWinKind.HAKEM_KOT -> if (mine) "حاکم‌کُت! ${summary.points.toFaDigitsLocal()} امتیاز" else "حاکم‌کُت ${if (playerCount == 4) "شدیم" else "شدی"}…"
                            HandWinKind.VOID -> "مساوی — این دست بی‌نتیجه ماند"
                        }
                        val subtitle = summary.winnerTeam?.let { "${summary.points.toFaDigitsLocal()} امتیاز برای ${teamName(it)}" }
                        pendingHandBanner = Banner(title, subtitle, big = true)
                        val sound = when (summary.kind) {
                            HandWinKind.NORMAL, HandWinKind.VOID -> if (mine) "hand_won" else "hand_lost"
                            else -> if (mine) "kot" else "hand_lost"
                        }
                        scope.launch { delay(TRICK_SHOW_MS + COLLECT_MS); onSound(sound) }
                        botReactions(summary)
                        feed += title
                    }

                    "card_drawn" -> {
                        val seat = e.payload["seat"]!!.jsonPrimitive.int
                        val kept = e.payload["kept"]!!.jsonPrimitive.content.toBoolean()
                        if (seat != mySeat) feed += "${seats[seat].name} کارت را ${if (kept) "برداشت" else "رد کرد"}"
                    }

                    "discarded" -> {
                        val seat = e.payload["seat"]!!.jsonPrimitive.int
                        feed += "${seats[seat].name} ${e.payload["count"]!!.jsonPrimitive.int.toFaDigitsLocal()} کارت سوزاند"
                    }

                    "drawing_finished" -> {
                        pendingHandBanner = Banner("دست‌ها کامل شد", "حاکم شروع می‌کند")
                        pendingPauseMs = maxOf(pendingPauseMs, 150L)
                    }

                    "hand_started" -> Unit // the new hakem's five cards simply appear with the next refresh

                    "game_finished" -> {
                        val winner = e.payload["winnerTeam"]!!.jsonPrimitive.int
                        val mine = winner == HokmRules.teamOf(mySeat, playerCount)
                        scope.launch { delay(TRICK_SHOW_MS + COLLECT_MS + 600); onSound(if (mine) "game_won" else "game_lost") }
                    }

                    "hokm_chosen" -> {
                        val hakem = e.payload["hakem"]!!.jsonPrimitive.int
                        val suit = Suit.parse(e.payload["hokm"]!!.jsonPrimitive.content)!!
                        feed += "${seats[hakem].name} حکم را ${suit.titleFa} کرد"
                        if (actor != mySeat) {
                            pendingHandBanner = Banner("حکم: ${suit.titleFa} ${suit.symbol}", "به انتخاب ${seats[hakem].name}")
                        }
                        // 3p/4p: the remaining cards are dealt out — animate it before showing them.
                        if (playerCount > 2) pendingDeal = true
                        pendingPauseMs = maxOf(pendingPauseMs, 150L)
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
        _state.value = _state.value.copy(error = message)
    }

    // ------------------------------------------------------------------ view → state

    /** Re-reads the human's private view. The screen never sees anything beyond this. */
    protected fun refresh() {
        val v = privateView() ?: return
        val old = _state.value
        val tricks = v["tricksWon"]!!.jsonArray.map { it.jsonPrimitive.int }
        val scores = v["scores"]!!.jsonArray.map { it.jsonPrimitive.int }
        val finished = v["phase"]!!.jsonPrimitive.content == Phase.FINISHED.name
        _state.value = old.copy(
            phase = if (finished) Phase.FINISHED else Phase.PLAYING,
            handPhase = HandPhase.entries.firstOrNull { it.id == v["handPhase"]!!.jsonPrimitive.content } ?: HandPhase.CHOOSING_HOKM,
            handNumber = v["handNumber"]!!.jsonPrimitive.int,
            hakem = v["hakem"]!!.jsonPrimitive.int,
            hokm = v["hokm"]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let { Suit.parse(it) },
            turn = v["turn"]!!.jsonPrimitive.int,
            trickNumber = v["trickNumber"]!!.jsonPrimitive.int,
            tricksWon = tricks,
            scores = scores,
            targetPoints = v["targetPoints"]!!.jsonPrimitive.int,
            handCounts = v["handCounts"]!!.jsonArray.map { it.jsonPrimitive.int },
            myHand = v["hand"]!!.jsonArray.map { Card.parse(it.jsonPrimitive.content)!! },
            legalCards = v["legalCards"]!!.jsonArray.map { Card.parse(it.jsonPrimitive.content)!! }.toSet(),
            revealed = v[HokmSession.Keys.REVEALED]?.jsonPrimitive?.content?.takeIf { it != "null" }?.let { Card.parse(it) },
            discardCount = v[HokmSession.Keys.DISCARD_COUNT]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            stockCount = v["stockCount"]!!.jsonPrimitive.int,
            hakemDraw = v["hakemDraw"]!!.jsonArray.map { p ->
                val o = p.jsonObject
                Play(o["seat"]!!.jsonPrimitive.int, Card.parse(o["card"]!!.jsonPrimitive.content)!!)
            },
            lastHand = v["lastHandResult"]?.let { parseHand(it.jsonObject) },
            finished = finished,
            winnerTeam = if (finished) scores.indexOf(scores.max()) else null,
            selectedForDiscard = if (v["handPhase"]!!.jsonPrimitive.content == HandPhase.DISCARDING.id) old.selectedForDiscard else emptySet(),
        )
    }

    private fun parseHand(o: JsonObject) = HandSummary(
        handNumber = o["handNumber"]!!.jsonPrimitive.int,
        winnerTeam = o["winnerTeam"]!!.jsonPrimitive.content.toIntOrNull(),
        points = o["points"]!!.jsonPrimitive.int,
        kind = HandWinKind.entries.first { it.id == o["kind"]!!.jsonPrimitive.content },
        hakem = o["hakem"]!!.jsonPrimitive.int,
        tricks = o["tricks"]!!.jsonArray.map { it.jsonPrimitive.int },
    )
}
