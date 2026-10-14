package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HandPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * HK-013 — the fixed baseline scenario must be *fixed*: same seed ⇒ byte-identical table states,
 * a finished match, and coverage of the situations the visual/performance/accessibility baseline
 * has to show. If a rules change alters the scenario, these tests change the recorded fingerprint
 * loudly instead of letting two measurements silently describe different games.
 */
class HokmBaselineScenarioTest {

    private val four = HokmBaselineScenario.fourPlayer
    private val two = HokmBaselineScenario.twoPlayer

    // ------------------------------------------------------------------ fixed, not drifting

    @Test
    fun `regenerating the same scenario is byte identical`() {
        val again = HokmBaselineScenario.generate()
        assertEquals(four.fingerprint, again.fingerprint, "the baseline scenario must be deterministic")
        assertEquals(four.stateDigest(), again.stateDigest())
        assertEquals(four.finalDigest, again.finalDigest)
        assertEquals(four.steps.size, again.steps.size)
    }

    @Test
    fun `a different seed is a different scenario`() {
        val other = HokmBaselineScenario.generate(seed = HokmBaselineScenario.SEED + 1)
        assertNotEquals(four.fingerprint, other.fingerprint, "the fingerprint must notice another deal")
        assertNotEquals(four.stateDigest(), other.stateDigest())
    }

    @Test
    fun `the recorded fingerprints are the ones in BASELINE md`() {
        // Pinned here so a rules/bot change cannot quietly redefine what every past measurement meant.
        assertEquals(
            "827a56d2d3d0dc0869ce5641920d781c424f9e3e4e29c449c6d26863e905c167",
            four.fingerprint,
            "four-player baseline fingerprint changed — update BASELINE.md and re-take the device measurements",
        )
        assertEquals(
            "963ec691f3be2145a1ba3f6db13aa1e135cec52d20dab365ed589a7849c43f74",
            two.fingerprint,
            "two-player baseline fingerprint changed — update BASELINE.md and re-take the device measurements",
        )
    }

    // ------------------------------------------------------------------ a real, finished match

    @Test
    fun `the scenario plays a whole match to a result`() {
        assertTrue(four.finished, "the four-player scenario must reach the match result")
        assertTrue(two.finished, "the two-player scenario must reach the match result")
        assertEquals(9, four.hands, "hands played in the four-player baseline")
        assertEquals(11, two.hands, "hands played in the two-player baseline")
        assertTrue(four.steps.isNotEmpty() && two.steps.isNotEmpty())
    }

    @Test
    fun `every captured view is the real typed snapshot of that seat`() {
        // capture() already checks this with check(); this test proves the steps are complete and
        // ordered, which is what makes the scenario usable as a frame-by-frame script on a device.
        four.steps.forEachIndexed { i, step ->
            assertEquals(i, step.index, "steps must be numbered in order")
            assertEquals(i + 1, step.engineVersion.toInt(), "one accepted action per step")
            assertEquals(4, step.states.size, "every seat's view is captured at every step")
            step.states.forEachIndexed { seat, snap ->
                assertEquals(seat, snap.mySeat, "seat $seat must see its own view")
                assertEquals(4, snap.playerCount)
            }
        }
    }

    // ------------------------------------------------------------------ coverage of the table

    @Test
    fun `every seat acts and holds a full hand`() {
        assertEquals(setOf(0, 1, 2, 3), HokmBaselineScenario.actingSeats(four), "all four seats must move")
        assertEquals(13, HokmBaselineScenario.maxHandSize(four), "a four-player deal is 13 cards")
        assertNotNull(HokmBaselineScenario.firstStepWithHand(four, 13), "the fan at full size must occur")
    }

    @Test
    fun `the four player scenario covers choosing hokm and playing`() {
        assertEquals(setOf(HandPhase.CHOOSING_HOKM, HandPhase.PLAYING), four.phases)
        assertEquals(listOf("choose_hokm", "play_card"), HokmBaselineScenario.actionTypes(four))
    }

    @Test
    fun `the two player scenario adds burning and drawing`() {
        assertEquals(
            setOf(HandPhase.CHOOSING_HOKM, HandPhase.DISCARDING, HandPhase.DRAWING, HandPhase.PLAYING),
            two.phases,
            "the two-player variant must cover every hand phase",
        )
        val actions = HokmBaselineScenario.actionTypes(two)
        assertTrue(actions.containsAll(listOf("choose_hokm", "discard", "keep_card", "pass_card", "play_card")), "got $actions")
        // Measured, not assumed: burning removes 3+2 and drawing refills, so the widest fan the
        // table ever has to lay out is 13 in both scenarios (the ROADMAP's 17-card case does not
        // occur under the shipped two-player rules — recorded in BASELINE.md as a measured fact).
        assertEquals(13, HokmBaselineScenario.maxHandSize(two), "the two-player fan peaks at 13 cards")
    }

    // ------------------------------------------------------------------ usable as a harness input

    @Test
    fun `generating the scenario is fast enough to run inside checkAll`() {
        val t0 = System.nanoTime()
        HokmBaselineScenario.generate()
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue(ms < 15_000, "scenario generation took ${ms}ms — too slow for the CI gate")
    }

    @Test
    fun `the steps expose a stable script for a device harness`() {
        // What a frame/memory/screenshot run walks: index, actor, action type and the four views.
        val script = four.steps.joinToString("|") { "${it.index}:${it.actorSeat}:${it.actionType}" }
        assertEquals(script, HokmBaselineScenario.generate().steps.joinToString("|") { "${it.index}:${it.actorSeat}:${it.actionType}" })
        assertTrue(four.stateDigest().length == 64, "the state digest is a sha256 hex string")
        assertTrue(393 == four.steps.size, "step count of the four-player baseline")
        assertTrue(534 == two.steps.size, "step count of the two-player baseline")
    }
}
