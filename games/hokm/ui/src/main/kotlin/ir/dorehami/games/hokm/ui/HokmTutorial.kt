package ir.dorehami.games.hokm.ui

/**
 * HK-021 (first slice) — short interactive tutorial as **pure, unit-testable** logic.
 *
 * The roadmap asks that a new user learn Hokm and Kot, that the tutorial be skippable and
 * resumable, and that it work with TalkBack and silent mode. This object defines the step
 * list, progression, and skip/resume policy. The Compose layer renders each step.
 */
object HokmTutorial {

    enum class Step {
        WELCOME, DEAL, TRUMP, FOLLOW_SUIT, TRICK_WIN, SCORE, KOT, DONE
    }

    data class State(
        val current: Step = Step.WELCOME,
        val completed: Set<Step> = emptySet(),
        val skipped: Boolean = false
    )

    /** Ordered list of steps. */
    val ORDER: List<Step> = listOf(
        Step.WELCOME, Step.DEAL, Step.TRUMP, Step.FOLLOW_SUIT,
        Step.TRICK_WIN, Step.SCORE, Step.KOT, Step.DONE
    )

    fun next(s: State): State {
        if (s.skipped) return s
        val idx = ORDER.indexOf(s.current)
        if (idx < 0 || idx >= ORDER.lastIndex) return s.copy(current = Step.DONE)
        val nextStep = ORDER[idx + 1]
        return s.copy(current = nextStep, completed = s.completed + s.current)
    }

    fun skip(s: State): State = s.copy(skipped = true, current = Step.DONE)

    fun resume(s: State): State = s.copy(skipped = false)

    fun isComplete(s: State): Boolean = s.current == Step.DONE

    fun progressPercent(s: State): Int {
        val idx = ORDER.indexOf(s.current)
        if (idx < 0) return 100
        return (idx * 100) / (ORDER.size - 1)
    }

    /** Persian title for each step. */
    fun title(step: Step): String = when (step) {
        Step.WELCOME -> "خوش آمدید"
        Step.DEAL -> "پخش کارت"
        Step.TRUMP -> "انتخاب حکم"
        Step.FOLLOW_SUIT -> "هم‌رنگ بازی کردن"
        Step.TRICK_WIN -> "بردن دست"
        Step.SCORE -> "امتیاز"
        Step.KOT -> "کوت"
        Step.DONE -> "پایان آموزش"
    }

    /** Whether the step needs sound (silent mode skips audio). */
    fun needsSound(step: Step): Boolean = step != Step.WELCOME && step != Step.DONE
}
