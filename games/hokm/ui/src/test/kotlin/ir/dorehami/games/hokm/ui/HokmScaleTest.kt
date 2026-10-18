package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmScaleTest {

    @Test
    fun `clamp range`() {
        assertEquals(0.5f, HokmScale.clamp(0.1f))
        assertEquals(2f, HokmScale.clamp(3f))
        assertEquals(1.5f, HokmScale.clamp(1.5f))
    }

    @Test
    fun `selected scale`() {
        assertTrue(HokmScale.selectedScale(true) > 1f)
        assertEquals(1f, HokmScale.selectedScale(false))
    }

    @Test
    fun `pressed scale`() {
        assertTrue(HokmScale.pressedScale(true) < 1f)
        assertEquals(1f, HokmScale.pressedScale(false))
    }
}
