package ir.dorehami.games.hokm.ui

/**
 * GX-05 (gradient slice) — gradient helpers as **pure, unit-testable** logic.
 */
object HokmGradient {

    data class Stop(val color: String, val position: Float)

    fun twoStopGradient(c1: String, c2: String): List<Stop> = listOf(Stop(c1, 0f), Stop(c2, 1f))

    fun isValid(stops: List<Stop>): Boolean = stops.size >= 2 && stops.all { it.position in 0f..1f }
}
