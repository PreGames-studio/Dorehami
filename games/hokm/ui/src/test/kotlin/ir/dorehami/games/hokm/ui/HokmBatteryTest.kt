package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmBatteryTest {

    @Test
    fun `normal level`() {
        assertEquals(HokmBattery.Level.NORMAL, HokmBattery.level(50))
    }

    @Test
    fun `low level`() {
        assertEquals(HokmBattery.Level.LOW, HokmBattery.level(15))
    }

    @Test
    fun `critical level`() {
        assertEquals(HokmBattery.Level.CRITICAL, HokmBattery.level(5))
    }

    @Test
    fun `reduce effects on low`() {
        assertTrue(HokmBattery.shouldReduceEffects(HokmBattery.Level.LOW))
        assertTrue(!HokmBattery.shouldReduceEffects(HokmBattery.Level.NORMAL))
    }

    @Test
    fun `reduce animations only critical`() {
        assertTrue(HokmBattery.shouldReduceAnimations(HokmBattery.Level.CRITICAL))
        assertTrue(!HokmBattery.shouldReduceAnimations(HokmBattery.Level.LOW))
    }
}
