package ir.dorehami.gamesdk

/**
 * Time source injected into games. Games never call `System.currentTimeMillis()` (ADR-0003).
 */
fun interface Clock {
    fun nowMillis(): Long
}

/** A clock that only moves when the test (or the engine replaying a record) tells it to. */
class ManualClock(private var now: Long = 0L) : Clock {
    override fun nowMillis(): Long = now

    fun advance(millis: Long) {
        require(millis >= 0) { "time only moves forward" }
        now += millis
    }

    fun set(millis: Long) {
        now = millis
    }
}

/**
 * Deterministic random source injected into games.
 *
 * The state is exposed so a session can be snapshotted and resumed exactly.
 */
interface Rng {
    /** Uniform value in `[0, bound)`. */
    fun nextInt(bound: Int): Int

    fun nextLong(): Long

    /** Opaque state for [snapshot]/restore. */
    var state: Long
}

/**
 * SplitMix64 — tiny, fast, platform independent (same stream on every JVM/Android version),
 * which is exactly what replay needs. Not for security.
 */
class SplitMixRng(seed: Long) : Rng {
    override var state: Long = seed

    override fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    override fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive" }
        // Rejection sampling keeps the distribution uniform.
        val mask = bound.toLong() - 1
        if (bound and (bound - 1) == 0) return (nextLong() and mask).toInt()
        while (true) {
            val bits = nextLong() ushr 1
            val value = bits % bound
            if (bits - value + (bound - 1) >= 0) return value.toInt()
        }
    }
}

/** Fisher–Yates shuffle driven by the injected [Rng] — the only legal way to shuffle in a game. */
fun <T> Rng.shuffle(list: List<T>): List<T> {
    val out = list.toMutableList()
    for (i in out.size - 1 downTo 1) {
        val j = nextInt(i + 1)
        val tmp = out[i]
        out[i] = out[j]
        out[j] = tmp
    }
    return out
}
