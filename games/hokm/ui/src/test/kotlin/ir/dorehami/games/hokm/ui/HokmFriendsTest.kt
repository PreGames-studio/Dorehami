package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmFriendsTest {

    @Test
    fun `add friend`() {
        val list = HokmFriends.add(emptyList(), HokmFriends.Friend("f1", "ali"))
        assertEquals(1, list.size)
    }

    @Test
    fun `add duplicate ignored`() {
        var list = HokmFriends.add(emptyList(), HokmFriends.Friend("f1", "ali"))
        list = HokmFriends.add(list, HokmFriends.Friend("f1", "ali"))
        assertEquals(1, list.size)
    }

    @Test
    fun `remove friend`() {
        var list = HokmFriends.add(emptyList(), HokmFriends.Friend("f1", "ali"))
        list = HokmFriends.remove(list, "f1")
        assertEquals(0, list.size)
    }

    @Test
    fun `online count`() {
        val list = listOf(HokmFriends.Friend("f1", "ali", true), HokmFriends.Friend("f2", "reza", false))
        assertEquals(1, HokmFriends.onlineCount(list))
    }

    @Test
    fun `find friend`() {
        val list = listOf(HokmFriends.Friend("f1", "ali"))
        assertEquals("ali", HokmFriends.find(list, "f1")?.name)
        assertNull(HokmFriends.find(list, "x"))
    }
}
