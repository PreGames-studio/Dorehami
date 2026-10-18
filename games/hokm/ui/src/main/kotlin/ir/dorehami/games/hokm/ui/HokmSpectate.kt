package ir.dorehami.games.hokm.ui

/**
 * HK-038 (spectate slice) — spectate helpers as **pure, unit-testable** logic.
 */
object HokmSpectate {

    data class Spectator(val id: String, val name: String)

    fun canSpectate(roomFull: Boolean, allowSpectators: Boolean): Boolean = allowSpectators

    fun addSpectator(list: List<Spectator>, spec: Spectator): List<Spectator> =
        if (list.any { it.id == spec.id }) list else list + spec

    fun removeSpectator(list: List<Spectator>, id: String): List<Spectator> =
        list.filter { it.id != id }

    fun count(list: List<Spectator>): Int = list.size
}
