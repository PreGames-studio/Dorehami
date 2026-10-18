package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmPlayerTest {

    private val p0 = HokmPlayer.Player(0, "ali", isMe = true)
    private val p1 = HokmPlayer.Player(1, "reza")
    private val p2 = HokmPlayer.Player(2, "sara")
    private val p3 = HokmPlayer.Player(3, "maryam")

    @Test
    fun `display name me`() {
        assertEquals("شما", HokmPlayer.displayName(p0))
    }

    @Test
    fun `display name other`() {
        assertEquals("reza", HokmPlayer.displayName(p1))
    }

    @Test
    fun `teammates are opposite`() {
        assertTrue(HokmPlayer.isTeammate(p0, p2))
        assertTrue(HokmPlayer.isTeammate(p1, p3))
    }

    @Test
    fun `not teammates adjacent`() {
        assertTrue(!HokmPlayer.isTeammate(p0, p1))
    }

    @Test
    fun `opponents`() {
        assertTrue(HokmPlayer.isOpponent(p0, p1))
        assertTrue(!HokmPlayer.isOpponent(p0, p2))
    }
}
