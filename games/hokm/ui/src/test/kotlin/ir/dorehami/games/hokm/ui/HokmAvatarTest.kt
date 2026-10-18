package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmAvatarTest {

    @Test
    fun `valid id`() {
        assertTrue(HokmAvatar.isValid(0))
        assertTrue(HokmAvatar.isValid(11))
        assertFalse(HokmAvatar.isValid(12))
    }

    @Test
    fun `clamp`() {
        assertEquals(0, HokmAvatar.clamp(-1))
        assertEquals(11, HokmAvatar.clamp(99))
        assertEquals(5, HokmAvatar.clamp(5))
    }

    @Test
    fun `name non-empty`() {
        assertTrue(HokmAvatar.name(0).isNotEmpty())
    }
}
