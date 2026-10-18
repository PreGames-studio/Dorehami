package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmTutorialTest {

    @Test
    fun `starts at welcome`() {
        assertEquals(HokmTutorial.Step.WELCOME, HokmTutorial.State().current)
    }

    @Test
    fun `next advances`() {
        val s = HokmTutorial.next(HokmTutorial.State())
        assertEquals(HokmTutorial.Step.DEAL, s.current)
    }

    @Test
    fun `full progression reaches done`() {
        var s = HokmTutorial.State()
        repeat(HokmTutorial.ORDER.size) { s = HokmTutorial.next(s) }
        assertTrue(HokmTutorial.isComplete(s))
    }

    @Test
    fun `skip marks skipped and done`() {
        val s = HokmTutorial.skip(HokmTutorial.State())
        assertTrue(s.skipped)
        assertTrue(HokmTutorial.isComplete(s))
    }

    @Test
    fun `skipped state does not advance`() {
        val s = HokmTutorial.skip(HokmTutorial.State())
        val s2 = HokmTutorial.next(s)
        assertTrue(s2.skipped)
    }

    @Test
    fun `resume clears skipped`() {
        val s = HokmTutorial.resume(HokmTutorial.skip(HokmTutorial.State()))
        assertTrue(!s.skipped)
    }

    @Test
    fun `progress increases`() {
        val s0 = HokmTutorial.State()
        val s1 = HokmTutorial.next(s0)
        assertTrue(HokmTutorial.progressPercent(s1) > HokmTutorial.progressPercent(s0))
    }

    @Test
    fun `progress 100 at done`() {
        assertEquals(100, HokmTutorial.progressPercent(HokmTutorial.State(current = HokmTutorial.Step.DONE)))
    }

    @Test
    fun `titles non-empty`() {
        HokmTutorial.Step.values().forEach { assertTrue(HokmTutorial.title(it).isNotEmpty()) }
    }

    @Test
    fun `completed set grows`() {
        val s = HokmTutorial.next(HokmTutorial.State())
        assertTrue(s.completed.contains(HokmTutorial.Step.WELCOME))
    }
}
