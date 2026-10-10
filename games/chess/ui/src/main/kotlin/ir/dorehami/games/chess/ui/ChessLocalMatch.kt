package ir.dorehami.games.chess.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.chess.ChessModule
import ir.dorehami.games.chess.ChessSession
import ir.dorehami.games.chess.ui.gl.BoardScene
import ir.dorehami.games.chess.ui.gl.MoveAnim
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How the local match is played. */
enum class ChessMode { VS_BOT, PASS_AND_PLAY }

/** Colour the human asked for against the bot. */
enum class ColorChoice { WHITE, BLACK, RANDOM }

/** One side as drawn on the screen. */
data class ChessPlayerCard(val name: String, val side: Int, val isBot: Boolean)

/** The final result as the screen shows it. */
data class ChessOutcome(val title: String, val detail: String, val winnerSide: Int, val iWon: Boolean?)

/**
 * Everything the chess screen draws. Built **only** from a player's `privateView()` plus UI
 * bookkeeping (selection, animation id, dialogs) — the screen never touches the session.
 */
data class ChessUiState(
    val mode: ChessMode = ChessMode.VS_BOT,
    val view: TableView? = null,
    val white: ChessPlayerCard = ChessPlayerCard("سفید", Side.WHITE, false),
    val black: ChessPlayerCard = ChessPlayerCard("سیاه", Side.BLACK, false),
    /** Side the camera sits behind (and whose card is at the bottom). */
    val viewerSide: Int = Side.WHITE,
    val selected: Int = -1,
    val quietTargets: Set<Int> = emptySet(),
    val captureTargets: Set<Int> = emptySet(),
    val positionId: Long = 0,
    val anim: MoveAnim? = null,
    val promotion: TapResult.ChoosePromotion? = null,
    val botThinking: Boolean = false,
    val canUndo: Boolean = false,
    val outcome: ChessOutcome? = null,
    val error: String? = null,
    /** Pass-and-play: the colour that must answer a draw offer (shown as a dialog), else 0. */
    val drawQuestionFor: Int = 0,
    /** A short note shown above the board (e.g. "ربات پیشنهاد مساوی را رد کرد"). */
    val note: String? = null,
) {
    val bottom: ChessPlayerCard get() = if (viewerSide == Side.WHITE) white else black
    val top: ChessPlayerCard get() = if (viewerSide == Side.WHITE) black else white

    /** The 3D scene for the current state. */
    fun scene(): BoardScene {
        val v = view ?: return BoardScene(IntArray(64))
        return BoardScene(
            squares = v.squares,
            selected = selected,
            quietTargets = quietTargets,
            captureTargets = captureTargets,
            lastFrom = v.lastFrom,
            lastTo = v.lastTo,
            checkSquare = v.checkSquare,
            anim = anim,
            positionId = positionId,
        )
    }

    /** Whose actions the bottom bar offers (the human to move / the human). */
    val canOfferDraw: Boolean get() = view?.you?.actions?.contains(ChessModule.ACTION_OFFER_DRAW) == true && outcome == null && !botThinking
    val canResign: Boolean get() = view?.you?.actions?.contains(ChessModule.ACTION_RESIGN) == true && outcome == null
}

/**
 * Drives one local match: human vs the deterministic bot, or two humans passing one phone.
 *
 * It is the "owner of the room" for local play: the only thing that calls [GameEngine.submit].
 * Takebacks rebuild the engine with [GameEngine.replay] from a shortened record, so a match is
 * always exactly reproducible from its seed and action log.
 */
