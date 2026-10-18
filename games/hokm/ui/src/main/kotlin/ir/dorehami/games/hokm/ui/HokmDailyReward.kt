package ir.dorehami.games.hokm.ui

/**
 * HK-048 (daily reward slice) — daily reward as **pure, unit-testable** logic.
 *
 * The roadmap mentions daily rewards (Hokm Plus). This object defines the reward
 * schedule, streak tracking, and claim eligibility. The Android layer drives the clock.
 */
object HokmDailyReward {

    data class State(
        val lastClaimDay: Int = -1,
        val streak: Int = 0,
        val totalClaimed: Int = 0
    )

    /** Reward amounts by streak day (1-indexed), capping at day 7. */
    val REWARDS: List<Int> = listOf(10, 15, 20, 25, 30, 40, 60)

    fun rewardForDay(day: Int): Int {
        val idx = (day - 1).coerceIn(0, REWARDS.lastIndex)
        return REWARDS[idx]
    }

    fun canClaim(state: State, todayDay: Int): Boolean = todayDay > state.lastClaimDay

    fun claim(state: State, todayDay: Int): State {
        if (!canClaim(state, todayDay)) return state
        val newStreak = if (state.lastClaimDay == todayDay - 1) state.streak + 1 else 1
        val cappedStreak = newStreak.coerceAtMost(REWARDS.size)
        return state.copy(
            lastClaimDay = todayDay,
            streak = cappedStreak,
            totalClaimed = state.totalClaimed + rewardForDay(cappedStreak)
        )
    }

    fun currentStreak(state: State): Int = state.streak

    fun isStreakBroken(state: State, todayDay: Int): Boolean =
        state.lastClaimDay >= 0 && todayDay - state.lastClaimDay > 1
}
