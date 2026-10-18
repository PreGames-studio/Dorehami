package ir.dorehami.games.hokm.ui

/**
 * HK-010 (snapshot slice) — snapshot model as **pure, unit-testable** logic.
 */
object HokmSnapshot {

    data class Snapshot(val version: Long, val data: String, val timestampMs: Long)

    fun isNewer(incoming: Snapshot, current: Snapshot?): Boolean =
        current == null || incoming.version > current.version

    fun shouldApply(incoming: Snapshot, current: Snapshot?): Boolean = isNewer(incoming, current)

    fun format(s: Snapshot): String = "v${s.version}@${s.timestampMs}"
}
