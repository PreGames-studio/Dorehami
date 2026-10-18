package ir.dorehami.games.hokm.ui

/**
 * GX-04 (path slice) — path helpers as **pure, unit-testable** logic.
 */
object HokmPath {

    data class Point(val x: Float, val y: Float)

    /** Quadratic bezier point. */
    fun quadratic(p0: Point, p1: Point, p2: Point, t: Float): Point {
        val c = t.coerceIn(0f, 1f)
        val u = 1f - c
        return Point(
            u * u * p0.x + 2 * u * c * p1.x + c * c * p2.x,
            u * u * p0.y + 2 * u * c * p1.y + c * c * p2.y
        )
    }

    /** Arc point (semicircle). */
    fun arc(start: Point, end: Point, height: Float, t: Float): Point {
        val c = t.coerceIn(0f, 1f)
        val x = start.x + (end.x - start.x) * c
        val baseY = start.y + (end.y - start.y) * c
        val arch = height * kotlin.math.sin(c * Math.PI.toFloat())
        return Point(x, baseY - arch)
    }
}
