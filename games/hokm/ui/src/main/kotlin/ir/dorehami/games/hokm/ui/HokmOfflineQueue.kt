package ir.dorehami.games.hokm.ui

/**
 * HK-031 (offline queue slice) — offline action queue as **pure, unit-testable** logic.
 *
 * The roadmap asks for offline play and sync. This object defines a bounded queue of
 * pending actions with dedup. The Android layer drains it when online.
 */
object HokmOfflineQueue {

    data class Action(val id: String, val payload: String, val timestampMs: Long)

    const val MAX_SIZE: Int = 100

    fun enqueue(queue: List<Action>, action: Action): List<Action> {
        if (queue.any { it.id == action.id }) return queue // dedup
        val new = queue + action
        return if (new.size > MAX_SIZE) new.takeLast(MAX_SIZE) else new
    }

    fun dequeue(queue: List<Action>): Pair<Action?, List<Action>> {
        if (queue.isEmpty()) return null to queue
        return queue.first() to queue.drop(1)
    }

    fun size(queue: List<Action>): Int = queue.size

    fun isEmpty(queue: List<Action>): Boolean = queue.isEmpty()

    fun clear(): List<Action> = emptyList()
}
