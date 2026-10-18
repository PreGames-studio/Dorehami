package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmSchemaTest {

    @Test
    fun `non empty`() {
        assertTrue(HokmSchema.isNonEmpty("x"))
        assertFalse(HokmSchema.isNonEmpty("   "))
    }

    @Test
    fun `range`() {
        assertTrue(HokmSchema.inRange(5, 1, 10))
        assertFalse(HokmSchema.inRange(0, 1, 10))
    }

    @Test
    fun `pattern`() {
        assertTrue(HokmSchema.matchesPattern("abc", Regex("[a-z]+")))
        assertFalse(HokmSchema.matchesPattern("123", Regex("[a-z]+")))
    }
}
