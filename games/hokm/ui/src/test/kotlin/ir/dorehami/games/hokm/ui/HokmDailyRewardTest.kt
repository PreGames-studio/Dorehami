package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmDailyRewardTest {

    @Test
    fun `can claim initially`() {
        assertTrue(HokmDailyReward.canClaim(HokmDailyReward.State(), 0))
    }

    @Test
    fun `claim updates state`() {
        val s = HokmDailyReward.claim(HokmDailyReward.State(), 0)
        assertEquals(0, s.lastClaimDay)
        assertEquals(1, s.streak)
        assertTrue(s.totalClaimed > 0)
    }

    @Test
    fun `cannot claim same day twice`() {
        val s = HokmDailyReward.claim(HokmDailyReward.State(), 0)
        assertTrue(!HokmDailyReward.canClaim(s, 0))
    }

    @Test
    fun `streak grows on consecutive days`() {
        var s = HokmDailyReward.State()
        s = HokmDailyReward.claim(s, 0)
        s = HokmDailyReward.claim(s, 1)
        assertEquals(2, s.streak)
    }

    @Test
    fun `streak resets after gap`() {
        var s = HokmDailyReward.State()
        s = HokmDailyReward.claim(s, 0)
        s = HokmDailyReward.claim(s, 5)
        assertEquals(1, s.streak)
    }

    @Test
    fun `streak broken detection`() {
        val s = HokmDailyReward.claim(HokmDailyReward.State(), 0)
        assertTrue(HokmDailyReward.isStreakBroken(s, 5))
    }

    @Test
    fun `reward for day 1`() {
        assertEquals(10, HokmDailyReward.rewardForDay(1))
    }

    @Test
    fun `reward caps at day 7`() {
        assertEquals(60, HokmDailyReward.rewardForDay(10))
    }

    @Test
    fun `claim same day no-op`() {
        val s = HokmDailyReward.claim(HokmDailyReward.State(), 0)
        val s2 = HokmDailyReward.claim(s, 0)
        assertEquals(s, s2)
    }
}
