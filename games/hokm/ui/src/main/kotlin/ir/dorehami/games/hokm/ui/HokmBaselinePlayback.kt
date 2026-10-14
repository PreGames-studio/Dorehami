package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HandPhase

/**
 * HK-013.a — the *playback plan* that turns the fixed baseline scenario (HK-013) into a script a
 * device harness (or a human with `adb`) can walk frame by frame.
 *
 * The scenario already fixes *what* happens; this file fixes *how it is shown for measurement*:
 * which seat's eyes we look through, how long each step stays up, and — most importantly — the
 * named checkpoints (BASELINE.md §4.8) at which frame timing is reset and screenshots are taken.
 * Checkpoints are derived from the captured snapshots themselves, never hand-picked step
 * numbers, so a rules change that moves a moment also moves its checkpoint, loudly, in tests.
 *
 * Pure JVM: the harness in `src/debug` only renders what this plan says.
 */
object HokmBaselinePlayback {

    /** Default dwell per step when the harness walks the whole match (≈4 min for 4p is too long
     * for a quick look, so operators pass a shorter one; frame runs use this default). */
    const val DEFAULT_STEP_MS = 250L

    /** How much longer a checkpoint stays up, so `adb screencap` can catch it reliably. */
    const val DEFAULT_CHECKPOINT_HOLD_MS = 1_500L

    /** One named measurement moment in the plan (BASELINE.md §4.8's fixed stations). */
    data class Checkpoint(
        /** Stable machine id, used in harness logs (`checkpoint=<id> step=<n>`). */
        val id: String,
        /** Persian label for the operator. */
        val titleFa: String,
        /** Step in the scenario this moment is first true at. */
        val stepIndex: Int,
        /** The seat whose view proves the moment (always the plan's view seat). */
        val seat: Int,
    )

    /** Everything a harness needs to replay one scenario deterministically. */
    data class Plan(
        val scenario: HokmBaselineScenario.Scenario,
        /** The seat the harness renders the table for. */
        val viewSeat: Int,
        val stepMs: Long,
        val checkpointHoldMs: Long,
        val checkpoints: List<Checkpoint>,
    ) {
        /** Total steps the harness will show. */
        val stepCount: Int get() = scenario.steps.size

        /** The checkpoint (if any) that becomes true exactly at [stepIndex]. */
        fun checkpointAt(stepIndex: Int): Checkpoint? = checkpoints.firstOrNull { it.stepIndex == stepIndex }

        /** How long [stepIndex] stays on screen: checkpoints hold longer for screenshots. */
        fun dwellMs(stepIndex: Int): Long = if (checkpointAt(stepIndex) != null) checkpointHoldMs else stepMs

        /** The snapshot the harness renders at [stepIndex]: the view seat's own table. */
        fun snapshotAt(stepIndex: Int): TableSnapshot = scenario.steps[stepIndex].states[viewSeat]
    }

    /**
     * Builds the plan for [scenario] as seen by [viewSeat]. Checkpoint moments that a scenario
     * never reaches are simply absent — the plan never invents a station (the two-player-only
     * burning/drawing stations, for example, do not appear in the four-player plan).
     */
    fun plan(
        scenario: HokmBaselineScenario.Scenario,
        viewSeat: Int = 0,
        stepMs: Long = DEFAULT_STEP_MS,
        checkpointHoldMs: Long = DEFAULT_CHECKPOINT_HOLD_MS,
    ): Plan {
        require(viewSeat in 0 until scenario.playerCount) { "view seat $viewSeat is not at this table" }
        require(stepMs > 0) { "step dwell must be positive" }
        require(checkpointHoldMs >= stepMs) { "a checkpoint must hold at least as long as a step" }

        val steps = scenario.steps
        fun firstIndexWhere(predicate: (HokmBaselineScenario.Step) -> Boolean): Int? =
            steps.firstOrNull(predicate)?.index

        val found = ArrayList<Checkpoint>()
        fun add(id: String, titleFa: String, stepIndex: Int?) {
            if (stepIndex != null) found += Checkpoint(id, titleFa, stepIndex, viewSeat)
        }

        // The nine stations of BASELINE.md §4.8, in scenario order of first appearance below;
        // the plan re-sorts them by step so the harness meets them chronologically.
        add(
            "full_hand", "دست کامل ۱۳ کارت",
            firstIndexWhere { it.states[viewSeat].myHand.size == HokmBaselineScenario.maxHandSize(scenario) },
        )
        add(
            "choose_hokm", "نخستین انتخاب حکم",
            firstIndexWhere { it.actionType == "choose_hokm" },
        )
        add(
            "mid_trick", "میانهٔ ترفند",
            firstIndexWhere { it.states[viewSeat].currentTrick.size == 2 },
        )
        add(
            "trick_completed", "ترفند کامل و جمع‌شده",
            firstIndexWhere { it.states[viewSeat].lastTrick.size == scenario.playerCount },
        )
        add(
            "hand_finished", "پایان یک دست (بنر)",
            firstIndexWhere { it.states[viewSeat].lastHand != null },
        )
        add(
            "discard", "سوزاندن کارت (دونفره)",
            firstIndexWhere { it.states[viewSeat].handPhase == HandPhase.DISCARDING },
        )
        add(
            "draw", "کشیدن از دسته (دونفره)",
            firstIndexWhere { it.states[viewSeat].handPhase == HandPhase.DRAWING },
        )
        add("match_finished", "صفحهٔ پایان مسابقه", steps.lastOrNull()?.index)

        val ordered = found.sortedWith(compareBy({ it.stepIndex }, { it.id }))
        return Plan(scenario, viewSeat, stepMs, checkpointHoldMs, ordered)
    }

    /** The primary (four-player) reference plan. */
    val fourPlayer: Plan by lazy { plan(HokmBaselineScenario.fourPlayer) }

    /** The two-player variant plan (adds the burning/drawing stations). */
    val twoPlayer: Plan by lazy { plan(HokmBaselineScenario.twoPlayer, viewSeat = 0) }
}
