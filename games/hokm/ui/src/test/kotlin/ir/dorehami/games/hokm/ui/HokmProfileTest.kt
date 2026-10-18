package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmProfileTest {

    @Test
    fun `sanitize trims name`() {
        val p = HokmProfile.sanitize(HokmProfile.Profile(name = "  ali  "))
        assertEquals("ali", p.name)
    }

    @Test
    fun `sanitize short name defaults`() {
        val p = HokmProfile.sanitize(HokmProfile.Profile(name = "a"))
        assertEquals("بازیکن", p.name)
    }

    @Test
    fun `sanitize clamps avatar`() {
        val p = HokmProfile.sanitize(HokmProfile.Profile(avatarId = 99))
        assertTrue(p.avatarId < HokmProfile.AVATAR_COUNT)
    }

    @Test
    fun `valid name check`() {
        assertTrue(HokmProfile.isValidName("ali"))
        assertFalse(HokmProfile.isValidName("a"))
        assertFalse(HokmProfile.isValidName("a".repeat(21)))
    }

    @Test
    fun `valid avatar check`() {
        assertTrue(HokmProfile.isValidAvatar(0))
        assertTrue(HokmProfile.isValidAvatar(11))
        assertFalse(HokmProfile.isValidAvatar(12))
    }

    @Test
    fun `addXp levels up`() {
        val p = HokmProfile.Profile(xp = 90)
        val p2 = HokmProfile.addXp(p, 20)
        assertEquals(2, p2.level)
        assertEquals(10, p2.xp)
    }

    @Test
    fun `addXp no level up`() {
        val p = HokmProfile.Profile(xp = 10, level = 1)
        val p2 = HokmProfile.addXp(p, 5)
        assertEquals(1, p2.level)
        assertEquals(15, p2.xp)
    }

    @Test
    fun `recordMatch increments`() {
        val p = HokmProfile.Profile(matchesPlayed = 3)
        assertEquals(4, HokmProfile.recordMatch(p).matchesPlayed)
    }

    @Test
    fun `xp progress in range`() {
        val p = HokmProfile.Profile(xp = 50)
        assertTrue(HokmProfile.xpProgress(p) in 0f..1f)
    }

    @Test
    fun `display name includes level`() {
        val p = HokmProfile.Profile(name = "ali", level = 3)
        assertTrue(HokmProfile.displayName(p).contains("ali"))
    }
}
