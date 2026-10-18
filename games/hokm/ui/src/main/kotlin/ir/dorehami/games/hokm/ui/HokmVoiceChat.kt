package ir.dorehami.games.hokm.ui

/**
 * HK-047 (voice chat slice) — voice chat helpers as **pure, unit-testable** logic.
 */
object HokmVoiceChat {

    enum class State { IDLE, CONNECTING, CONNECTED, MUTED, DISCONNECTED }

    fun canSpeak(state: State, muted: Boolean): Boolean = state == State.CONNECTED && !muted

    fun toggleMute(state: State): State = if (state == State.CONNECTED) State.MUTED else if (state == State.MUTED) State.CONNECTED else state

    fun label(state: State): String = when (state) {
        State.IDLE -> "خاموش"
        State.CONNECTING -> "در حال اتصال"
        State.CONNECTED -> "متصل"
        State.MUTED -> "بی‌صدا"
        State.DISCONNECTED -> "قطع"
    }
}
