package ir.dorehami.games.hokm.ui

/**
 * GX-04 (tilt slice) — tilt helpers as **pure, unit-testable** logic.
 */
object HokmTilt {

    fun clamp(degrees: Float): Float = degrees.coerceIn(-15f, 15f)

    fun forCard(isDragging: Boolean): Float = if (isDragging) 8f else 0f
}
