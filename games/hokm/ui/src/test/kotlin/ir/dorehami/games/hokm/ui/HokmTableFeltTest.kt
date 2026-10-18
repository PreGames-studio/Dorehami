package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTableFeltTest {

    @Test
    fun `default is emerald`() {
        assertEquals(HokmTableFelt.Felt.EMERALD, HokmTableFelt.defaultFelt())
    }

    @Test
    fun `dark velvet is dark`() {
        assertTrue(HokmTableFelt.isDark(HokmTableFelt.Felt.DARK_VELVET))
    }

    @Test
    fun `emerald not dark`() {
        assertTrue(!HokmTableFelt.isDark(HokmTableFelt.Felt.EMERALD))
    }

    @Test
    fun `display names non-empty`() {
        HokmTableFelt.Felt.values().forEach { f ->
            assertTrue(HokmTableFelt.displayName(f).isNotEmpty())
        }
    }
}
