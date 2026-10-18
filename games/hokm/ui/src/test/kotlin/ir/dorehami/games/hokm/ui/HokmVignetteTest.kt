package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmVignetteTest {

    @Test
    fun `opacity high`() {
        assertEquals(0.6f, HokmVignette.opacity("high"))
    }

    @Test
    fun `enabled check`() {
        assertTrue(HokmVignette.isEnabled("high"))
        assertTrue(!HokmVignette.isEnabled("off"))
    }
}
