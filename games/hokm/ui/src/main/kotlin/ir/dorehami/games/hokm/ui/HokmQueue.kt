package ir.dorehami.games.hokm.ui

/**
 * HK-011 (queue slice) — queue helpers as **pure, unit-testable** logic.
 */
object HokmQueue {

    fun <T> enqueue(queue: List<T>, value: T): List<T> = queue + value

    fun <T> dequeue(queue: List<T>): Pair<T?, List<T>> =
        if (queue.isEmpty()) null to emptyList() else queue.first() to queue.drop(1)

    fun <T> peek(queue: List<T>): T? = queue.firstOrNull()

    fun <T> size(queue: List<T>): Int = queue.size
}
