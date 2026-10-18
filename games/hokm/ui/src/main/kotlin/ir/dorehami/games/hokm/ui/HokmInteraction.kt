package ir.dorehami.games.hokm.ui

/**
 * HK-019 (first slice) — reliable card interaction as **pure, unit-testable** logic.
 *
 * The roadmap asks for selection/confirmation, an optional quick mode, prevention of
 * double-send, and an explanation when a card is illegal — plus a wrong-touch test. None of
 * that needs Compose or a device; it is a small reducer over a sealed state. The Compose
 * layer later drives [HokmInteraction.reduce] from taps and renders [HokmInteraction.State].
 *
 * Contract (the numbers/behaviours the tests pin):
 *  * A card can only be **selected** if it is in the legal set; selecting an illegal card
 *    yields [State.Error] with a reason, never a silent no-op.
 *  * **Quick mode** (single tap plays) skips [State.Selected] and goes straight to
 *    [State.Playing].
 *  * **Confirm mode** (default) goes Selected -> Confirming -> Playing.
 *  * **Double-send guard:** once [State.Playing] is reached, further plays are ignored
 *    until [State.Idle] is restored by [Action.Reset].
 *  * **Cancel** from Selected/Confirming returns to Idle without playing.
 *  * **Illegal explanation** is a non-empty Persian reason string.
 */
object HokmInteraction {

    enum class Mode { QUICK, CONFIRM }

    sealed class State {
        object Idle : State()
        data class Selected(val card: String) : State()
        data class Confirming(val card: String) : State()
        data class Playing(val card: String) : State()
        data class Error(val card: String?, val reason: String) : State()
    }

    sealed class Action {
        data class Select(val card: String, val legal: Boolean) : Action()
        object Confirm : Action()
        object Cancel : Action()
        object Reset : Action()
        data class PlayResult(val ok: Boolean, val reason: String = "") : Action()
    }

    /** Persian reason strings for illegal plays. */
    const val REASON_NOT_IN_HAND: String = "این کارت در دست شما نیست"
    const val REASON_ILLEGAL_MOVE: String = "بازی این کارت مجاز نیست"
    const val REASON_DOUBLE_SEND: String = "کارت در حال ارسال است"
    const val REASON_NOTHING_SELECTED: String = "ابتدا یک کارت انتخاب کنید"

    fun reduce(state: State, action: Action, mode: Mode = Mode.CONFIRM): State = when (action) {
        is Action.Select -> {
            if (!action.legal) State.Error(action.card, REASON_ILLEGAL_MOVE)
            else when (state) {
                is State.Playing -> State.Error(action.card, REASON_DOUBLE_SEND)
                else -> if (mode == Mode.QUICK) State.Playing(action.card) else State.Selected(action.card)
            }
        }
        is Action.Confirm -> when (state) {
            is State.Selected -> if (mode == Mode.QUICK) State.Playing(state.card) else State.Confirming(state.card)
            is State.Confirming -> State.Playing(state.card)
            is State.Playing -> State.Error(state.card, REASON_DOUBLE_SEND)
            else -> State.Error(null, REASON_NOTHING_SELECTED)
        }
        is Action.Cancel -> State.Idle
        is Action.Reset -> State.Idle
        is Action.PlayResult -> when (state) {
            is State.Playing -> if (action.ok) State.Idle else State.Error(state.card, action.reason.ifBlank { REASON_ILLEGAL_MOVE })
            else -> state
        }
    }

    /** Whether the state allows a new selection. */
    fun canSelect(state: State): Boolean = state is State.Idle || state is State.Selected || state is State.Confirming || state is State.Error

    /** Whether the state is "busy" (playing in flight). */
    fun isBusy(state: State): Boolean = state is State.Playing

    /** The card currently in flight, if any. */
    fun inFlightCard(state: State): String? = (state as? State.Playing)?.card
}
