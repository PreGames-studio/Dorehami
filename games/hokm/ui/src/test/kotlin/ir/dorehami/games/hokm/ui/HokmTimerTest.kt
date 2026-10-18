package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmTimerTest {

    @Test
    fun `start sets remaining to total`() {
        val s = HokmTimer.start(1000)
        assertEquals(1000L, s.remainingMs)
        assertTrue(s.running)
    }

    @Test
    fun `tick reduces remaining`() {
        val s = HokmTimer.start(1000)
        val s2 = HokmTimer.tick(s, 300)
        assertEquals(700L, s2.remainingMs)
    }

    @Test
    fun `tick expiry`() {
        val s = HokmTimer.start(1000)
        val s2 = HokmTimer.tick(s, 1000)
        assertTrue(s2.expired)
        assertEquals(0L, s2.remainingMs)
    }

    @Test
    fun `pause stops running`() {
        val s = HokmTimer.start(1000)
        val s2 = HokmTimer.pause(s)
        assertTrue(!s2.running)
    }

    @Test
    fun `resume after pause`() {
        val s = HokmTimer.start(1000)
        val s2 = HokmTimer.resume(HokmTimer.pause(s))
        assertTrue(s2.running)
    }

    @Test
    fun `resume after expiry does nothing`() {
        val s = HokmTimer.start(1000)
        val expired = HokmTimer.tick(s, 1000)
        val resumed = HokmTimer.resume(expired)
        assertTrue(resumed.expired)
    }

    @Test
    fun `warning threshold`() {
        val s = HokmTimer.start(10000)
        val s2 = HokmTimer.tick(s, 6000)
        assertTrue(HokmTimer.isWarning(s2))
    }

    @Test
    fun `no warning when plenty time`() {
        val s = HokmTimer.start(30000)
        assertTrue(!HokmTimer.isWarning(s))
    }

    @Test
    fun `progress in range`() {
        val s = HokmTimer.start(1000)
        val s2 = HokmTimer.tick(s, 250)
        assertTrue(HokmTimer.progress(s2) in 0f..1f)
    }

    @Test
    fun `reset restores`() {
        val s = HokmTimer.start(1000)
        val expired = HokmTimer.tick(s, 1000)
        val reset = HokmTimer.reset(expired)
        assertTrue(!reset.expired)
        assertEquals(1000L, reset.remainingMs)
    }
}
