package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmDepthTest {

    @Test
    fun `dragging highest z`() {
        assertTrue(HokmDepth.zForCard(false, true) > HokmDepth.zForCard(true, false))
    }

    @Test
    fun `selected higher than normal`() {
        assertTrue(HokmDepth.zForCard(true, false) > HokmDepth.zForCard(false, false))
    }

    @Test
    fun `elevation selected higher`() {
        assertTrue(HokmDepth.elevation(true) > HokmDepth.elevation(false))
    }
}
