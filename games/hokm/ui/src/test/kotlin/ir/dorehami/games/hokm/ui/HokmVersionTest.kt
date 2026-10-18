package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse

class HokmVersionTest {

    @Test
    fun `parse valid`() {
        val v = HokmVersion.parse("1.2.3")
        assertEquals(HokmVersion.Version(1, 2, 3), v)
    }

    @Test
    fun `parse invalid returns null`() {
        assertNull(HokmVersion.parse("abc"))
        assertNull(HokmVersion.parse("1.2"))
    }

    @Test
    fun `version compare`() {
        assertTrue(HokmVersion.Version(1, 2, 3) < HokmVersion.Version(1, 2, 4))
        assertTrue(HokmVersion.Version(2, 0, 0) > HokmVersion.Version(1, 9, 9))
    }

    @Test
    fun `policy force when below minimum`() {
        val cur = HokmVersion.Version(1, 0, 0)
        val min = HokmVersion.Version(1, 1, 0)
        val latest = HokmVersion.Version(1, 2, 0)
        assertEquals(HokmVersion.UpdatePolicy.FORCE, HokmVersion.policy(cur, min, latest))
    }

    @Test
    fun `policy optional when below latest`() {
        val cur = HokmVersion.Version(1, 1, 0)
        val min = HokmVersion.Version(1, 0, 0)
        val latest = HokmVersion.Version(1, 2, 0)
        assertEquals(HokmVersion.UpdatePolicy.OPTIONAL, HokmVersion.policy(cur, min, latest))
    }

    @Test
    fun `policy none when current`() {
        val cur = HokmVersion.Version(1, 2, 0)
        val min = HokmVersion.Version(1, 0, 0)
        val latest = HokmVersion.Version(1, 2, 0)
        assertEquals(HokmVersion.UpdatePolicy.NONE, HokmVersion.policy(cur, min, latest))
    }

    @Test
    fun `isSupported`() {
        assertTrue(HokmVersion.isSupported(HokmVersion.Version(1, 1, 0), HokmVersion.Version(1, 0, 0)))
        assertFalse(HokmVersion.isSupported(HokmVersion.Version(0, 9, 0), HokmVersion.Version(1, 0, 0)))
    }
}
