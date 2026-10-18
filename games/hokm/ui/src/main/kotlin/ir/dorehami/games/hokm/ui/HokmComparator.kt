package ir.dorehami.games.hokm.ui

/**
 * HK-048 (comparator slice) — comparator helpers as **pure, unit-testable** logic.
 */
object HokmComparator {

    fun compareByScore(a: Int, b: Int): Int = b.compareTo(a)

    fun compareByName(a: String, b: String): Int = a.compareTo(b, ignoreCase = true)

    fun topN(items: List<Int>, n: Int): List<Int> = items.sortedDescending().take(n)
}
