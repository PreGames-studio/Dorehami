package ir.dorehami.games.esmfamil.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import ir.dorehami.games.esmfamil.EsmFamilModule
import ir.dorehami.games.esmfamil.EsmFamilSession
import ir.dorehami.games.esmfamil.EsmFamilSheet
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put

/**
 * The pass-and-play table: one phone, two to eight people, the phone moving from hand to hand.
 *
 * It owns the same [GameEngine] a Bluetooth room owns — the rules are identical, only the transport
 * is a thumb instead of a radio. The seed is picked by the caller and recorded, so a saved record
 * replays exactly (ADR-0003).
 */
class EsmFamilLocalMatch(
    names: List<String>,
    rounds: Int = EsmFamilModule.DEFAULT_ROUNDS,
    categories: List<String> = EsmFamilSheet.DEFAULT_IDS,
    letters: String = EsmFamilSheet.LETTERS_COMMON,
    seed: Long = 1L,
) {
    private val playerNames: List<String> = names.mapIndexed { index, name ->
        name.trim().ifBlank { "بازیکن ${index + 1}" }
    }
    private val players: List<PlayerId> = playerNames.indices.map { PlayerId("p_local_$it") }
    private val categoryIds: List<String> = if (categories.size >= EsmFamilModule.MIN_CATEGORIES) categories else EsmFamilSheet.DEFAULT_IDS

    private var engine: GameEngine = GameEngine.start(
        EsmFamilModule,
        GameConfig(
            seed = seed,
            players = players,
            options = view {
                put(EsmFamilSession.Options.ROUNDS, rounds)
                put(
                    EsmFamilSession.Options.CATEGORIES,
                    buildJsonArray { categoryIds.forEach { add(JsonPrimitive(it)) } },
                )
                put(EsmFamilSession.Options.LETTERS, letters)
            },
            clock = ManualClock(),
            rng = SplitMixRng(seed),
        ),
    )

    private val seats: List<EsmFamilSeat> = playerNames.mapIndexed { index, name ->
        EsmFamilSeat(id = players[index].value, name = name, isMe = index == 0)
    }

    private val _state = mutableStateOf(EsmFamilUiState(seats = seats, categories = categoryIds, rounds = rounds))
    val state: State<EsmFamilUiState> get() = _state

    private var draft: Map<String, String> = emptyMap()
    private var writingSeat: Int = 0

    init {
        refresh()
    }

    /** What the current writer types into one column. */
    fun type(category: String, text: String) {
        if (text.length > MAX_ANSWER_CHARS) return
        draft = draft + (category to text)
        refresh(error = null)
    }

    /** Hands the current sheet in. The last sheet opens all of them. */
    fun submit(): Boolean {
        if (_state.value.phase != EsmFamilPhase.WRITING) return false
        val player = players[writingSeat]
        val action = GameAction(
            type = EsmFamilModule.ACTION_SUBMIT,
            payload = view {
                put(
                    EsmFamilModule.PAYLOAD_ANSWERS,
                    view { categoryIds.forEach { put(it, draft[it].orEmpty().trim()) } },
                )
            },
        )
        val result = engine.submit(player, action)
        if (result is ApplyResult.Rejected) {
            refresh(error = result.messageFa)
            return false
        }
        draft = emptyMap()
        if (engine.session.phase != ir.dorehami.gamesdk.Phase.FINISHED && isWriting()) {
            writingSeat = (writingSeat + 1) % players.size
        }
        refresh()
        return true
    }

    /** Opens the scores and deals the next letter (or ends the match). */
    fun next() {
        if (_state.value.phase != EsmFamilPhase.REVEAL) return
        engine.submit(players[0], GameAction(EsmFamilModule.ACTION_NEXT_ROUND))
        draft = emptyMap()
        if (isWriting()) writingSeat = 0
        refresh()
    }

    /** Closes the round early, for a table that ran out of time. */
    fun closeRound() {
        if (_state.value.phase != EsmFamilPhase.WRITING) return
        engine.submit(players[0], GameAction(EsmFamilModule.ACTION_FINISH_ROUND))
        draft = emptyMap()
        refresh()
    }

    private fun isWriting(): Boolean {
        val view = engine.session.publicView()
        return (view["phase"] as? JsonPrimitive)?.content == EsmFamilSession.RoundPhase.WRITING.name
    }

    private fun refresh(error: String? = null) {
        val session = engine.session
        val public = EsmFamilUiModel.public(session.publicView())
        val myPrivate = if (writingSeat in players.indices) {
            EsmFamilUiModel.mine(session.privateView(players[writingSeat]))
        } else {
            emptyMap()
        }
        val standings = public.standings.map { (id, pair) ->
            EsmFamilStanding(
                name = seats.firstOrNull { it.id == id }?.name ?: id,
                rank = pair.first,
                score = pair.second,
                isMe = id == players[0].value,
            )
        }
        _state.value = EsmFamilUiState(
            round = public.round,
            rounds = public.rounds,
            letter = public.letter,
            categories = public.categories,
            phase = public.phase,
            seats = seats,
            writingSeat = writingSeat,
            draft = draft,
            mine = myPrivate,
            submittedIds = public.submitted,
            rows = EsmFamilUiModel.rows(public, seats),
            totals = public.totals,
            standings = standings,
            modeLabel = "آفلاین · ${seats.size} نفره روی همین گوشی",
            isHost = true,
            error = error,
            waitingForOthers = public.submitted.size.let { players.size - it }.coerceAtLeast(0),
        )
    }

    /** The record of what has happened so far — the same shape the online authority stores. */
    fun record() = engine.record()

    private companion object {
        /** Same ceiling the rules enforce; keeps a paste accident out of the draft. */
        const val MAX_ANSWER_CHARS = 40
    }
}
