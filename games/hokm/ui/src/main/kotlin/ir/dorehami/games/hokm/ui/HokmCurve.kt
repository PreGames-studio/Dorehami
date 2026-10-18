package ir.dorehami.games.hokm.ui

/**
 * GX-04 (curve slice) — curve helpers as **pure, unit-testable** logic.
 */
object HokmCurve {

    data class Point(val x: Float, val y: Float)

    /** Cubic bezier point. */
    fun cubic(p0: Point, p1: Point, p2: Point, p3: Point, t: Float): Point {
        val c = t.coerceIn(0f, 1f)
        val u = 1f - c
        return Point(
            u*u*u*p0.x + 3*u*u*c*p1.x + 3*u*c*c*p2.x + c*c*c*p3.x,
            u*u*u*p0.y + 3*u*u*c*p1.y + 3*u*c*c*p2.y + c*c*c*p3.y
        )
    }
}
