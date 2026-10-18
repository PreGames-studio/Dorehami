package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmSnapshotTest {

    @Test
    fun `newer when no current`() {
        val s = HokmSnapshot.Snapshot(1, "d", 0)
        assertTrue(HokmSnapshot.isNewer(s, null))
    }

    @Test
    fun `newer when higher version`() {
        val cur = HokmSnapshot.Snapshot(1, "d", 0)
        val inc = HokmSnapshot.Snapshot(2, "d2", 1)
        assertTrue(HokmSnapshot.isNewer(inc, cur))
    }

    @Test
    fun `not newer when lower version`() {
        val cur = HokmSnapshot.Snapshot(2, "d", 0)
        val inc = HokmSnapshot.Snapshot(1, "d2", 1)
        assertTrue(!HokmSnapshot.isNewer(inc, cur))
    }

    @Test
    fun `should apply same as newer`() {
        val cur = HokmSnapshot.Snapshot(1, "d", 0)
        val inc = HokmSnapshot.Snapshot(2, "d2", 1)
        assertTrue(HokmSnapshot.shouldApply(inc, cur))
    }

    @Test
    fun `format non-empty`() {
        val s = HokmSnapshot.Snapshot(1, "d", 0)
        assertTrue(HokmSnapshot.format(s).isNotEmpty())
    }
}
