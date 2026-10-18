package ir.dorehami.games.hokm.ui

/**
 * HK-012 (rng slice) — rng helpers as **pure, unit-testable** logic.
 */
object HokmRng {

    /** Linear congruential generator. */
    fun next(seed: Long): Long = seed * 1103515245 + 12345

    fun nextInt(seed: Long, bound: Int): Pair<Int, Long> {
        val s = next(seed)
        val v = ((s ushr 16) % bound.toLong()).toInt()
        return v to s
    }

    fun nextFloat(seed: Long): Pair<Float, Long> {
        val s = next(seed)
        return (((s ushr 16) and 0xFFFF).toFloat() / 65536f) to s
    }
}
