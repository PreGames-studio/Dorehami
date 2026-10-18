package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSpectateTest {

    @Test
    fun `can spectate when allowed`() {
        assertTrue(HokmSpectate.canSpectate(true, true))
        assertTrue(!HokmSpectate.canSpectate(true, false))
    }

    @Test
    fun `add spectator`() {
        val list = HokmSpectate.addSpectator(emptyList(), HokmSpectate.Spectator("s1", "ali"))
        assertEquals(1, HokmSpectate.count(list))
    }

    @Test
    fun `add duplicate ignored`() {
        var list = HokmSpectate.addSpectator(emptyList(), HokmSpectate.Spectator("s1", "ali"))
        list = HokmSpectate.addSpectator(list, HokmSpectate.Spectator("s1", "ali"))
        assertEquals(1, HokmSpectate.count(list))
    }

    @Test
    fun `remove spectator`() {
        var list = HokmSpectate.addSpectator(emptyList(), HokmSpectate.Spectator("s1", "ali"))
        list = HokmSpectate.removeSpectator(list, "s1")
        assertEquals(0, HokmSpectate.count(list))
    }
}
