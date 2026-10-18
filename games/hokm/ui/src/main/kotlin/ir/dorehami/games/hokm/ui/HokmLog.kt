package ir.dorehami.games.hokm.ui

/**
 * HK-053 (log slice) — log buffer as **pure, unit-testable** logic.
 */
object HokmLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(val level: Level, val tag: String, val message: String, val timestampMs: Long)

    const val MAX_ENTRIES: Int = 500

    fun append(buffer: List<Entry>, entry: Entry): List<Entry> {
        val new = buffer + entry
        return if (new.size > MAX_ENTRIES) new.takeLast(MAX_ENTRIES) else new
    }

    fun filterByLevel(buffer: List<Entry>, minLevel: Level): List<Entry> {
        val order = Level.values().indexOf(minLevel)
        return buffer.filter { Level.values().indexOf(it.level) >= order }
    }

    fun count(buffer: List<Entry>, level: Level): Int = buffer.count { it.level == level }
}
