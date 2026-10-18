package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmIdTest {

    @Test
    fun `generate length 8`() {
        val id = HokmId.generate(42)
        assertEquals(8, id.length)
    }

    @Test
    fun `generate valid`() {
        val id = HokmId.generate(42)
        assertTrue(HokmId.isValid(id))
    }

    @Test
    fun `generate deterministic`() {
        assertEquals(HokmId.generate(42), HokmId.generate(42))
    }
}
