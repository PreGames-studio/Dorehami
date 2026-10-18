package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmCardBackTest {

    @Test
    fun `default back is emerald girih`() {
        assertEquals(HokmCardBack.Design.EMERALD_GIRIH, HokmCardBack.defaultBack().design)
    }

    @Test
    fun `isCustom false for default`() {
        assertTrue(!HokmCardBack.isCustom(HokmCardBack.defaultBack()))
    }

    @Test
    fun `isCustom true with uri`() {
        val b = HokmCardBack.Back(HokmCardBack.Design.CUSTOM_IMAGE, "file://x.png")
        assertTrue(HokmCardBack.isCustom(b))
    }

    @Test
    fun `isCustom false without uri`() {
        val b = HokmCardBack.Back(HokmCardBack.Design.CUSTOM_IMAGE, null)
        assertTrue(!HokmCardBack.isCustom(b))
    }

    @Test
    fun `display names non-empty`() {
        HokmCardBack.Design.values().forEach { d ->
            assertTrue(HokmCardBack.displayName(d).isNotEmpty())
        }
    }
}
