package ir.dorehami.games.backgammon.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.backgammon.BackgammonBot
import ir.dorehami.games.backgammon.BackgammonModule
import ir.dorehami.games.backgammon.BackgammonMove
import ir.dorehami.games.backgammon.BackgammonPosition
import ir.dorehami.games.backgammon.BackgammonSession
import ir.dorehami.games.backgammon.BackgammonTurnDraft
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** How a backgammon table is played. NETWORK is reserved for the online room flow (not wired yet). */
enum class BackgammonMode { VS_BOT, PASS_AND_PLAY, NETWORK }

/** Everything the backgammon screen draws; built only from the engine's public state. */
data class BackgammonUiState(
    val board: BackgammonPosition = BackgammonPosition(),
    val dice: List<Int> = emptyList(),
    val whiteToMove: Boolean = true,
    val whiteName: String = "سفید",
    val blackName: String = "سیاه",
    val myTurn: Boolean = false,
    val canRoll: Boolean = false,
    val selectedPoint: Int = -1,
    val selectedDie: Int = 0,
    val hints: Set<Int> = emptySet(),
    val pending: List<BackgammonMove> = emptyList(),
    val scoreWhite: Int = 0,
    val scoreBlack: Int = 0,
    val target: Int = BackgammonModule.DEFAULT_TARGET_POINTS,
    val gameNumber: Int = 1,
    val thinking: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
    val outcomeTitle: String? = null,
    val outcomeDetail: String? = null,
) {
    /** Whose turn is on the table, as a display name. */
    val turnName: String get() = if (whiteToMove) whiteName else blackName

    /** True when the player may pick a checker up right now. */
    val canMove: Boolean get() = myTurn && dice.isNotEmpty()
}

/**
 * Runs one local backgammon match (against the deterministic bot, or pass-and-play on one phone).
 *
 * The authority is always a real [GameEngine] over [BackgammonModule]: the screen submits actions and
 * renders whatever the engine accepts, so the offline table exercises exactly the same rules as the
 * networked one will.
 */
