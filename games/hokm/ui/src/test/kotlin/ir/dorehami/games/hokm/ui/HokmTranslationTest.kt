package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTranslationTest {

    @Test
    fun `lerp at 0 returns a`() {
        val a = HokmTranslation.Point(0f, 0f)
        val b = HokmTranslation.Point(10f, 10f)
        assertEquals(a, HokmTranslation.lerp(a, b, 0f))
    }

    @Test
    fun `lerp at 1 returns b`() {
        val a = HokmTranslation.Point(0f, 0f)
        val b = HokmTranslation.Point(10f, 10f)
        assertEquals(b, HokmTranslation.lerp(a, b, 1f))
    }

    @Test
    fun `lerp midpoint`() {
        val a = HokmTranslation.Point(0f, 0f)
        val b = HokmTranslation.Point(10f, 10f)
        val m = HokmTranslation.lerp(a, b, 0.5f)
        assertEquals(5f, m.x)
        assertEquals(5f, m.y)
    }

    @Test
    fun `distance`() {
        val a = HokmTranslation.Point(0f, 0f)
        val b = HokmTranslation.Point(3f, 4f)
        assertEquals(5f, HokmTranslation.distance(a, b), 0.001f)
    }
}
