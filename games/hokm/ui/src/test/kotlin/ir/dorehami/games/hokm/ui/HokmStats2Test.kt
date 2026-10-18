package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmStats2Test {

    @Test
    fun `mean`() {
        assertEquals(2f, HokmStats2.mean(listOf(1f, 2f, 3f)))
    }

    @Test
    fun `mean empty`() {
        assertEquals(0f, HokmStats2.mean(emptyList()))
    }

    @Test
    fun `max`() {
        assertEquals(5f, HokmStats2.max(listOf(1f, 5f, 3f)))
    }

    @Test
    fun `min`() {
        assertEquals(1f, HokmStats2.min(listOf(1f, 5f, 3f)))
    }

    @Test
    fun `clamp01`() {
        assertEquals(0f, HokmStats2.clamp01(-0.5f))
        assertEquals(1f, HokmStats2.clamp01(1.5f))
        assertEquals(0.5f, HokmStats2.clamp01(0.5f))
    }
}