class BackgammonLocalMatch(
    private val scope: CoroutineScope,
    private val mode: BackgammonMode,
    private val humanName: String,
    private val botName: String,
    private val targetPoints: Int,
    seed: Long,
    private val onSound: (String) -> Unit = {},
) {
    companion object {
        val WHITE: PlayerId = PlayerId("p_white")
        val BLACK: PlayerId = PlayerId("p_black")
    }

    private var seedValue: Long = seed
    private var engine: GameEngine = newEngine(seedValue)
    private var draft: BackgammonTurnDraft? = null
    private var lastVersion: Long = -1L
    private var selectedPoint: Int = -1
    private var selectedDie: Int = 0
    private var botJob: Job? = null

    private val _state = mutableStateOf(BackgammonUiState())
    val state: State<BackgammonUiState> get() = _state

    private fun newEngine(seed: Long): GameEngine = GameEngine.start(
        BackgammonModule,
        GameConfig(
            seed = seed,
            players = listOf(WHITE, BLACK),
            options = view { put(BackgammonSession.Options.TARGET_POINTS, targetPoints) },
            // The table may read the wall clock; the rules never do (ADR-0003).
            clock = { System.currentTimeMillis() },
            rng = SplitMixRng(seed),
        ),
    )

    /** Starts listening for the bot's replies (a no-op in pass-and-play). */
    fun start() {
        refresh()
        scheduleBot()
    }

    fun stop() {
        botJob?.cancel()
        botJob = null
    }

    // ---------------------------------------------------------------- actions

    fun roll() {
        val s = session() ?: return
        if (!myTurn(s)) return
        val player = playerToMove(s)
        when (val result = engine.submit(player, GameAction(BackgammonModule.ACTION_ROLL))) {
            is ApplyResult.Ok -> {
                emit(result.emissions)
                resetDraft()
                refresh()
            }
            is ApplyResult.Rejected -> refresh(error = result.messageFa)
        }
    }

    fun tapPoint(index: Int) {
        val s = session() ?: return
        if (!myTurn(s)) return
        val active = draft ?: return
        if (active.diceLeft.isEmpty()) return

        val die = when {
            selectedDie != 0 && active.sourcesFor(selectedDie).contains(index) -> selectedDie
            else -> active.diceLeft.distinct().firstOrNull { active.sourcesFor(it).contains(index) } ?: 0
        }
        if (die == 0) {
            refresh(error = "این مهره با تاس‌های فعلی حرکت نمی‌کند")
            return
        }
        selectedPoint = -1
        selectedDie = 0
        active.play(index, die)
        if (active.isComplete()) submit(active) else refresh()
    }

    fun tapDie(die: Int) {
        val active = draft ?: return
        if (die !in active.diceLeft) return
        selectedDie = die
        if (selectedPoint != -1 && active.sourcesFor(die).contains(selectedPoint)) {
            val point = selectedPoint
            selectedPoint = -1
            selectedDie = 0
            active.play(point, die)
            if (active.isComplete()) submit(active) else refresh()
        } else {
            refresh()
        }
    }

    /** Explicit "I am done" — allowed only when the stacked sequence is maximal. */
    fun submitTurn() {
        val active = draft ?: return
        if (!active.isSubmittable()) {
            refresh(error = "باید بیشترین استفاده از تاس‌ها بشود")
            return
        }
        submit(active)
    }

    fun undo() {
        val active = draft ?: return
        active.undo()
        selectedPoint = -1
        selectedDie = 0
        refresh()
    }

    fun playAgain() {
        botJob?.cancel()
        seedValue = System.currentTimeMillis() xor (seedValue shl 13)
        engine = newEngine(seedValue)
        resetDraft()
        refresh()
        scheduleBot()
    }

    fun clearError() {
        refresh()
    }

    // ---------------------------------------------------------------- internals

    private fun session(): BackgammonSession? = engine.session as? BackgammonSession

    private fun playerToMove(s: BackgammonSession): PlayerId = if (s.isWhiteTurn()) WHITE else BLACK

    private fun myTurn(s: BackgammonSession): Boolean = when (mode) {
        BackgammonMode.PASS_AND_PLAY -> true
        else -> s.isWhiteTurn()
    }

    private fun submit(active: BackgammonTurnDraft) {
        val player = if (active.isWhite) WHITE else BLACK
        when (val result = engine.submit(player, active.action())) {
            is ApplyResult.Ok -> {
                emit(result.emissions)
                resetDraft()
                refresh()
                scheduleBot()
            }
            is ApplyResult.Rejected -> {
                active.reset()
                selectedPoint = -1
                selectedDie = 0
                refresh(error = result.messageFa)
            }
        }
    }

    private fun resetDraft() {
        draft = null
        selectedPoint = -1
        selectedDie = 0
    }

    private fun scheduleBot() {
        if (mode != BackgammonMode.VS_BOT) return
        botJob?.cancel()
        botJob = scope.launch {
            var thinking = false
            while (true) {
                val s = session() ?: break
                if (s.phase == Phase.FINISHED) break
                if (s.isWhiteTurn()) break
                if (!thinking) {
                    thinking = true
                    _state.value = _state.value.copy(thinking = true)
                }
                delay(650)
                val action = BackgammonBot.action(s, BLACK) ?: break
                when (val result = engine.submit(BLACK, action)) {
                    is ApplyResult.Ok -> emit(result.emissions)
                    is ApplyResult.Rejected -> break
                }
                delay(350)
            }
            _state.value = _state.value.copy(thinking = false)
            refresh()
        }
    }

    private fun emit(emissions: List<Emission>) {
        emissions.forEach { emission ->
            if (emission is Emission.Sound) onSound(emission.name)
        }
    }

    private fun refresh(error: String? = null) {
        val s = session() ?: return
        if (s.version != lastVersion) {
            lastVersion = s.version
            draft = null
        }
        val dice = s.currentDice()
        val whiteTurn = s.isWhiteTurn()
        val active = draft ?: if (myTurn(s) && dice.isNotEmpty() && s.phase != Phase.FINISHED) {
            BackgammonTurnDraft(s.board(), dice, whiteTurn).also { draft = it }
        } else {
            null
        }
        val board = active?.board ?: s.board()
        // With a die chosen, show exactly its sources; otherwise show everything that can move now,
        // so the player can tap a checker straight away.
        val hints = when {
            active == null -> emptySet()
            selectedDie != 0 -> active.sourcesFor(selectedDie).toSet()
            else -> active.diceLeft.flatMap { die -> active.sourcesFor(die) }.toSet()
        }
        val result = s.result()
        // Match score and game number are match bookkeeping only present in the public view.
        val view = s.publicView()
        val score = view["score"]?.jsonObject ?: JsonObject(emptyMap())
        val whiteScore = score["white"]?.jsonPrimitive?.intOrNull ?: 0
        val blackScore = score["black"]?.jsonPrimitive?.intOrNull ?: 0
        val gameNumber = view["game"]?.jsonPrimitive?.intOrNull ?: 1

        val whiteName = if (mode == BackgammonMode.PASS_AND_PLAY) "بازیکن ۱" else humanName
        val blackName = when (mode) {
            BackgammonMode.VS_BOT -> botName
            BackgammonMode.PASS_AND_PLAY -> "بازیکن ۲"
            BackgammonMode.NETWORK -> "حریف"
        }

        _state.value = BackgammonUiState(
            board = board,
            dice = if (active != null) active.diceLeft else dice,
            whiteToMove = whiteTurn,
            whiteName = whiteName,
            blackName = blackName,
            myTurn = myTurn(s),
            canRoll = myTurn(s) && dice.isEmpty() && s.phase != Phase.FINISHED,
            selectedPoint = selectedPoint,
            selectedDie = selectedDie,
            hints = hints,
            pending = active?.sequence() ?: emptyList(),
            scoreWhite = whiteScore,
            scoreBlack = blackScore,
            target = targetPoints,
            gameNumber = gameNumber,
            thinking = _state.value.thinking && mode == BackgammonMode.VS_BOT && !whiteTurn,
            error = error,
            finished = s.phase == Phase.FINISHED,
            outcomeTitle = if (result != null) {
                val winnerIsWhite = result.winners.contains(WHITE)
                if (winnerIsWhite) "سفید برد" else "سیاه برد"
            } else {
                null
            },
            outcomeDetail = if (result != null) {
                "امتیاز مسابقه: ${whiteScore} — ${blackScore} · هدف ${targetPoints}"
            } else {
                null
            },
        )
    }
}
