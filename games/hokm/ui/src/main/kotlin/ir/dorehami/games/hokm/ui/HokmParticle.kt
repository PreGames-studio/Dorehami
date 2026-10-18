package ir.dorehami.games.hokm.ui

/**
 * HK-025 (particle slice) — particle helpers as **pure, unit-testable** logic.
 */
object HokmParticle {

    data class Particle(val x: Float, val y: Float, val vx: Float, val vy: Float, val life: Float)

    fun step(p: Particle, dt: Float, gravity: Float = 9.8f): Particle =
        p.copy(
            x = p.x + p.vx * dt,
            y = p.y + p.vy * dt + 0.5f * gravity * dt * dt,
            vy = p.vy + gravity * dt,
            life = (p.life - dt).coerceAtLeast(0f)
        )

    fun isAlive(p: Particle): Boolean = p.life > 0f

    fun alpha(p: Particle, maxLife: Float): Float = (p.life / maxLife).coerceIn(0f, 1f)
}
