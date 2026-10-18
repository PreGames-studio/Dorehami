package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class HokmMaintenanceTest {

    @Test
    fun `inactive by default`() {
        val s = HokmMaintenance.Status(false, "", null)
        assertTrue(!HokmMaintenance.isActive(s))
        assertTrue(!HokmMaintenance.shouldBlockPlay(s))
    }

    @Test
    fun `active blocks play`() {
        val s = HokmMaintenance.Status(true, "down", null)
        assertTrue(HokmMaintenance.isActive(s))
        assertTrue(HokmMaintenance.shouldBlockPlay(s))
    }

    @Test
    fun `remaining ms computed`() {
        val s = HokmMaintenance.Status(true, "down", 1000)
        assertEquals(400L, HokmMaintenance.remainingMs(s, 600))
    }

    @Test
    fun `remaining ms null when no end`() {
        val s = HokmMaintenance.Status(true, "down", null)
        assertNull(HokmMaintenance.remainingMs(s, 0))
    }

    @Test
    fun `remaining ms clamped at 0`() {
        val s = HokmMaintenance.Status(true, "down", 500)
        assertEquals(0L, HokmMaintenance.remainingMs(s, 1000))
    }

    @Test
    fun `default message non-empty`() {
        assertTrue(HokmMaintenance.defaultMessage().isNotEmpty())
    }
}
