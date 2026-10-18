package ir.dorehami.games.hokm.ui

/**
 * HK-020 (datetime slice) — date/time helpers as **pure, unit-testable** logic.
 */
object HokmDateTime {

    fun formatDuration(ms: Long): String {
        val sec = ms / 1000
        val min = sec / 60
        val remSec = sec % 60
        return "${min}:${remSec.toString().padStart(2, '0')}"
    }

    fun isToday(timestampMs: Long, todayStartMs: Long): Boolean =
        timestampMs >= todayStartMs && timestampMs < todayStartMs + 86400000

    fun dayIndex(timestampMs: Long): Int = (timestampMs / 86400000).toInt()
}
