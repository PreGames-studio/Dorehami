package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmHashTest {

    @Test
    fun `deterministic`() {
        assertEquals(HokmHash.djb2("hello"), HokmHash.djb2("hello"))
    }

    @Test
    fun `different inputs different hashes usually`() {
        assertTrue(HokmHash.djb2("hello") != HokmHash.djb2("world"))
    }

    @Test
    fun `is same`() {
        assertTrue(HokmHash.isSame("abc", "abc"))
    }
}
