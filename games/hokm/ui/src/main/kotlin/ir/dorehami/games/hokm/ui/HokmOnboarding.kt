package ir.dorehami.games.hokm.ui

/**
 * HK-021 (onboarding slice) — first-launch onboarding as **pure, unit-testable** logic.
 *
 * The roadmap asks for short interactive tutorial. This object tracks whether the user
 * has completed onboarding, the current step, and skip state. The Android layer renders.
 */
object HokmOnboarding {

    data class State(
        val completed: Boolean = false,
        val step: Int = 0,
        val skipped: Boolean = false,
        val totalSteps: Int = 5
    )

    fun initial(): State = State()

    fun advance(s: State): State {
        if (s.completed || s.skipped) return s
        val next = s.step + 1
        return if (next >= s.totalSteps) s.copy(step = s.totalSteps, completed = true) else s.copy(step = next)
    }

    fun skip(s: State): State = s.copy(skipped = true, completed = true)

    fun reset(s: State): State = State(totalSteps = s.totalSteps)

    fun progress(s: State): Float =
        if (s.totalSteps == 0) 1f else s.step.toFloat() / s.totalSteps.toFloat()

    fun isDone(s: State): Boolean = s.completed || s.skipped
}
