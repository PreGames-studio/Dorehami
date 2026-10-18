package ir.dorehami.games.hokm.ui

/**
 * HK-053 (changelog slice) — changelog helpers as **pure, unit-testable** logic.
 */
object HokmChangelog {

    data class Entry(val version: String, val notes: String, val timestampMs: Long)

    fun latest(entries: List<Entry>): Entry? = entries.maxByOrNull { it.timestampMs }

    fun format(e: Entry): String = "${e.version}: ${e.notes}"

    fun count(entries: List<Entry>): Int = entries.size
}
