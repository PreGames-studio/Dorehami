package ir.dorehami.games.hokm.ui

/**
 * HK-011 (hash slice) — hash helpers as **pure, unit-testable** logic.
 */
object HokmHash {

    /** Simple deterministic hash (djb2). */
    fun djb2(s: String): Long {
        var hash: Long = 5381
        for (c in s) {
            hash = ((hash shl 5) + hash) + c.code
        }
        return hash
    }

    fun isSame(a: String, b: String): Boolean = djb2(a) == djb2(b)
}
