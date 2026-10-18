package ir.dorehami.games.hokm.ui

/**
 * HK-036 (matchmaking slice) — matchmaking helpers as **pure, unit-testable** logic.
 */
object HokmMatchmaking {

    data class QueueEntry(val playerId: String, val skill: Int, val joinedMs: Long)

    fun canMatch(a: QueueEntry, b: QueueEntry, skillTolerance: Int = 100): Boolean =
        kotlin.math.abs(a.skill - b.skill) <= skillTolerance

    fun findMatch(queue: List<QueueEntry>, entry: QueueEntry, skillTolerance: Int = 100): QueueEntry? =
        queue.firstOrNull { it.playerId != entry.playerId && canMatch(it, entry, skillTolerance) }

    fun queueSize(queue: List<QueueEntry>): Int = queue.size

    fun removePlayer(queue: List<QueueEntry>, playerId: String): List<QueueEntry> =
        queue.filter { it.playerId != playerId }
}
