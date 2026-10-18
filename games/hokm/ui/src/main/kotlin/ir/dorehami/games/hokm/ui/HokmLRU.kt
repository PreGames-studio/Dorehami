package ir.dorehami.games.hokm.ui

/**
 * HK-011 (lru slice) — lru helpers as **pure, unit-testable** logic.
 */
object HokmLRU {

    fun <K> touch(order: List<K>, key: K): List<K> =
        (order.filter { it != key } + key)

    fun <K> evictIfNeeded(order: List<K>, capacity: Int): List<K> =
        if (order.size > capacity) order.drop(order.size - capacity) else order
}
