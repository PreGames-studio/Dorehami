package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmParticleTest {

    @Test
    fun `step moves particle`() {
        val p = HokmParticle.Particle(0f, 0f, 1f, 1f, 1f)
        val p2 = HokmParticle.step(p, 0.1f)
        assertTrue(p2.x > 0f)
    }

    @Test
    fun `life decreases`() {
        val p = HokmParticle.Particle(0f, 0f, 0f, 0f, 1f)
        val p2 = HokmParticle.step(p, 0.3f)
        assertTrue(p2.life < p.life)
    }

    @Test
    fun `is alive when life positive`() {
        val p = HokmParticle.Particle(0f, 0f, 0f, 0f, 0.5f)
        assertTrue(HokmParticle.isAlive(p))
    }

    @Test
    fun `is dead when life zero`() {
        val p = HokmParticle.Particle(0f, 0f, 0f, 0f, 0f)
        assertTrue(!HokmParticle.isAlive(p))
    }

    @Test
    fun `alpha in range`() {
        val p = HokmParticle.Particle(0f, 0f, 0f, 0f, 0.5f)
        val a = HokmParticle.alpha(p, 1f)
        assertTrue(a in 0f..1f)
    }
}
