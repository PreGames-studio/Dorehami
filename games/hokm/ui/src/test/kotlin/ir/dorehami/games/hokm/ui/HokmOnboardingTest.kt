package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmOnboardingTest {

    @Test
    fun `initial not done`() {
        val s = HokmOnboarding.initial()
        assertTrue(!HokmOnboarding.isDone(s))
    }

    @Test
    fun `advance completes after total steps`() {
        var s = HokmOnboarding.initial()
        repeat(5) { s = HokmOnboarding.advance(s) }
        assertTrue(HokmOnboarding.isDone(s))
    }

    @Test
    fun `skip marks done`() {
        val s = HokmOnboarding.skip(HokmOnboarding.initial())
        assertTrue(HokmOnboarding.isDone(s))
        assertTrue(s.skipped)
    }

    @Test
    fun `advance after done is no-op`() {
        val s = HokmOnboarding.skip(HokmOnboarding.initial())
        val s2 = HokmOnboarding.advance(s)
        assertEquals(s, s2)
    }

    @Test
    fun `progress increases`() {
        val s0 = HokmOnboarding.initial()
        val s1 = HokmOnboarding.advance(s0)
        assertTrue(HokmOnboarding.progress(s1) > HokmOnboarding.progress(s0))
    }

    @Test
    fun `reset clears`() {
        val s = HokmOnboarding.reset(HokmOnboarding.skip(HokmOnboarding.initial()))
        assertTrue(!HokmOnboarding.isDone(s))
    }
}
