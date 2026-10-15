package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HK-013.a — the playback plan is what a device harness walks; it must be a faithful,
 * deterministic reading of the fixed scenario, with its measurement checkpoints on real
 * moments of the match (BASELINE.md §4.8), never on invented frames.
 */
class HokmBaselinePlaybackTest {

    private val four = HokmBaselinePlayback.fourPlayer
    private val two = HokmBaselinePlayback.twoPlayer

    @Test
    fun `the plan walks every step of the scenario exactly once`() {
        assertEquals(393, four.stepCount)
        assertEquals(534, two.stepCount)
        for (i in 0 until four.stepCount) {
            assertEquals(four.scenario.steps[i].states[0], four.snapshotAt(i), "frame $i must be the view seat's real snapshot")
        }
    }

    @Test
    fun `checkpoints sit on real moments and in chronological order`() {
        for (plan in listOf(four, two)) {
            val indices = plan.checkpoints.map { it.stepIndex }
            assertEquals(indices.sorted(), indices, "checkpoints must be chronological")
            assertEquals(
                plan.checkpoints.map { it.id }.toSet().size,
                plan.checkpoints.size,
                "checkpoint ids must be unique",
            )
            assertTrue(indices.all { it in 0 until plan.stepCount }, "checkpoints must stay inside the scenario")
        }
    }

    @Test
    fun `the four player plan has the shared stations but no two-player ones`() {
        val ids = four.checkpoints.map { it.id }
        assertTrue(ids.containsAll(listOf("full_hand", "choose_hokm", "mid_trick", "trick_completed", "hand_finished", "match_finished")), "got $ids")
        assertTrue("discard" !in ids && "draw" !in ids, "two-player stations must not leak into the four-player plan: $ids")
    }

    @Test
    fun `the two player plan adds burning and drawing stations`() {
        val ids = two.checkpoints.map { it.id }
        assertTrue(ids.containsAll(listOf("choose_hokm", "discard", "draw", "hand_finished", "match_finished")), "got $ids")
    }

    @Test
    fun `each checkpoint proves its own moment`() {
        val byId = four.checkpoints.associateBy { it.id }
        val fullHand = assertNotNull(byId["full_hand"])
        assertEquals(13, four.snapshotAt(fullHand.stepIndex).myHand.size)
        val choose = assertNotNull(byId["choose_hokm"])
        assertEquals("choose_hokm", four.scenario.steps[choose.stepIndex].actionType)
        val mid = assertNotNull(byId["mid_trick"])
        assertEquals(2, four.snapshotAt(mid.stepIndex).currentTrick.size)
        val done = assertNotNull(byId["trick_completed"])
        assertEquals(4, four.snapshotAt(done.stepIndex).lastTrick.size)
        val hand = assertNotNull(byId["hand_finished"])
        assertNotNull(four.snapshotAt(hand.stepIndex).lastHand)
        val match = assertNotNull(byId["match_finished"])
        assertEquals(four.stepCount - 1, match.stepIndex)
        assertTrue(four.snapshotAt(match.stepIndex).finished)
    }

    @Test
    fun `checkpoints hold longer than ordinary steps`() {
        val checkpointStep = four.checkpoints.first().stepIndex
        assertEquals(HokmBaselinePlayback.DEFAULT_CHECKPOINT_HOLD_MS, four.dwellMs(checkpointStep))
        val plainStep = (0 until four.stepCount).first { four.checkpointAt(it) == null }
        assertEquals(HokmBaselinePlayback.DEFAULT_STEP_MS, four.dwellMs(plainStep))
        assertNull(four.checkpointAt(plainStep))
    }

    @Test
    fun `the view seat is honoured and validated`() {
        val fromSeat2 = HokmBaselinePlayback.plan(HokmBaselineScenario.fourPlayer, viewSeat = 2)
        assertEquals(2, fromSeat2.snapshotAt(0).mySeat)
        assertTrue(fromSeat2.checkpoints.all { it.seat == 2 })
        assertFailsWith<IllegalArgumentException> {
            HokmBaselinePlayback.plan(HokmBaselineScenario.fourPlayer, viewSeat = 4)
        }
        assertFailsWith<IllegalArgumentException> {
            HokmBaselinePlayback.plan(HokmBaselineScenario.fourPlayer, stepMs = 0)
        }
    }

    @Test
    fun `rebuilding the plan is deterministic`() {
        val again = HokmBaselinePlayback.plan(HokmBaselineScenario.generate())
        assertEquals(four.checkpoints, again.checkpoints, "the same scenario must produce the same measurement script")
    }
}
