package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmRankTest {

    @Test
    fun `all has 13 ranks`() {
        assertEquals(13, HokmRank.ALL.size)
    }

    @Test
    fun `ace is ace`() {
        assertTrue(HokmRank.isAce('A'))
        assertTrue(!HokmRank.isAce('K'))
    }

    @Test
    fun `face cards`() {
        assertTrue(HokmRank.isFace('K'))
        assertTrue(HokmRank.isFace('Q'))
        assertTrue(HokmRank.isFace('J'))
        assertTrue(!HokmRank.isFace('A'))
    }

    @Test
    fun `valid rank`() {
        assertTrue(HokmRank.isValid('A'))
        assertTrue(!HokmRank.isValid('X'))
    }

    @Test
    fun `ace strongest (lowest value)`() {
        assertTrue(HokmRank.value('A') < HokmRank.value('2'))
    }

    @Test
    fun `labels non-empty`() {
        HokmRank.ALL.forEach { r ->
            assertTrue(HokmRank.labelFa(r).isNotEmpty())
        }
    }
}
