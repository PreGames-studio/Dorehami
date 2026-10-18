package ir.dorehami.games.hokm.ui

/**
 * HK-053 (crash report slice) — crash report helpers as **pure, unit-testable** logic.
 */
object HokmCrashReport {

    data class Report(val throwable: String, val timestampMs: Long, val context: String)

    fun create(throwable: Throwable, nowMs: Long, context: String = ""): Report =
        Report(throwable.toString(), nowMs, context)

    fun format(r: Report): String = "${r.context}: ${r.throwable} @ ${r.timestampMs}"

    fun shouldSend(r: Report): Boolean = r.throwable.isNotEmpty()
}
