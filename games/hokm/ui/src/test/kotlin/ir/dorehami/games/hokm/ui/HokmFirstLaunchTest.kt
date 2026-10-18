package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmFirstLaunchTest {

    @Test
    fun `first launch true initially`() {
        assertTrue(HokmFirstLaunch.isFirstLaunch(HokmFirstLaunch.State()))
    }

    @Test
    fun `mark launched`() {
        val s = HokmFirstLaunch.markLaunched(HokmFirstLaunch.State(), "1.0.0")
        assertTrue(!HokmFirstLaunch.isFirstLaunch(s))
        assertEquals("1.0.0", s.version)
    }

    @Test
    fun `needs onboarding first launch`() {
        assertTrue(HokmFirstLaunch.needsOnboarding(HokmFirstLaunch.State()))
    }

    @Test
    fun `needs migration on version change`() {
        val s = HokmFirstLaunch.markLaunched(HokmFirstLaunch.State(), "1.0.0")
        assertTrue(HokmFirstLaunch.needsMigration(s, "1.1.0"))
    }

    @Test
    fun `no migration same version`() {
        val s = HokmFirstLaunch.markLaunched(HokmFirstLaunch.State(), "1.0.0")
        assertTrue(!HokmFirstLaunch.needsMigration(s, "1.0.0"))
    }

    @Test
    fun `no migration first launch`() {
        assertTrue(!HokmFirstLaunch.needsMigration(HokmFirstLaunch.State(), "1.0.0"))
    }
}
