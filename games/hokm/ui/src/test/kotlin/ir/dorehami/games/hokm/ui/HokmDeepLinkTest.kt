package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmDeepLinkTest {

    @Test
    fun `parse join link`() {
        val a = HokmDeepLink.parse("hokm://join/ABC123")
        assertTrue(a is HokmDeepLink.Action.JoinRoom)
        assertEquals("ABC123", (a as HokmDeepLink.Action.JoinRoom).code)
    }

    @Test
    fun `parse profile link`() {
        val a = HokmDeepLink.parse("hokm://profile/u42")
        assertTrue(a is HokmDeepLink.Action.OpenProfile)
    }

    @Test
    fun `parse home link`() {
        val a = HokmDeepLink.parse("hokm://home")
        assertTrue(a is HokmDeepLink.Action.OpenHome)
    }

    @Test
    fun `parse unknown`() {
        val a = HokmDeepLink.parse("https://example.com")
        assertTrue(a is HokmDeepLink.Action.Unknown)
    }

    @Test
    fun `parse empty unknown`() {
        val a = HokmDeepLink.parse("")
        assertTrue(a is HokmDeepLink.Action.Unknown)
    }

    @Test
    fun `build join link`() {
        assertEquals("hokm://join/XYZ", HokmDeepLink.buildJoinLink("XYZ"))
    }
}
