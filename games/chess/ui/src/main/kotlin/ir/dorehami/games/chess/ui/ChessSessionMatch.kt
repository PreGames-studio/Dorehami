package ir.dorehami.games.chess.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.chess.ChessModule
import ir.dorehami.games.chess.ChessSession
import ir.dorehami.games.chess.ui.gl.MoveAnim
import ir.dorehami.games.chess.ui.room.ChessRoomMode
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.session.ClientState
import ir.dorehami.session.SessionClient
import ir.dorehami.session.SessionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Mirrors a host-authoritative chess session into the same table state used by local play.
 * The guest never applies a move locally; it only sends actions and renders its private view.
 */
internal class ChessSessionMatch(
    private val scope: CoroutineScope,
    private val client: SessionClient,
    private val roomMode: ChessRoomMode,
    private val onSound: (String) -> Unit,
) {
    private val _state = mutableStateOf(
        ChessUiState(
            mode = ChessMode.NETWORK,
            modeLabel = if (roomMode == ChessRoomMode.BLUETOOTH) "بلوتوث · آفلاین" else "هات‌اسپات · آفلاین",
        ),
    )
    val state: State<ChessUiState> get() = _state

    private var jobs: List<Job> = emptyList()
    private var positionCounter = 0L
    private var lastFen: String? = null
    private var lastPly = -1
    private var lastSquares: IntArray? = null
    private var lastSeq = -1L
    private var actionPending = false

    fun begin() {
        if (jobs.isNotEmpty()) return
        jobs = listOf(
            scope.launch { client.state.collect { onState(it) } },
            scope.launch { client.events.collect { onEvent(it) } },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
    }

    fun onSquareTap(square: Int) {
        val current = _state.value
        val view = current.view ?: return
        if (actionPending || current.promotion != null || current.outcome != null) return
        when (val result = Selection.tap(view, current.selected, square)) {
            is TapResult.Select -> {
                onSound("select")
                _state.value = current.copy(
                    selected = result.square,
                    quietTargets = result.quietTargets,
                    captureTargets = result.captureTargets,
                    error = null,
                )
            }
            TapResult.Deselect -> _state.value = current.copy(
                selected = -1,
                quietTargets = emptySet(),
                captureTargets = emptySet(),
            )
            is TapResult.Move -> send(ChessSession.moveAction(result.uci) ?: return)
            is TapResult.ChoosePromotion -> _state.value = current.copy(promotion = result)
            is TapResult.Rejected -> {
                onSound("illegal")
                _state.value = current.copy(error = result.messageFa)
            }
            TapResult.Ignore -> Unit
        }
    }

    fun choosePromotion(piece: Char?) {
        val pending = _state.value.promotion ?: return
        _state.value = _state.value.copy(promotion = null)
        if (piece == null) return
        send(ChessSession.moveAction(FenBoard.name(pending.from) + FenBoard.name(pending.to) + piece) ?: return)
    }

    fun resign() {
        if (_state.value.canResign) send(GameAction(ChessModule.ACTION_RESIGN))
    }

    fun offerDraw() {
        if (_state.value.canOfferDraw) send(GameAction(ChessModule.ACTION_OFFER_DRAW))
    }

    fun answerDraw(accept: Boolean) {
        if (_state.value.drawQuestionFor == 0) return
        send(GameAction(if (accept) ChessModule.ACTION_ACCEPT_DRAW else ChessModule.ACTION_DECLINE_DRAW))
    }

    fun flip() {
        _state.value = _state.value.copy(viewerSide = -_state.value.viewerSide)
    }

    fun clearError() {
        if (_state.value.error != null) _state.value = _state.value.copy(error = null)
    }

    fun clearNote() {
        if (_state.value.note != null) _state.value = _state.value.copy(note = null)
    }

    private fun send(action: GameAction) {
        if (actionPending) return
        actionPending = true
        scope.launch {
            try {
                client.send(action)
            } catch (_: Exception) {
                actionPending = false
                _state.value = _state.value.copy(error = "حرکت به میزبان نرسید؛ ارتباط را بررسی کن.")
            }
        }
    }

    private fun onState(sessionState: ClientState) {
        if (!sessionState.connected && sessionState.privateView.isEmpty()) {
            _state.value = _state.value.copy(error = "ارتباط با میزبان قطع شد.")
            return
        }
        if (!sessionState.privateView.containsKey("fen") || !sessionState.privateView.containsKey("you")) return
        val view = TableView.parse(sessionState.privateView)
        val previous = _state.value
        val changed = view.fen != lastFen || view.ply != lastPly
        var animation = previous.anim
        if (changed) {
            positionCounter++
            val before = lastSquares
            val uci = if (before != null && lastPly in 0 until view.ply) view.uci.getOrNull(lastPly) else null
            animation = if (before != null && uci != null) MoveAnim.of(before, uci) else null
            lastFen = view.fen
            lastPly = view.ply
            lastSquares = view.squares
        }
        if (sessionState.seq != lastSeq) {
            actionPending = false
            lastSeq = sessionState.seq
        }
        val outcome = outcomeOf(view)
        if (previous.outcome == null && outcome != null) {
            onSound(when {
                outcome.winnerSide == 0 -> "draw"
                outcome.iWon == true -> "win"
                else -> "lose"
            })
        }
        _state.value = previous.copy(
            view = view,
            white = playerCard(sessionState, view.whitePlayer, Side.WHITE),
            black = playerCard(sessionState, view.blackPlayer, Side.BLACK),
            viewerSide = if (previous.view == null) view.you?.color ?: previous.viewerSide else previous.viewerSide,
            selected = if (changed) -1 else previous.selected,
            quietTargets = if (changed) emptySet() else previous.quietTargets,
            captureTargets = if (changed) emptySet() else previous.captureTargets,
            positionId = positionCounter,
            anim = animation,
            botThinking = false,
            canUndo = false,
            outcome = outcome,
            drawQuestionFor = view.you?.takeIf { it.drawOfferedToMe }?.color ?: 0,
            error = if (!sessionState.connected) "ارتباط با میزبان قطع شد." else if (changed) null else previous.error,
        )
    }

    private fun onEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.Error -> {
                actionPending = false
                _state.value = _state.value.copy(error = event.messageFa)
            }
            is SessionEvent.Sound -> if (event.name != ChessSession.SOUND_GAME_END) onSound(event.name)
            is SessionEvent.Public -> if (event.type == "draw_declined") {
                _state.value = _state.value.copy(note = "پیشنهاد مساوی رد شد")
            }
            else -> Unit
        }
    }

    private fun playerCard(sessionState: ClientState, playerId: String, side: Int): ChessPlayerCard {
        val member = sessionState.room?.members?.firstOrNull { it.player.id.value == playerId }
        return ChessPlayerCard(member?.player?.displayName ?: ChessText.colorName(side), side, member?.isBot == true)
    }

    private fun outcomeOf(view: TableView): ChessOutcome? {
        if (!view.finished) return null
        val winnerSide = view.winner?.let { view.colorOf(it) } ?: 0
        if (winnerSide == 0) return ChessOutcome("مساوی", ChessText.reason(view.outcomeReason), 0, null)
        val won = view.winner != null && view.winner == view.you?.player
        return ChessOutcome(if (won) "بردی! 🎉" else "باختی", ChessText.reason(view.outcomeReason), winnerSide, won)
    }
}
