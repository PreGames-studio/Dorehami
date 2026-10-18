package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmSpringTest {

    @Test
    fun `step moves toward target`() {
        val (v, _) = HokmSpring.step(0f, 10f, 0f, 100f, 10f, 0.016f)
        assertTrue(v > 0f)
    }

    @Test
    fun `settled when close`() {
        assertTrue(HokmSpring.isSettled(10f, 10f, 0f))
    }

    @Test
    fun `not settled when far`() {
        assertTrue(!HokmSpring.isSettled(0f, 10f, 0f))
    }
}
