package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmRewardTest {

    @Test
    fun `kot reward highest`() {
        assertTrue(HokmReward.matchReward(true, true) > HokmReward.matchReward(true, false))
    }

    @Test
    fun `loss reward small`() {
        assertEquals(10, HokmReward.matchReward(false, false))
    }

    @Test
    fun `daily reward scales with streak`() {
        assertTrue(HokmReward.dailyReward(3) > HokmReward.dailyReward(1))
    }
}
