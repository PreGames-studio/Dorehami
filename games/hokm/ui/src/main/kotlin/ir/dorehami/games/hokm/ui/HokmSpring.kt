package ir.dorehami.games.hokm.ui

/**
 * GX-04 (spring slice) — spring helpers as **pure, unit-testable** logic.
 */
object HokmSpring {

    /** Damped spring step. */
    fun step(value: Float, target: Float, velocity: Float, stiffness: Float, damping: Float, dt: Float): Pair<Float, Float> {
        val force = (target - value) * stiffness
        val newVel = (velocity + force * dt) * (1f - damping * dt).coerceAtLeast(0f)
        val newVal = value + newVel * dt
        return newVal to newVel
    }

    fun isSettled(value: Float, target: Float, velocity: Float, threshold: Float = 0.01f): Boolean =
        kotlin.math.abs(value - target) < threshold && kotlin.math.abs(velocity) < threshold
}
