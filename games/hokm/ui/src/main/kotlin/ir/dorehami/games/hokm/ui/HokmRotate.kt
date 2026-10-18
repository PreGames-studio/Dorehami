package ir.dorehami.games.hokm.ui

/**
 * GX-04 (rotate slice) — rotate helpers as **pure, unit-testable** logic.
 */
object HokmRotate {

    fun normalize(degrees: Float): Float {
        var d = degrees % 360f
        if (d < 0) d += 360f
        return d
    }

    fun toRadians(degrees: Float): Float = degrees * (Math.PI.toFloat() / 180f)
}