class ChessLocalMatch(
    private val scope: CoroutineScope,
    val mode: ChessMode,
    playerName: String,
    colorChoice: ColorChoice,
    private val botLevel: Int,
    private val autoRotate: Boolean,
    seed: Long,
    private val onSound: (String) -> Unit,
) {
    companion object {
        private const val BOT_MIN_THINK_MS = 420L
        private const val ROTATE_DELAY_MS = 650L
        private val HUMAN = PlayerId("p_me")
        private val BOT = PlayerId("p_bot")
        private val P1 = PlayerId("p_one")
        private val P2 = PlayerId("p_two")
    }

    private val players: List<PlayerId> = if (mode == ChessMode.VS_BOT) {
        // Seat 0 is white unless the colours are swapped: "black" = the bot sits first.
        if (colorChoice == ColorChoice.BLACK) listOf(BOT, HUMAN) else listOf(HUMAN, BOT)
    } else {
        listOf(P1, P2)
    }

    private val config = GameConfig(
        seed = seed,
        players = players,
        options = buildJsonObject {
            put(
                ChessModule.Options.COLORS,
                if (mode == ChessMode.VS_BOT && colorChoice == ColorChoice.RANDOM) ChessModule.Options.COLORS_RANDOM else ChessModule.Options.COLORS_FIRST_WHITE,
            )
            put(ChessModule.Options.BOT_LEVEL, botLevel)
        },
        clock = ManualClock(),
        rng = SplitMixRng(seed),
    )

    private var engine: GameEngine = GameEngine.start(ChessModule, config)
    private val session: ChessSession get() = engine.session as ChessSession

    private val names: Map<PlayerId, String> = mapOf(
        HUMAN to playerName.ifBlank { "شما" },
        BOT to "ربات ${ChessText.levelName(botLevel)}",
        P1 to "بازیکن ۱",
        P2 to "بازیکن ۲",
    )

    private val _state = mutableStateOf(ChessUiState(mode = mode))
    val state: State<ChessUiState> get() = _state

    private var positionCounter = 0L
    private var lastFen: String? = null
    private var lastPly = -1
    private var lastSquares: IntArray? = null
    private var botJob: Job? = null
    private var botPending = false
    private var rotateJob: Job? = null
    private var started = false

    /** Shows the first position and lets the bot move if it has white. */
    fun start() {
        if (started) return
        started = true
        val whiteId = session.playerOf(1)
        val viewer = if (mode == ChessMode.VS_BOT) (if (whiteId == HUMAN) Side.WHITE else Side.BLACK) else Side.WHITE
        _state.value = _state.value.copy(
            white = card(session.playerOf(1), Side.WHITE),
            black = card(session.playerOf(-1), Side.BLACK),
            viewerSide = viewer,
        )
        refresh()
        maybeBot()
    }

    /** The record of this match so far (for saving / verification later). */
    fun record() = engine.record()

    // ------------------------------------------------------------------ human input

    /** A tap on the 3D board ([square] = -1 when off the board). */
    fun onSquareTap(square: Int) {
        val s = _state.value
        val v = s.view ?: return
        if (s.botThinking || s.promotion != null || s.outcome != null) return
        when (val r = Selection.tap(v, s.selected, square)) {
            is TapResult.Select -> {
                onSound("select")
                _state.value = s.copy(selected = r.square, quietTargets = r.quietTargets, captureTargets = r.captureTargets, error = null)
            }
            TapResult.Deselect -> _state.value = s.copy(selected = -1, quietTargets = emptySet(), captureTargets = emptySet())
            is TapResult.Move -> playMove(r.uci)
            is TapResult.ChoosePromotion -> _state.value = s.copy(promotion = r)
            is TapResult.Rejected -> {
                onSound("illegal")
                _state.value = s.copy(error = r.messageFa)
            }
            TapResult.Ignore -> Unit
        }
    }

    /** Answer of the promotion dialog; `null` cancels. */
    fun choosePromotion(piece: Char?) {
        val p = _state.value.promotion ?: return
        _state.value = _state.value.copy(promotion = null)
        if (piece == null) return
        playMove(FenBoard.name(p.from) + FenBoard.name(p.to) + piece)
    }

    fun resign() {
        val who = activeHuman() ?: return
        cancelBot()
        submit(who, GameAction(ChessModule.ACTION_RESIGN))
    }

    fun offerDraw() {
        val who = activeHuman() ?: return
        if (!_state.value.canOfferDraw) return
        submit(who, GameAction(ChessModule.ACTION_OFFER_DRAW))
        if (mode == ChessMode.VS_BOT) {
            // The bot answers right away (deterministically) through its normal policy.
            _state.value = _state.value.copy(botThinking = true)
            botPending = true
            botJob = scope.launch {
                delay(700)
                botPending = false
                _state.value = _state.value.copy(botThinking = false)
                if (_state.value.view?.drawOfferBy != HUMAN.value) return@launch
                val r = engine.submitBot(BOT)
                if (r is ApplyResult.Ok) {
                    val agreed = session.phase == Phase.FINISHED
                    handle(r, BOT)
                    if (!agreed) _state.value = _state.value.copy(note = "ربات پیشنهاد مساوی را رد کرد")
                }
            }
        }
    }

    /** Pass-and-play: the asked player accepts or declines. */
    fun answerDraw(accept: Boolean) {
        val side = _state.value.drawQuestionFor
        if (side == 0) return
        val who = session.playerOf(side)
        submit(who, GameAction(if (accept) ChessModule.ACTION_ACCEPT_DRAW else ChessModule.ACTION_DECLINE_DRAW))
        if (!accept) _state.value = _state.value.copy(note = "پیشنهاد مساوی رد شد")
    }

    /**
     * Takes back the last move of the human (and the bot's reply) — or, in pass-and-play, the last
     * move. Implemented by replaying a shortened action log from the seed.
     */
    fun undo() {
        val s = _state.value
        if (!s.canUndo) return
        cancelBot()
        val actions = engine.actions
        val cut = if (mode == ChessMode.VS_BOT) {
            actions.indexOfLast { it.player == HUMAN && it.type == ChessModule.ACTION_MOVE }
        } else {
            actions.indexOfLast { it.type == ChessModule.ACTION_MOVE }
        }
        if (cut < 0) return
        val record = engine.record()
        engine = GameEngine.replay(ChessModule, record.copy(actions = actions.take(cut)))
        onSound("undo")
        _state.value = _state.value.copy(outcome = null, note = null, error = null)
        refresh()
        if (mode == ChessMode.PASS_AND_PLAY && autoRotate) rotateTo(session.sideToMove, immediate = true)
        maybeBot()
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    fun clearNote() {
        if (_state.value.note != null) _state.value = _state.value.copy(note = null)
    }

    /** Turns the camera to the other side (manual "flip board"). */
    fun flip() {
        _state.value = _state.value.copy(viewerSide = -_state.value.viewerSide)
    }

    // ------------------------------------------------------------------ engine plumbing

    private fun playMove(uci: String) {
        val who = activeHuman() ?: return
        val action = ChessSession.moveAction(uci) ?: return
        submit(who, action)
    }

    private fun submit(player: PlayerId, action: GameAction) {
        when (val r = engine.submit(player, action)) {
            is ApplyResult.Ok -> handle(r, player)
            is ApplyResult.Rejected -> {
                onSound("illegal")
                _state.value = _state.value.copy(error = r.messageFa)
            }
        }
    }

    private fun handle(r: ApplyResult.Ok, actor: PlayerId) {
        r.emissions.filterIsInstance<Emission.Sound>().forEach { playSound(it.name) }
        // Only a changed position animates, so resign/draw actions simply refresh.
        refresh()
        if (mode == ChessMode.PASS_AND_PLAY && autoRotate && session.phase == Phase.PLAYING) rotateTo(session.sideToMove, immediate = false)
        if (actor != BOT) maybeBot()
    }

    private fun playSound(name: String) {
        if (name != ChessSession.SOUND_GAME_END) {
            onSound(name)
            return
        }
        val winners = session.result()?.winners.orEmpty()
        onSound(
            when {
                winners.size != 1 -> "draw"
                mode == ChessMode.PASS_AND_PLAY || winners[0] == HUMAN -> "win"
                else -> "lose"
            },
        )
    }

    /** Lets the bot move (off the main thread) when it is its turn. */
    private fun maybeBot() {
        if (mode != ChessMode.VS_BOT) return
        if (session.phase != Phase.PLAYING || session.colorOf(BOT) != session.sideToMove) return
        if (botPending) return
        val eng = engine
        val sess = session
        val versionAtStart = sess.version
        botPending = true
        _state.value = _state.value.copy(botThinking = true)
        botJob = scope.launch {
            if (_state.value.anim != null) delay(560) // let the last move land first
            val t0 = System.nanoTime()
            // The session is only read here; input that could change it is blocked while
            // botThinking, and resign/undo cancel this job and discard its answer.
            val action = withContext(Dispatchers.Default) { ChessModule.botAction?.invoke(sess, BOT) }
            val spent = (System.nanoTime() - t0) / 1_000_000
            if (spent < BOT_MIN_THINK_MS) delay(BOT_MIN_THINK_MS - spent)
            botPending = false
            _state.value = _state.value.copy(botThinking = false)
            if (action != null && engine === eng && sess.version == versionAtStart) submit(BOT, action)
        }
    }

    private fun cancelBot() {
        botPending = false
        val j = botJob
        botJob = null
        if (j != null) j.cancel()
        if (_state.value.botThinking) _state.value = _state.value.copy(botThinking = false)
    }

    private fun rotateTo(side: Int, immediate: Boolean) {
        val old = rotateJob
        rotateJob = null
        if (old != null) old.cancel()
        if (immediate) {
            _state.value = _state.value.copy(viewerSide = side)
            return
        }
        rotateJob = scope.launch {
            delay(ROTATE_DELAY_MS)
            _state.value = _state.value.copy(viewerSide = side)
        }
    }

    /** The human whose input the screen currently takes. */
    private fun activeHuman(): PlayerId? = when (mode) {
        ChessMode.VS_BOT -> HUMAN
        ChessMode.PASS_AND_PLAY -> session.playerOf(session.sideToMove)
    }

    /**
     * Re-reads the view of the active human and rebuilds the UI state. When the position moved on,
     * the first new ply is the one that gets animated (if the bot answered before this ran, its
     * move is still shown as the last-move highlight — the human's own move is the visible one).
     */
    private fun refresh() {
        val prev = _state.value
        val v = TableView.parse(session.privateView(activeHuman() ?: HUMAN))
        val changed = v.fen != lastFen || v.ply != lastPly
        var anim = prev.anim
        if (changed) {
            positionCounter++
            val before = lastSquares
            val uci = if (before != null && lastPly in 0 until v.ply) v.uci.getOrNull(lastPly) else null
            anim = if (before != null && uci != null) MoveAnim.of(before, uci) else null
            lastFen = v.fen
            lastPly = v.ply
            lastSquares = v.squares
        }
        val canUndo = when (mode) {
            ChessMode.VS_BOT -> engine.actions.any { it.player == HUMAN && it.type == ChessModule.ACTION_MOVE }
            ChessMode.PASS_AND_PLAY -> engine.actions.any { it.type == ChessModule.ACTION_MOVE }
        }
        val drawQuestion = if (mode == ChessMode.PASS_AND_PLAY && v.drawOfferBy != null && !v.finished) -v.colorOf(v.drawOfferBy) else 0
        _state.value = prev.copy(
            view = v,
            selected = if (changed) -1 else prev.selected,
            quietTargets = if (changed) emptySet() else prev.quietTargets,
            captureTargets = if (changed) emptySet() else prev.captureTargets,
            positionId = positionCounter,
            anim = anim,
            canUndo = canUndo,
            outcome = outcomeOf(v),
            drawQuestionFor = drawQuestion,
            error = if (changed) null else prev.error,
        )
    }

    private fun outcomeOf(v: TableView): ChessOutcome? {
        if (!v.finished) return null
        val reason = ChessText.reason(v.outcomeReason)
        val winnerSide = v.winner?.let { v.colorOf(it) } ?: 0
        return if (winnerSide == 0) {
            ChessOutcome("مساوی", reason, 0, null)
        } else if (mode == ChessMode.VS_BOT) {
            val iWon = v.winner == HUMAN.value
            ChessOutcome(if (iWon) "بردی! 🎉" else "باختی", reason, winnerSide, iWon)
        } else {
            ChessOutcome("${ChessText.colorName(winnerSide)} برد", reason, winnerSide, null)
        }
    }

    private fun card(id: PlayerId, side: Int) = ChessPlayerCard(names[id] ?: ChessText.colorName(side), side, id == BOT)
}
