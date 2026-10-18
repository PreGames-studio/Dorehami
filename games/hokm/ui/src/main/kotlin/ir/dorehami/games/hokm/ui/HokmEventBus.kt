package ir.dorehami.games.hokm.ui

/**
 * HK-053 (event bus slice) — event bus helpers as **pure, unit-testable** logic.
 */
object HokmEventBus {

    data class Event(val name: String, val payload: String = "")

    fun enqueue(queue: List<Event>, event: Event): List<Event> = queue + event

    fun dequeue(queue: List<Event>): Pair<Event?, List<Event>> =
        if (queue.isEmpty()) null to emptyList() else queue.first() to queue.drop(1)

    fun size(queue: List<Event>): Int = queue.size
}
