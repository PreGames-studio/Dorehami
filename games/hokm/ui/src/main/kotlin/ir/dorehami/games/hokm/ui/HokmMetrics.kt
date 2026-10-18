package ir.dorehami.games.hokm.ui

/**
 * HK-053 (metrics slice) — metrics helpers as **pure, unit-testable** logic.
 */
object HokmMetrics {

    data class Metric(val name: String, val value: Double, val timestampMs: Long)

    fun average(metrics: List<Metric>): Double =
        if (metrics.isEmpty()) 0.0 else metrics.map { it.value }.average()

    fun max(metrics: List<Metric>): Double = metrics.maxOfOrNull { it.value } ?: 0.0

    fun count(metrics: List<Metric>, name: String): Int = metrics.count { it.name == name }
}
