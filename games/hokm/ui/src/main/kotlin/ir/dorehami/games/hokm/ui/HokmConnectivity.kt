package ir.dorehami.games.hokm.ui

/**
 * HK-031 (connectivity slice) — connectivity model as **pure, unit-testable** logic.
 */
object HokmConnectivity {

    enum class State { ONLINE, OFFLINE, UNKNOWN }

    fun isOnline(s: State): Boolean = s == State.ONLINE

    fun shouldQueue(s: State): Boolean = s != State.ONLINE

    fun label(s: State): String = when (s) {
        State.ONLINE -> "آنلاین"
        State.OFFLINE -> "آفلاین"
        State.UNKNOWN -> "نامشخص"
    }
}
