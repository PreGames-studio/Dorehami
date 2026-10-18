package ir.dorehami.games.hokm.ui

/**
 * GX-04 (translation slice) — translation helpers as **pure, unit-testable** logic.
 */
object HokmTranslation {

    data class Point(val x: Float, val y: Float)

    fun lerp(a: Point, b: Point, t: Float): Point {
        val clamped = t.coerceIn(0f, 1f)
        return Point(a.x + (b.x - a.x) * clamped, a.y + (b.y - a.y) * clamped)
    }

    fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
