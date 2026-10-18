package ir.dorehami.games.hokm.ui

/**
 * HK-024 (shuffle slice) — deterministic shuffle as **pure, unit-testable** logic.
 */
object HokmShuffle {

    /** Fisher-Yates shuffle using a simple LCG seeded by [seed]. */
    fun shuffle(deck: List<String>, seed: Long): List<String> {
        val result = deck.toMutableList()
        var rng = seed
        for (i in result.size - 1 downTo 1) {
            rng = rng * 1103515245 + 12345
            val j = ((rng ushr 16) % (i + 1).toLong()).toInt()
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }

    fun isPermutation(original: List<String>, shuffled: List<String>): Boolean =
        original.sorted() == shuffled.sorted()

    fun cut(deck: List<String>, at: Int): List<String> {
        val idx = at.coerceIn(0, deck.size)
        return deck.drop(idx) + deck.take(idx)
    }
}
