package ir.dorehami.games.hokm.ui

/**
 * HK-011 (set slice) — set helpers as **pure, unit-testable** logic.
 */
object HokmSet {

    fun <T> add(set: Set<T>, value: T): Set<T> = set + value

    fun <T> remove(set: Set<T>, value: T): Set<T> = set - value

    fun <T> contains(set: Set<T>, value: T): Boolean = value in set

    fun <T> size(set: Set<T>): Int = set.size

    fun <T> union(a: Set<T>, b: Set<T>): Set<T> = a + b

    fun <T> intersect(a: Set<T>, b: Set<T>): Set<T> = a intersect b
}
