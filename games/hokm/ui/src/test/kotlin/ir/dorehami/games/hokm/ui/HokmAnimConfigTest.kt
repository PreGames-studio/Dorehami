package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmAnimConfigTest {

    @Test
    fun `effective duration zero when reduced motion`() {
        val c = HokmAnimConfig.Config(300, true, 1.0f)
        assertEquals(0L, HokmAnimConfig.effectiveDuration(c))
    }

    @Test
    fun `effective duration scaled`() {
        val c = HokmAnimConfig.Config(300, false, 0.5f)
        assertEquals(150L, HokmAnimConfig.effectiveDuration(c))
    }

    @Test
    fun `should animate false when reduced motion`() {
        val c = HokmAnimConfig.Config(300, true, 1.0f)
        assertTrue(!HokmAnimConfig.shouldAnimate(c))
    }

    @Test
    fun `should animate true normal`() {
        val c = HokmAnimConfig.Config(300, false, 1.0f)
        assertTrue(HokmAnimConfig.shouldAnimate(c))
    }

    @Test
    fun `should animate false when speed zero`() {
        val c = HokmAnimConfig.Config(300, false, 0f)
        assertTrue(!HokmAnimConfig.shouldAnimate(c))
    }

    @Test
    fun `clamp multiplier`() {
        assertEquals(3f, HokmAnimConfig.clampMultiplier(5f))
        assertEquals(0f, HokmAnimConfig.clampMultiplier(-1f))
    }
}
