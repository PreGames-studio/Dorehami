package ir.dorehami.games.hokm.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.view
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.HokmRules
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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** The table's model types live in [HokmUiModel.kt] (pure Kotlin, JVM-testable). */


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
        /**
         * GX-04 keeps only the *bot's* human-like thinking pace here: it is not a movement of the
         * table, so it does not belong to [HokmChoreography]. Every card movement's length now
         * comes from that table ([beatMs]).
         */
        const val BOT_THINK_MS = 650L
        const val BOT_LEAD_MS = 900L

        /**
         * GX-04: how deep the notice queue may get before the felt stops waiting out its movements.
         * A queue this deep means truth is already ahead (a burst, a reconnect resync); the
         * in-flight movement is then fast-forwarded — its cues still fire exactly once — so the
         * table never holds the game behind an animation (the roadmap's BASELINE criterion).
         */
        const val SKIP_AHEAD_QUEUE = 4
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
    protected var pendingPauseMs = 0L
    private var pendingCollect: Int? = null
    private var pendingHandBanner: Banner? = null

    /** GX-04: the sound the coming hand banner must speak (kot/hand_lost/hand_won), set on finish. */
    private var pendingHandSound: String? = null
    private var pendingDeal = false
    private var started = false

    /**
     * Versioned FIFO of animation notices (HK-010). Actions enqueue their emissions here; the
     * loop drains one notice at a time so bursts can never interleave, and duplicate delivery is
     * refused before any sound can fire twice.
     */
    protected val eventQueue = HokmEventQueue()

    /** Enqueues one action's emissions as a notice and wakes the loop. */
    protected fun offerNotice(version: Long, actor: Int, emissions: List<Emission>) {
        if (eventQueue.offer(Notice(version, actor, emissions))) wake.trySend(Unit)
    }

    // ------------------------------------------------------------------ GX-04 choreography

    /**
     * GX-04: the player's reduced-motion choice. The table has no Compose access on purpose, so
     * the screen (which owns the system-setting hook) keeps this flag in sync. Every movement is
     * read through [beat]/[beatMs] so exactly one table rules the pacing.
     */
    var reducedMotion: Boolean = false

    /** The row that actually plays for [event] right now (reduced-motion variant included). */
    protected fun beat(event: HokmChoreography.Event): HokmChoreography.Beat =
        HokmChoreography.beat(event, reducedMotion)

    /** How long [event] plays right now, as the loop wants it. */
    protected fun beatMs(event: HokmChoreography.Event): Long = beat(event).totalMs.toLong()

    /** The movement currently in flight; the loop fires its cues at their offsets (never twice). */
    private var cueRunner: HokmCueRunner? = null

    /** The seat the moving cards belong to (a cut's sparks, a collected trick). */
    private var cueSeat: Int = -1

    private var hapticStamp = 0L

    /**
     * Starts a movement. Its cues fire from the loop (see [runPendingCues]) or inline when the
     * caller is a linear script ([runBeat]) — either way the truth is never gated on it: hand,
     * turn and scores still come from snapshots, and a skipped movement is *heard*, not dropped.
     */
    private fun startBeat(
        event: HokmChoreography.Event,
        seat: Int = -1,
        rename: (HokmChoreography.Beat) -> HokmChoreography.Beat = { it },
    ) {
        cueRunner = HokmCueRunner(rename(beat(event)))
        cueSeat = seat
    }

    /** True while a movement is in flight; the loop services its cues before anything else. */
    private val hasPendingCues: Boolean get() = cueRunner?.let { !it.finished } ?: false

    /**
     * One turn of a movement: waits out the movement's own clock and fires whatever became due.
     * Writing this as a wait-then-fire (instead of a single `delay(totalMs)`) is what puts a card's
     * click on the frame the card lands, and keeps the loop able to service truth between cues.
     */
    private suspend fun runPendingCues() {
        val runner = cueRunner ?: return
        val wait = runner.waitMs() ?: run { cueRunner = null; return }
        if (wait > 0L) delay(wait)
        runner.due(wait).forEach { fireCue(it, cueSeat) }
        if (runner.finished) cueRunner = null
    }

    /**
     * GX-04: fast-forwards the movement in flight — the player still *hears* it (every pending cue
     * fires once), but the table does not wait for it. Used when the notice queue proves the felt
     * is behind the truth, so a burst or a resync can never make the game wait on an animation.
     */
    private fun finishPendingCues() {
        val runner = cueRunner ?: return
        runner.finish().forEach { fireCue(it, cueSeat) }
        cueRunner = null
    }

    /** Plays a whole movement inline (the opening script has nothing else to do meanwhile). */
    protected suspend fun runBeat(
        event: HokmChoreography.Event,
        seat: Int = -1,
        rename: (HokmChoreography.Beat) -> HokmChoreography.Beat = { it },
    ) {
        val runner = HokmCueRunner(rename(beat(event)))
        while (true) {
            val wait = runner.waitMs() ?: break
            if (wait > 0L) delay(wait)
            runner.due(wait).forEach { fireCue(it, seat) }
        }
    }

    /**
     * Turns a cue into the thing the player perceives. SOUND goes to the sound player, HAPTIC is
     * *published* in the state for the screen to buzz (the table owns no Android API), FX schedules
     * the particle burst at the seat the movement belongs to.
     */
    private fun fireCue(cue: HokmChoreography.Cue, seat: Int) {
        when (cue.kind) {
            HokmChoreography.CueKind.SOUND -> onSound(cue.name)
            HokmChoreography.CueKind.HAPTIC -> {
                _state.value = _state.value.copy(hapticCue = TableHaptic(cue.name, ++hapticStamp))
            }
            HokmChoreography.CueKind.FX -> when (cue.name) {
                "cut" -> pushFx(FxKind.CUT, seat, HokmChoreography.Fx.CUT_MS.toLong())
                "trick_win" -> pushFx(FxKind.TRICK_WIN, seat, HokmChoreography.Fx.TRICK_WIN_MS.toLong())
                "confetti" -> pushFx(FxKind.CONFETTI, -1, HokmChoreography.Fx.CONFETTI_MS.toLong())
                else -> Unit // an unknown fx id is a table bug the tests already refuse
            }
        }
    }

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
            delay(beatMs(HokmChoreography.Event.TRICK_SHOW) + beatMs(HokmChoreography.Event.COLLECT) + 300)
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
            // GX-04: one beat per flipped card; the deal-click fires at the beat's own cue.
            runBeat(HokmChoreography.Event.HAKEM_DRAW)
        }
        val hakem = _state.value.hakem
        _state.value = _state.value.copy(
            banner = Banner("${seats[hakem].name} حاکم شد", "اولین آس را آورد", big = true),
        )
        runBeat(HokmChoreography.Event.HAKEM_BANNER)
        // Hakem receives the first five cards; the rest is dealt after hokm is named.
        onSound("card_deal")
        _state.value = _state.value.copy(introShown = null, banner = null)
        refresh()
        wake.trySend(Unit)
    }

    protected suspend fun loop() {
        while (scope.isActive) {
            // 1) Animations requested by the last action run before anything else moves. The cycle
            //    runs whenever any of its pieces is pending — a banner or deal can arrive in its
            //    own notice (rooms fan out one emission per notice) and must not be stranded.
            if (pendingPauseMs > 0L || pendingCollect != null || pendingHandBanner != null || pendingDeal || hasPendingCues) {
                val ms = pendingPauseMs
                pendingPauseMs = 0L
                if (ms > 0L) delay(ms)
                // GX-04: a movement already in flight gets its turn first, so a cue lands on the
                // frame it belongs to (a card's click on contact) instead of behind the next move.
                // If the queue is deep the felt is already behind the truth: fast-forward.
                if (hasPendingCues && eventQueue.size > SKIP_AHEAD_QUEUE) finishPendingCues()
                else if (hasPendingCues) runPendingCues()
                pendingCollect?.let { winner ->
                    pendingCollect = null
                    _state.value = _state.value.copy(collectingTo = winner)
                    // The sweep is one beat: whoosh at 0, the result sound and the spark a hair
                    // later, and the felt is released when the beat ends (never before).
                    runBeat(
                        HokmChoreography.Event.COLLECT,
                        seat = winner,
                        rename = { row ->
                            val won = HokmRules.teamOf(winner, playerCount) == HokmRules.teamOf(mySeat, playerCount)
                            row.withSound("collect.result", if (won) "trick_won" else "trick_lost")
                        },
                    )
                    _state.value = HokmPacing.onTrickCollected(_state.value)
                    refresh()
                }
                pendingHandBanner?.let { banner ->
                    pendingHandBanner = null
                    _state.value = _state.value.copy(banner = banner)
                    if (banner.big) {
                        // The hand banner's own cue speaks the result (kot / lost / won) once, at
                        // the banner's offset — no background coroutine racing the banner itself.
                        val sound = pendingHandSound ?: "hand_won"
                        pendingHandSound = null
                        runBeat(HokmChoreography.Event.HAND_BANNER, rename = { it.withSound("banner.in", sound) })
                    } else {
                        pendingHandSound = null
                        runBeat(HokmChoreography.Event.BANNER)
                    }
                    _state.value = _state.value.copy(banner = null)
                }
                if (pendingDeal) {
                    pendingDeal = false
                    _state.value = _state.value.copy(dealing = true)
                    // GX-04: shuffle and the cards coming out are two cues of one deal beat.
                    runBeat(HokmChoreography.Event.DEAL)
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

            // 2) Drain the versioned notice queue one notice at a time (HK-010). Effects play at
            //    their own pace; the loop never runs a move while animation work is pending. A
            //    refresh follows every notice so truth (hand/turn) stays current mid-burst — the
            //    merge only rebuilds the felt when the queue has emptied (see [HokmUiMapper.merge]).
            val notice = eventQueue.poll()
            if (notice != null) {
                handleEmissions(notice.emissions, notice.actor)
                refresh()
                continue
            }

            // 3) Whose move?
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

            // 4) Someone else's move: locally that is a bot (with a human-like pause); in a room the
            //    host drives it and we simply wait for the next event.
            if (!botStep(s, actor)) {
                wake.receive()
            }
        }
    }

    /**
     * An action completed ([r] from [actor] at the session `version` it produced). Ok results are
     * enqueued as versioned notices — never applied inline — so a burst of actions animates one
     * at a time and a redelivered action is refused before its sounds can double (HK-010).
     */
    protected fun onResult(r: ApplyResult, actor: Int, version: Long) {
        when (r) {
            is ApplyResult.Rejected -> setError(r.messageFa)
            is ApplyResult.Ok -> offerNotice(version, actor, r.emissions)
        }
    }

    protected fun handleEmissions(emissions: List<Emission>, actor: Int) {
        val feed = ArrayList<String>()
        for (e in emissions) {
            when (e) {
                is Emission.Sound -> when (e.name) {
                    // GX-04: the throw's own cues speak (slide now, click on contact, `card_cut`
                    // instead of `card_play` when the throw is a cut) — see the Public branch below.
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
                        val lead = cur.tableCards.firstOrNull()?.card?.suit
                        val hokm = cur.hokm
                        // `بریدن`: the same throw, a snappier click and the gold spark. The beat is
                        // built from the choreography either way — a cut never plays two clicks.
                        val cutting = lead != null && hokm != null && card.suit == hokm && lead != hokm
                        if (cutting) feed += "${seats[seat].name} با حکم برید!"
                        startBeat(HokmChoreography.Event.PLAY, seat) { throwBeat ->
                            if (cutting) throwBeat.withSound("play.contact", "card_cut")
                            else throwBeat.withoutCues("play.cut", "play.cut.haptic")
                        }
                        // Presentation never writes results (HK-010): only the felt display is
                        // updated here, idempotently. myHand/legalCards/turn come from the next
                        // snapshot merge — a re-delivered card cannot duplicate or lose a card.
                        val trickNumber = e.payload["trickNumber"]?.jsonPrimitive?.int ?: cur.tableTrickNumber
                        _state.value = HokmPacing.onCardPlayed(cur, seat, card, trickNumber)
                    }

                    "trick_won" -> {
                        val winner = e.payload["winner"]!!.jsonPrimitive.int
                        val before = _state.value
                        val after = HokmPacing.onTrickWon(before, winner)
                        _state.value = after
                        // Only a sweep that actually owns the felt schedules the collect cycle;
                        // superseded/stale wins change nothing.
                        if (after.sweepOf != before.sweepOf) {
                            pendingCollect = winner
                            pendingPauseMs = maxOf(pendingPauseMs, beatMs(HokmChoreography.Event.TRICK_SHOW))
                            feed += "${seats[winner].name} ترفند را برد"
                        }
                    }

                    "hand_finished" -> {
                        val summary = HokmUiMapper.handSummary(e.payload)
                        if (summary == null) {
                            feed += "نتیجهٔ دست خوانده نشد"
                            continue
                        }
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
                        _state.value = HokmPacing.recordHand(_state.value, summary)
                        if (summary.kind == HandWinKind.KOT || summary.kind == HandWinKind.HAKEM_KOT) {
                            scope.launch {
                                delay(beatMs(HokmChoreography.Event.TRICK_SHOW) + beatMs(HokmChoreography.Event.COLLECT))
                                pushFx(FxKind.KOT, -1, HokmChoreography.Fx.KOT_MS.toLong())
                            }
                        }
                        // GX-04: the result sound is the coming banner's own cue (offset inside the
                        // beat) instead of a coroutine guessing when the banner will appear.
                        pendingHandSound = when (summary.kind) {
                            HandWinKind.NORMAL, HandWinKind.VOID -> if (mine) "hand_won" else "hand_lost"
                            else -> if (mine) "kot" else "hand_lost"
                        }
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
                        scope.launch {
                            // Let the last trick finish, then the match banner beat speaks the
                            // result (game_won/game_lost) and the celebration burst is scheduled.
                            delay(
                                beatMs(HokmChoreography.Event.TRICK_SHOW) +
                                    beatMs(HokmChoreography.Event.COLLECT) + 600L,
                            )
                            val banner = HokmChoreography.beat(HokmChoreography.Event.MATCH_BANNER)
                                .withSound("match.in", if (mine) "game_won" else "game_lost")
                            val runner = HokmCueRunner(banner)
                            while (true) {
                                val wait = runner.waitMs() ?: break
                                if (wait > 0L) delay(wait)
                                runner.due(wait).forEach { fireCue(it, -1) }
                            }
                        }
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

    /**
     * Re-reads the human's private view through the typed [HokmUiMapper] boundary. Incomplete or
     * inconsistent views are refused (the table keeps its previous state) and stale views are
     * ignored by [HokmUiMapper.merge]. The screen never sees anything beyond this.
     *
     * HK-010: while notices are still queued, the merge updates truth only — the felt keeps being
     * built by the notice stream (no card is dropped or doubled by a mid-burst resync). The felt
     * is rebuilt from the snapshot once the queue has emptied.
     */
    protected fun refresh() {
        val v = privateView() ?: return
        when (val mapped = HokmUiMapper.map(v)) {
            is MappedView.Rejected -> setError(mapped.messageFa)
            is MappedView.Valid -> _state.value = HokmUiMapper.merge(_state.value, mapped.snapshot, feltSync = eventQueue.isEmpty)
        }
    }
}
