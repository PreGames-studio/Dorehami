package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmInviteTest {

    @Test
    fun `create invite`() {
        val inv = HokmInvite.create("ABC123", "ali", "reza", 0)
        assertTrue(HokmInvite.isValid(inv))
    }

    @Test
    fun `accept invite`() {
        val inv = HokmInvite.create("ABC123", "ali", "reza", 0)
        val accepted = HokmInvite.accept(inv)
        assertTrue(accepted.accepted)
    }

    @Test
    fun `not expired within ttl`() {
        val inv = HokmInvite.create("ABC123", "ali", "reza", 0)
        assertTrue(!HokmInvite.isExpired(inv, 1000))
    }

    @Test
    fun `expired after ttl`() {
        val inv = HokmInvite.create("ABC123", "ali", "reza", 0)
        assertTrue(HokmInvite.isExpired(inv, 400000))
    }
}
