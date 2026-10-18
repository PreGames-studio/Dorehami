package ir.dorehami.games.hokm.ui

/**
 * HK-011 (id slice) — id helpers as **pure, unit-testable** logic.
 */
object HokmId {

    fun generate(seed: Long): String {
        var s = seed
        val sb = StringBuilder()
        repeat(8) {
            s = s * 1103515245 + 12345
            val idx = ((s ushr 16) % 36).toInt()
            val c = if (idx < 10) ('0' + idx) else ('A' + (idx - 10))
            sb.append(c)
        }
        return sb.toString()
    }

    fun isValid(id: String): Boolean = id.length == 8 && id.all { it.isLetterOrDigit() }
}
