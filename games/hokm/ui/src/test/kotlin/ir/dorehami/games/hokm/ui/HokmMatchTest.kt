package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmMatchTest {

    @Test
    fun `initial phase dealing`() {
        assertEquals(HokmMatch.Phase.DEALING, HokmMatch.Match().phase)
    }

    @Test
    fun `set trump moves to playing`() {
        val m = HokmMatch.setTrump(HokmMatch.Match())
        assertEquals(HokmMatch.Phase.PLAYING, m.phase)
    }

    @Test
    fun `end hand updates score`() {
        val m = HokmMatch.Match(phase = HokmMatch.Phase.PLAYING)
        val m2 = HokmMatch.endHand(m, 5, 2)
        assertTrue(m2.ourScore > 0)
    }

    @Test
    fun `match over at 7`() {
        val m = HokmMatch.Match(ourScore = 6, theirScore = 0)
        val m2 = HokmMatch.endHand(m, 5, 2)
        assertTrue(HokmMatch.isOver(m2))
    }

    @Test
    fun `match not over early`() {
        val m = HokmMatch.Match(ourScore = 0, theirScore = 0)
        val m2 = HokmMatch.endHand(m, 5, 2)
        assertTrue(!HokmMatch.isOver(m2))
    }

    @Test
    fun `start hand increments hand number`() {
        val m = HokmMatch.Match(handNumber = 1)
        val m2 = HokmMatch.startHand(m)
        assertEquals(2, m2.handNumber)
    }
}
