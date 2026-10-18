package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmAchievementTest {

    @Test
    fun `first win locked initially`() {
        assertTrue(!HokmAchievement.isUnlocked(HokmAchievement.Achievement.FIRST_WIN, HokmStats.Stats()))
    }

    @Test
    fun `first win unlocked after win`() {
        val s = HokmStats.recordWin(HokmStats.Stats())
        assertTrue(HokmAchievement.isUnlocked(HokmAchievement.Achievement.FIRST_WIN, s))
    }

    @Test
    fun `kot master needs 5 kots`() {
        var s = HokmStats.Stats()
        repeat(5) { s = HokmStats.recordWin(s, kot = true) }
        assertTrue(HokmAchievement.isUnlocked(HokmAchievement.Achievement.KOT_MASTER, s))
    }

    @Test
    fun `unlocked count`() {
        val s = HokmStats.recordWin(HokmStats.Stats())
        assertEquals(1, HokmAchievement.unlockedCount(s))
    }
}
