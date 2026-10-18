package ir.dorehami.games.hokm.ui

/**
 * GX-05 (light slice) — lighting helpers as **pure, unit-testable** logic.
 */
object HokmLight {

    data class Light(val intensity: Float, val angle: Float)

    fun defaultLight(): Light = Light(0.8f, 45f)

    fun dimmed(l: Light, factor: Float): Light = l.copy(intensity = (l.intensity * factor).coerceIn(0f, 1f))

    fun isBright(l: Light): Boolean = l.intensity > 0.5f
}
