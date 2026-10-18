package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmGradientTest {

    @Test
    fun `two stop gradient`() {
        val g = HokmGradient.twoStopGradient("#FF0000", "#0000FF")
        assertTrue(HokmGradient.isValid(g))
        assertTrue(g.size == 2)
    }
}
