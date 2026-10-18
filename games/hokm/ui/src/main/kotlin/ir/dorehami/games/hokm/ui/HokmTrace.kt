package ir.dorehami.games.hokm.ui

/**
 * HK-053 (trace slice) — trace helpers as **pure, unit-testable** logic.
 */
object HokmTrace {

    data class Span(val name: String, val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    fun create(name: String, startMs: Long, endMs: Long): Span = Span(name, startMs, endMs)

    fun totalDuration(spans: List<Span>): Long = spans.sumOf { it.durationMs }

    fun format(s: Span): String = "${s.name}: ${s.durationMs}ms"
}
