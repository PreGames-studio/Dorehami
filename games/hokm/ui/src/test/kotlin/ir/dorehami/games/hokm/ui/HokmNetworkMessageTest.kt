package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmNetworkMessageTest {

    @Test
    fun `create message`() {
        val m = HokmNetworkMessage.create(HokmNetworkMessage.Type.PLAY, "A♠", "ali", 100)
        assertEquals(HokmNetworkMessage.Type.PLAY, m.type)
        assertEquals("A♠", m.payload)
    }

    @Test
    fun `game message check`() {
        val m = HokmNetworkMessage.create(HokmNetworkMessage.Type.PLAY, "A♠", "ali", 0)
        assertTrue(HokmNetworkMessage.isGameMessage(m))
    }

    @Test
    fun `social message check`() {
        val m = HokmNetworkMessage.create(HokmNetworkMessage.Type.CHAT, "hi", "ali", 0)
        assertTrue(HokmNetworkMessage.isSocialMessage(m))
    }

    @Test
    fun `chat not game message`() {
        val m = HokmNetworkMessage.create(HokmNetworkMessage.Type.CHAT, "hi", "ali", 0)
        assertTrue(!HokmNetworkMessage.isGameMessage(m))
    }
}
