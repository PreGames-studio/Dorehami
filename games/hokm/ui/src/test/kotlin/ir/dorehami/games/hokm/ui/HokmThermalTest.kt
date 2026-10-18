package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmThermalTest {

    @Test
    fun `cool at low temp`() {
        assertEquals(HokmThermal.Level.COOL, HokmThermal.fromTemperature(30f))
    }

    @Test
    fun `hot at 42`() {
        assertEquals(HokmThermal.Level.HOT, HokmThermal.fromTemperature(42f))
    }

    @Test
    fun `critical at 46`() {
        assertEquals(HokmThermal.Level.CRITICAL, HokmThermal.fromTemperature(46f))
    }

    @Test
    fun `reduce effects on hot`() {
        assertTrue(HokmThermal.shouldReduceEffects(HokmThermal.Level.HOT))
        assertTrue(!HokmThermal.shouldReduceEffects(HokmThermal.Level.COOL))
    }

    @Test
    fun `reduce animations only critical`() {
        assertTrue(HokmThermal.shouldReduceAnimations(HokmThermal.Level.CRITICAL))
        assertTrue(!HokmThermal.shouldReduceAnimations(HokmThermal.Level.HOT))
    }
}
