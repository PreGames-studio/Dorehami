package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class HokmStickersTest {

    @Test
    fun `catalog has 12 stickers`() {
        assertEquals(12, HokmStickers.count())
    }

    @Test
    fun `byCode finds sticker`() {
        assertNotNull(HokmStickers.byCode("thumbs_up"))
    }

    @Test
    fun `byCode null for unknown`() {
        assertTrue(HokmStickers.byCode("nope") == null)
    }

    @Test
    fun `can send initially`() {
        val r = HokmStickers.canSend(0, 3000, 0)
        assertTrue(r.allowed)
    }

    @Test
    fun `cooldown blocks rapid send`() {
        val r = HokmStickers.canSend(1000, 1500, 0)
        assertTrue(!r.allowed)
    }

    @Test
    fun `max stickers blocks`() {
        val r = HokmStickers.canSend(0, 3000, HokmStickers.MAX_STICKERS_PER_MATCH)
        assertTrue(!r.allowed)
    }

    @Test
    fun `all codes non-empty`() {
        HokmStickers.allCodes().forEach { assertTrue(it.isNotEmpty()) }
    }

    @Test
    fun `sticker labels non-empty`() {
        HokmStickers.Sticker.values().forEach { assertTrue(it.labelFa.isNotEmpty()) }
    }
}
