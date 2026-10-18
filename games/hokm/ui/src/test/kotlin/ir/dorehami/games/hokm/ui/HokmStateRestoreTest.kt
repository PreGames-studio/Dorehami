package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmStateRestoreTest {

    @Test
    fun `empty when no save`() {
        assertEquals(HokmStateRestore.Result.EMPTY, HokmStateRestore.decide(false, false))
    }

    @Test
    fun `restored when valid`() {
        assertEquals(HokmStateRestore.Result.RESTORED, HokmStateRestore.decide(true, true))
    }

    @Test
    fun `failed when invalid`() {
        assertEquals(HokmStateRestore.Result.FAILED, HokmStateRestore.decide(true, false))
    }

    @Test
    fun `messages non-empty`() {
        HokmStateRestore.Result.values().forEach { r ->
            assertTrue(HokmStateRestore.message(r).isNotEmpty())
        }
    }
}
