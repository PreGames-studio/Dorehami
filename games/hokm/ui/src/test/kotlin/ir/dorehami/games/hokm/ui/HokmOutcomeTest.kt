package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmOutcomeTest {

    @Test
    fun `ok err`() {
        val r = HokmOutcome.ok(42)
        assertTrue(HokmOutcome.isOk(r))
        assertEquals(42, HokmOutcome.getOrNull(r))
        val e = HokmOutcome.err("boom")
        assertTrue(HokmOutcome.isErr(e))
        assertEquals("boom", HokmOutcome.errorOrNull(e))
    }

    @Test
    fun `getOrNull on err`() {
        assertEquals(null, HokmOutcome.getOrNull(HokmOutcome.err("x")))
    }

    @Test
    fun `errorOrNull on ok`() {
        assertEquals(null, HokmOutcome.errorOrNull(HokmOutcome.ok(1)))
    }
}
