package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmRankingTest {

    @Test
    fun `rank of top score`() {
        assertEquals(1, HokmRanking.rankOf(100, listOf(90, 80, 70)))
    }

    @Test
    fun `rank of lowest`() {
        assertEquals(4, HokmRanking.rankOf(50, listOf(90, 80, 70)))
    }

    @Test
    fun `percentile`() {
        val p = HokmRanking.percentile(80, listOf(90, 70, 60))
        assertTrue(p in 0f..1f)
    }
}
