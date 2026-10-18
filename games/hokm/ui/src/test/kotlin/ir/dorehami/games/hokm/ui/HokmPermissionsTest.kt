package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmPermissionsTest {

    @Test
    fun `initial not granted`() {
        val s = HokmPermissions.State()
        assertTrue(!HokmPermissions.isGranted(s, HokmPermissions.Permission.MICROPHONE))
    }

    @Test
    fun `update granted`() {
        val s = HokmPermissions.update(HokmPermissions.State(), HokmPermissions.Permission.MICROPHONE, HokmPermissions.Status.GRANTED)
        assertTrue(HokmPermissions.isGranted(s, HokmPermissions.Permission.MICROPHONE))
    }

    @Test
    fun `rationale on denied`() {
        val s = HokmPermissions.update(HokmPermissions.State(), HokmPermissions.Permission.MICROPHONE, HokmPermissions.Status.DENIED)
        assertTrue(HokmPermissions.shouldShowRationale(s, HokmPermissions.Permission.MICROPHONE))
    }

    @Test
    fun `allGranted check`() {
        var s = HokmPermissions.State()
        s = HokmPermissions.update(s, HokmPermissions.Permission.MICROPHONE, HokmPermissions.Status.GRANTED)
        s = HokmPermissions.update(s, HokmPermissions.Permission.NOTIFICATIONS, HokmPermissions.Status.GRANTED)
        assertTrue(HokmPermissions.allGranted(s, listOf(HokmPermissions.Permission.MICROPHONE, HokmPermissions.Permission.NOTIFICATIONS)))
    }

    @Test
    fun `allGranted false when missing`() {
        val s = HokmPermissions.State()
        assertTrue(!HokmPermissions.allGranted(s, listOf(HokmPermissions.Permission.MICROPHONE)))
    }

    @Test
    fun `rationale text non-empty`() {
        HokmPermissions.Permission.values().forEach { p ->
            assertTrue(HokmPermissions.rationaleText(p).isNotEmpty())
        }
    }
}
