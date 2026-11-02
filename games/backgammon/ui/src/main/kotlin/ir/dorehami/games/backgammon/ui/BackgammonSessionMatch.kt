package ir.dorehami.games.backgammon.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.backgammon.BackgammonModule
import ir.dorehami.games.backgammon.BackgammonPosition
import ir.dorehami.games.backgammon.BackgammonTurnDraft
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.session.ClientState
import ir.dorehami.session.SessionEvent
import ir.dorehami.session.SessionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * A seat at a shared backgammon table (Bluetooth room).
 *
 * The host runs the engine; this class only mirrors my private view into the same
 * [BackgammonUiState] the offline table renders and forwards my taps as [GameAction]s. The local
 * turn draft is a *preview*: the host validates every move through the same rules, so a modified
 * client cannot move a checker the rules would not allow.
 */
class BackgammonSessionMatch(
    private val scope: CoroutineScope,
    private val client: SessionClient,
    private val modeLabel: String,
) {
    private val _state = mutableStateOf(BackgammonUiState())
    val state: State<BackgammonUiState> get() = _state

    private var draft: BackgammonTurnDraft? = null
    private var lastStamp: Int = Int.MIN_VALUE
    private var selectedPoint: Int = -1
    private var selectedDie: Int = 0
    private var job: Job? = null
    private var started = false
    private var lastState: ClientState? = null

    fun begin() {
        if (started) return
        started = true
        job = scope.launch {
            // Wait for the first real view from the host, then follow every update.
            client.state.first { it.privateView.containsKey("points") }
            launch {
                // A rejected move never changes the hosted state, so the preview has to be rolled
                // back here — otherwise my illegal checkers would stay on the board.
                client.events.collect { event ->
                    if (event is SessionEvent.Error) {
                        draft = null
                        selectedPoint = -1
                        selectedDie = 0
                        lastState?.let { render(it, errorOverride = event.messageFa) }
                    }
                }
            }
            client.state.collect { state ->
                lastState = state
                render(state)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    // ---------------------------------------------------------------- my taps

    fun roll() {
        val s = _state.value
        if (!s.canRoll) return
        submit(GameAction(BackgammonModule.ACTION_ROLL))
    }

    fun tapPoint(index: Int) {
        val active = draft ?: return
        if (active.diceLeft.isEmpty()) return
        val die = when {
            selectedDie != 0 && active.sourcesFor(selectedDie).contains(index) -> selectedDie
            else -> active.diceLeft.distinct().firstOrNull { active.sourcesFor(it).contains(index) } ?: 0
        }
        if (die == 0) {
            _state.value = _state.value.copy(error = "این مهره با تاس‌های فعلی حرکت نمی‌کند")
            return
        }
        selectedPoint = -1
        selectedDie = 0
        active.play(index, die)
        if (active.isComplete()) submit(active.action()) else render(client.state.value)
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
            if (active.isComplete()) submit(active.action()) else render(client.state.value)
        } else {
            render(client.state.value)
        }
    }

    fun undo() {
        val active = draft ?: return
        active.undo()
        selectedPoint = -1
        selectedDie = 0
        render(client.state.value)
    }

    fun submitTurn() {
        val active = draft ?: return
        if (!active.isSubmittable()) {
            _state.value = _state.value.copy(error = "باید بیشترین استفاده از تاس‌ها بشود")
            return
        }
        submit(active.action())
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    // ---------------------------------------------------------------- internals

    private fun submit(action: GameAction) {
        scope.launch { runCatching { client.send(action) } }
    }

    private fun render(s: ClientState, errorOverride: String? = null) {
        val view = s.privateView
        if (view.isEmpty()) return
        val me = s.me.value
        val whiteId = view.string("white")
        val blackId = view.string("black")
        val myWhite = whiteId == me
        val myTurn = view.string("turn") == me
        val dice = view.ints("dice") ?: emptyList()
        val board = view.position() ?: return
        val finished = s.phase == "FINISHED" || board.whiteFinished || board.blackFinished
        val whiteToMove = whiteId != null && view.string("turn") == whiteId

        // The draft belongs to one board: any accepted move (or a new roll) throws the preview away.
        val stamp = board.hashCode() * 31 + dice.hashCode()
        val stampChanged = stamp != lastStamp
        if (stampChanged) {
            lastStamp = stamp
            draft = null
            selectedPoint = -1
            selectedDie = 0
        }
        // A rejected move is shown once: the next accepted board clears it.
        val error = errorOverride ?: if (stampChanged) null else _state.value.error
        val canPlay = myTurn && dice.isNotEmpty() && !finished
        val active = if (canPlay) {
            draft ?: BackgammonTurnDraft(board, dice, myWhite).also { draft = it }
        } else {
            null
        }
        val shown = active?.board ?: board
        val hints = when {
            active == null -> emptySet()
            selectedDie != 0 -> active.sourcesFor(selectedDie).toSet()
            else -> active.diceLeft.flatMap { die -> active.sourcesFor(die) }.toSet()
        }
        val score = view["score"] as? JsonObject ?: JsonObject(emptyMap())
        val members = s.room?.members ?: emptyList()
        fun nameOf(id: String?): String = members.firstOrNull { it.player.id.value == id }?.player?.displayName ?: "حریف"

        _state.value = BackgammonUiState(
            board = shown,
            dice = active?.diceLeft ?: dice,
            whiteToMove = whiteToMove,
            whiteName = if (myWhite) s.room?.members?.firstOrNull { it.player.id.value == me }?.player?.displayName ?: "شما" else nameOf(whiteId),
            blackName = if (!myWhite) s.room?.members?.firstOrNull { it.player.id.value == me }?.player?.displayName ?: "شما" else nameOf(blackId),
            myTurn = myTurn && !finished,
            canRoll = myTurn && dice.isEmpty() && !finished,
            selectedPoint = selectedPoint,
            selectedDie = selectedDie,
            hints = hints,
            pending = active?.sequence() ?: emptyList(),
            scoreWhite = (score["white"] as? JsonPrimitive)?.intOrNull ?: 0,
            scoreBlack = (score["black"] as? JsonPrimitive)?.intOrNull ?: 0,
            target = (view["target"] as? JsonPrimitive)?.intOrNull ?: BackgammonModule.DEFAULT_TARGET_POINTS,
            gameNumber = (view["game"] as? JsonPrimitive)?.intOrNull ?: 1,
            thinking = false,
            error = error,
            finished = finished,
            outcomeTitle = when {
                board.whiteFinished -> "سفید برد"
                board.blackFinished -> "سیاه برد"
                else -> null
            },
            outcomeDetail = if (finished) "اتاق $modeLabel" else null,
        )
    }
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

private fun JsonObject.ints(key: String): List<Int>? =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull }

internal fun JsonObject.position(): BackgammonPosition? {
    val points = ints("points") ?: return null
    if (points.size != 24) return null
    val bar = this["bar"] as? JsonObject ?: JsonObject(emptyMap())
    val off = this["off"] as? JsonObject ?: JsonObject(emptyMap())
    return BackgammonPosition(
        points = points,
        barWhite = (bar["white"] as? JsonPrimitive)?.intOrNull ?: 0,
        barBlack = (bar["black"] as? JsonPrimitive)?.intOrNull ?: 0,
        offWhite = (off["white"] as? JsonPrimitive)?.intOrNull ?: 0,
        offBlack = (off["black"] as? JsonPrimitive)?.intOrNull ?: 0,
    )
}
