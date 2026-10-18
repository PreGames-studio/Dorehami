package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmShadowTest {

    @Test
    fun `elevated shadow larger blur`() {
        val e = HokmShadow.cardShadow(true)
        val n = HokmShadow.cardShadow(false)
        assertTrue(e.blur > n.blur)
    }

    @Test
    fun `shadows visible`() {
        assertTrue(HokmShadow.isVisible(HokmShadow.cardShadow(true)))
        assertTrue(HokmShadow.isVisible(HokmShadow.contactShadow()))
    }
}
