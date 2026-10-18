package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmLobbyTest {

    @Test
    fun `create room has 4 empty seats`() {
        val r = HokmLobby.createRoom("ABC123")
        assertEquals(4, r.seats.size)
        assertTrue(r.seats.all { it.status == HokmLobby.SeatStatus.EMPTY })
    }

    @Test
    fun `fill seat`() {
        val r = HokmLobby.createRoom("ABC123")
        val r2 = HokmLobby.fillSeat(r, 0, "ali")
        assertEquals("ali", r2.seats[0].name)
        assertEquals(HokmLobby.SeatStatus.FILLED, r2.seats[0].status)
    }

    @Test
    fun `add bot`() {
        val r = HokmLobby.createRoom("ABC123")
        val r2 = HokmLobby.addBot(r, 1)
        assertEquals(HokmLobby.SeatStatus.BOT, r2.seats[1].status)
    }

    @Test
    fun `isFull false when empty`() {
        val r = HokmLobby.createRoom("ABC123")
        assertTrue(!HokmLobby.isFull(r))
    }

    @Test
    fun `isFull true when all filled`() {
        var r = HokmLobby.createRoom("ABC123")
        for (i in 0..3) r = HokmLobby.fillSeat(r, i, "p$i")
        assertTrue(HokmLobby.isFull(r))
    }

    @Test
    fun `canStart requires ready`() {
        var r = HokmLobby.createRoom("ABC123")
        for (i in 0..3) r = HokmLobby.fillSeat(r, i, "p$i")
        assertTrue(!HokmLobby.canStart(r))
        for (i in 0..3) r = HokmLobby.markReady(r, i)
        assertTrue(HokmLobby.canStart(r))
    }

    @Test
    fun `bot counts as ready`() {
        var r = HokmLobby.createRoom("ABC123")
        for (i in 0..3) r = HokmLobby.addBot(r, i)
        assertTrue(HokmLobby.allReady(r))
    }

    @Test
    fun `empty seat count`() {
        val r = HokmLobby.createRoom("ABC123")
        assertEquals(4, HokmLobby.emptySeatCount(r))
    }

    @Test
    fun `valid code check`() {
        assertTrue(HokmLobby.isValidCode("ABC123"))
        assertFalse(HokmLobby.isValidCode("AB"))
        assertFalse(HokmLobby.isValidCode("ABCDE!"))
    }

    @Test
    fun `filled seat count`() {
        var r = HokmLobby.createRoom("ABC123")
        r = HokmLobby.fillSeat(r, 0, "ali")
        assertEquals(1, HokmLobby.filledSeatCount(r))
    }
}
