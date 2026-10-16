package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * GX-04 guard: the timing table stays honest.
 *
 * These tests are the reason the table can be trusted without a device: shape (phases tile the
 * beat), sound names that exist, reduced-motion variants that keep their information, and a clock
 * that can be skipped or resynced without ever replaying a cue or hanging the table.
 */
class HokmChoreographyTest {

    private val events = HokmChoreography.Event.entries

    // ------------------------------------------------------------------ the table's shape

    @Test
    fun `every event has exactly one row with a stable unique id`() {
        val beats = HokmChoreography.beats
        assertEquals(events.size, beats.size)
        assertEquals(events.toList(), beats.map { it.event })
        val ids = beats.map { it.event.id }
        assertEquals(ids.size, ids.toSet().size, "event ids must be unique")
        assertTrue(ids.all { it.matches(Regex("[a-z][a-z_]*")) }, "ids are machine ids: $ids")
        assertTrue(beats.all { it.event.titleFa.isNotBlank() }, "every event needs a Persian label")
    }

    @Test
    fun `no beat can hold the table longer than the budget`() {
        HokmChoreography.beats.forEach { beat ->
            assertTrue(beat.totalMs in 0..HokmChoreography.MAX_EVENT_MS, "${beat.event.id}: ${beat.totalMs}ms")
            if (beat.event.kind != HokmChoreography.BeatKind.DECORATION) {
                assertTrue(beat.totalMs > 0, "${beat.event.id} is not decoration, so it must take time")
            }
        }
    }

    @Test
    fun `phases tile the whole beat without a gap or an overlap`() {
        HokmChoreography.beats.forEach { beat ->
            val phases = beat.phases
            assertTrue(phases.isNotEmpty(), "${beat.event.id} has no phases")
            assertEquals(0, phases.first().fromMs, "${beat.event.id} must start at 0")
            assertEquals(beat.totalMs, phases.last().toMs, "${beat.event.id} must end at totalMs")
            phases.forEach { phase ->
                assertTrue(phase.fromMs <= phase.toMs, "${beat.event.id}/${phase.id} runs backwards")
                assertTrue(phase.durationMs > 0, "${beat.event.id}/${phase.id} is empty")
                assertTrue(phase.titleFa.isNotBlank(), "${beat.event.id}/${phase.id} needs a label")
            }
            phases.zipWithNext().forEach { (a, b) ->
                assertEquals(a.toMs, b.fromMs, "${beat.event.id}: ${a.id} → ${b.id} must touch")
            }
            assertEquals(phases.size, phases.map { it.id }.toSet().size, "${beat.event.id}: duplicate phase id")
        }
    }

    @Test
    fun `cue ids are unique inside a beat and across the whole table`() {
        val all = ArrayList<String>()
        HokmChoreography.beats.forEach { beat ->
            val ids = beat.cues.map { it.id }
            assertEquals(ids.size, ids.toSet().size, "${beat.event.id}: duplicate cue id")
            all += ids
        }
        assertEquals(all.size, all.toSet().size, "a cue id must belong to exactly one beat")
    }

    @Test
    fun `cues land inside their beat in firing order`() {
        HokmChoreography.beats.forEach { beat ->
            beat.cues.forEach { cue ->
                assertTrue(cue.atMs in 0..beat.totalMs, "${beat.event.id}/${cue.id} at ${cue.atMs}ms")
            }
            val offsets = beat.cues.map { it.atMs }
            assertEquals(offsets.sorted(), offsets, "${beat.event.id}: cues must be declared in order")
        }
    }

    @Test
    fun `every cue names something that exists`() {
        val haptics = setOf("card_lift", "card_contact", "cut")
        val fx = setOf("cut", "trick_win", "confetti")
        HokmChoreography.beats.forEach { beat ->
            beat.cues.forEach { cue ->
                when (cue.kind) {
                    HokmChoreography.CueKind.SOUND -> assertTrue(
                        cue.name in HokmChoreography.SOUNDS,
                        "${beat.event.id}/${cue.id}: `${cue.name}` is not a bundled sound",
                    )
                    HokmChoreography.CueKind.HAPTIC -> assertTrue(
                        cue.name in haptics,
                        "${beat.event.id}/${cue.id}: `${cue.name}` is not a known haptic slot",
                    )
                    HokmChoreography.CueKind.FX -> assertTrue(
                        cue.name in fx,
                        "${beat.event.id}/${cue.id}: `${cue.name}` is not a known fx id",
                    )
                }
            }
        }
    }

    @Test
    fun `the bundled sound list is the shipped nineteen`() {
        assertEquals(19, HokmChoreography.SOUNDS.size)
        // The sounds the table actually fires today must all be declared.
        listOf("card_play", "card_cut", "card_slide", "whoosh", "trick_won", "trick_lost", "shuffle", "card_deal", "hand_won", "kot", "game_won")
            .forEach { assertTrue(it in HokmChoreography.SOUNDS, "$it missing from SOUNDS") }
    }

    // ------------------------------------------------------------------ pinned pacing

    @Test
    fun `the shipped pacing did not move`() {
        fun total(event: HokmChoreography.Event) = HokmChoreography.durationMs(event)
        assertEquals(1150, total(HokmChoreography.Event.TRICK_SHOW))
        assertEquals(520, total(HokmChoreography.Event.COLLECT))
        assertEquals(420, total(HokmChoreography.Event.HAKEM_DRAW))
        assertEquals(1400, total(HokmChoreography.Event.DEAL))
        assertEquals(2600, total(HokmChoreography.Event.HAND_BANNER))
        assertEquals(180, total(HokmChoreography.Event.SELECT))
        // Two rows are GX-02 tokens, so the token and the table can never drift apart.
        assertEquals(HokmTokens.Motion.CARD_MS, total(HokmChoreography.Event.PLAY))
        assertEquals(HokmTokens.Motion.COLLECT_MS, total(HokmChoreography.Event.COLLECT))
    }

    @Test
    fun `the deal starts with a shuffle phase and the shuffle sound sits on it`() {
        val deal = HokmChoreography.beat(HokmChoreography.Event.DEAL)
        val riffle = assertNotNull(deal.phase("riffle"))
        assertEquals(0, riffle.fromMs)
        val shuffleCue = assertNotNull(deal.cue("deal.shuffle"))
        assertEquals("shuffle", shuffleCue.name)
        assertEquals(riffle.fromMs, shuffleCue.atMs)
        // The cards only start coming out when the riffle ends.
        assertEquals(riffle.toMs, assertNotNull(deal.cue("deal.out")).atMs)
    }

    @Test
    fun `the play sound fires when the card touches the felt, not when it leaves the hand`() {
        val play = HokmChoreography.beat(HokmChoreography.Event.PLAY)
        val travel = assertNotNull(play.phase("travel"))
        val slide = assertNotNull(play.cue("play.slide"))
        val contact = assertNotNull(play.cue("play.contact"))
        val haptic = assertNotNull(play.cue("play.contact.haptic"))
        assertEquals(0, slide.atMs, "the slide starts with the movement")
        assertEquals(travel.toMs, contact.atMs, "contact is the end of the travel phase")
        assertEquals(contact.atMs, haptic.atMs, "the click and the tick must land together")
        assertTrue(contact.atMs > slide.atMs, "contact must follow the throw")
        assertTrue(contact.atMs < play.totalMs, "the card still settles after contact")
    }

    @Test
    fun `a trump cut renames the click and adds the spark, it never doubles a sound`() {
        val play = HokmChoreography.beat(HokmChoreography.Event.PLAY)
        val cut = play.withSound("play.contact", "card_cut")
        assertEquals("card_cut", assertNotNull(cut.cue("play.contact")).name)
        assertEquals("card_slide", assertNotNull(cut.cue("play.slide")).name)
        assertEquals(1, cut.cues.count { it.kind == HokmChoreography.CueKind.SOUND && it.name == "card_cut" })
        // The spark and the stronger buzz belong to the cut; a plain throw drops both.
        assertNotNull(cut.cue("play.cut"))
        assertNotNull(cut.cue("play.cut.haptic"))
        val plain = play.withoutCues("play.cut", "play.cut.haptic")
        assertNull(plain.cue("play.cut"))
        assertNull(plain.cue("play.cut.haptic"))
        assertEquals(cut.cues.size - 2, plain.cues.size, "dropping a cut removes exactly its two cues")
        // The failure mode we care about: renaming a slot that does not exist must not be silent.
        assertFailsWith<IllegalArgumentException> { cut.withSound("play.nope", "card_cut") }
        assertFailsWith<IllegalArgumentException> { play.withoutCues() .withSound("play.cut", "card_cut") }
    }

    @Test
    fun `the slots the table renames all exist`() {
        // Exactly the renames the table performs when the game decides the sound for us.
        listOf(
            Triple(HokmChoreography.Event.PLAY, "play.contact", "card_cut"),
            Triple(HokmChoreography.Event.COLLECT, "collect.result", "trick_lost"),
            Triple(HokmChoreography.Event.HAND_BANNER, "banner.in", "hand_lost"),
            Triple(HokmChoreography.Event.MATCH_BANNER, "match.in", "game_lost"),
        ).forEach { (event, slot, name) ->
            assertTrue(name in HokmChoreography.SOUNDS, "$name must be a shipped sound")
            val renamed = HokmChoreography.beat(event).withSound(slot, name)
            assertEquals(name, assertNotNull(renamed.cue(slot)).name, "$slot")
        }
    }

    @Test
    fun `decoration lifetimes stay bounded and longer than the beat that starts them`() {
        HokmChoreography.Fx.ALL_MS.forEach { ms -> assertTrue(ms in 100..4_000, "fx lifetime $ms") }
        // The cut flash and the celebration burst must outlive the cue that starts them.
        assertTrue(HokmChoreography.Fx.CUT_MS >= HokmChoreography.durationMs(HokmChoreography.Event.PLAY))
        assertTrue(HokmChoreography.Fx.TRICK_WIN_MS >= HokmChoreography.durationMs(HokmChoreography.Event.COLLECT))
        assertTrue(HokmChoreography.Fx.CONFETTI_MS >= HokmChoreography.durationMs(HokmChoreography.Event.CELEBRATE))
        assertEquals(HokmChoreography.Fx.ALL_MS.size, HokmChoreography.Fx.ALL_MS.toSet().size, "fx lifetimes are distinct")
    }

    @Test
    fun `the short banner keeps its shipped length`() {
        assertEquals(1300, HokmChoreography.durationMs(HokmChoreography.Event.BANNER))
        assertTrue(HokmChoreography.beat(HokmChoreography.Event.BANNER).cues.isEmpty(), "a short banner says nothing")
    }

    @Test
    fun `the screen can ask for the haptic of a movement it owns itself`() {
        assertEquals("card_lift", HokmChoreography.hapticSlot(HokmChoreography.Event.SELECT, "select.lift"))
        assertEquals("card_contact", HokmChoreography.hapticSlot(HokmChoreography.Event.PLAY, "play.contact.haptic"))
        assertEquals("cut", HokmChoreography.hapticSlot(HokmChoreography.Event.PLAY, "play.cut.haptic"))
        // A sound cue is not a haptic, and an unknown cue is not silently invented.
        assertNull(HokmChoreography.hapticSlot(HokmChoreography.Event.PLAY, "play.slide"))
        assertNull(HokmChoreography.hapticSlot(HokmChoreography.Event.PLAY, "play.nope"))
    }

    @Test
    fun `the hakem announcement is a read pause with its own sound`() {
        val beat = HokmChoreography.beat(HokmChoreography.Event.HAKEM_BANNER)
        assertEquals(1500, beat.totalMs)
        assertEquals("hokm_chosen", assertNotNull(beat.cue("hakem.in")).name)
        assertTrue(beat.cue("hakem.in")!!.atMs in 0..beat.totalMs)
    }

    @Test
    fun `every event has a curve for the renderer`() {
        events.forEach { assertNotNull(HokmChoreography.curve(it), it.id) }
        val play = HokmChoreography.beat(HokmChoreography.Event.PLAY)
        assertEquals(HokmChoreography.Curve.DECELERATE, assertNotNull(play.phase("travel")).curve)
        assertEquals(HokmChoreography.Curve.LINEAR, assertNotNull(HokmChoreography.beat(HokmChoreography.Event.DEAL).phase("riffle")).curve)
    }

    // ------------------------------------------------------------------ reduced motion

    @Test
    fun `reduced motion compresses movements into the reduced budget`() {
        events.filter { it.kind == HokmChoreography.BeatKind.MOVEMENT }.forEach { event ->
            val full = HokmChoreography.durationMs(event, reducedMotion = false)
            val reduced = HokmChoreography.durationMs(event, reducedMotion = true)
            assertTrue(reduced in 1..HokmMotion.REDUCED_MAX_MS, "${event.id}: $reduced ms is not a compression of $full")
            assertTrue(reduced <= full)
        }
    }

    @Test
    fun `read pauses keep their length under reduced motion`() {
        events.filter { it.kind == HokmChoreography.BeatKind.READ }.forEach { event ->
            val full = HokmChoreography.beat(event, reducedMotion = false)
            val reduced = HokmChoreography.beat(event, reducedMotion = true)
            assertEquals(full.totalMs, reduced.totalMs, "${event.id}: a read pause is information, not motion")
            assertEquals(full.phases, reduced.phases)
            assertEquals(full.cues, reduced.cues)
        }
    }

    @Test
    fun `decoration disappears but what the player must hear survives`() {
        val full = HokmChoreography.beat(HokmChoreography.Event.CELEBRATE, reducedMotion = false)
        val reduced = HokmChoreography.beat(HokmChoreography.Event.CELEBRATE, reducedMotion = true)
        assertEquals(0, reduced.totalMs, "confetti has no reduced-motion length")
        assertTrue(full.cues.any { it.kind == HokmChoreography.CueKind.FX })
        assertFalse(reduced.cues.any { it.kind == HokmChoreography.CueKind.FX }, "fx are decoration, they go")
        assertFalse(HokmChoreography.plays(HokmChoreography.Event.CELEBRATE, reducedMotion = true))
        assertTrue(HokmChoreography.plays(HokmChoreography.Event.CELEBRATE, reducedMotion = false))
    }

    @Test
    fun `compression never deletes a sound or a haptic cue`() {
        events.forEach { event ->
            val full = HokmChoreography.beat(event, reducedMotion = false)
            val reduced = HokmChoreography.beat(event, reducedMotion = true)
            val audible = { kind: HokmChoreography.CueKind -> kind != HokmChoreography.CueKind.FX }
            assertEquals(
                full.cues.filter { audible(it.kind) }.map { it.id },
                reduced.cues.filter { audible(it.kind) }.map { it.id },
                "${event.id}: every cue the table fires must still fire under reduced motion",
            )
            reduced.cues.forEach { cue ->
                assertTrue(cue.atMs in 0..reduced.totalMs, "${event.id}/${cue.id} clamped out of the beat")
            }
        }
    }

    @Test
    fun `reduced phases still tile the compressed beat`() {
        events.forEach { event ->
            val reduced = HokmChoreography.beat(event, reducedMotion = true)
            if (reduced.totalMs == 0) return@forEach
            assertEquals(0, reduced.phases.first().fromMs)
            assertEquals(reduced.totalMs, reduced.phases.last().toMs)
            reduced.phases.zipWithNext().forEach { (a, b) ->
                assertTrue(a.toMs <= b.fromMs, "${event.id}: ${a.id} → ${b.id} overlap after compression")
            }
        }
    }

    @Test
    fun `a shortened movement still plays all of its sound cues`() {
        val beat = HokmChoreography.beat(HokmChoreography.Event.PLAY, reducedMotion = true)
        val cursor = HokmBeatCursor(beat)
        val fired = cursor.finish()
        assertEquals(beat.cues.size, fired.size, "a skipped reduction still plays every cue")
        assertEquals(beat.cues.map { it.id }.toSet(), fired.map { it.id }.toSet())
    }

    // ------------------------------------------------------------------ the clock

    @Test
    fun `the cursor fires every cue exactly once and in order`() {
        HokmChoreography.beats.forEach { beat ->
            val cursor = HokmBeatCursor(beat)
            val fired = ArrayList<HokmChoreography.Cue>()
            // Walk the whole beat in 50ms steps, then run past the end.
            var guard = 0
            while (guard++ < 200) {
                fired += cursor.advance(50)
                if (cursor.elapsedMs >= beat.totalMs) break
            }
            fired += cursor.advance(5_000)
            assertEquals(beat.cues.map { it.id }, fired.map { it.id }, "${beat.event.id}: cue order/duplication")
            assertTrue(cursor.done, "${beat.event.id}: the cursor must finish")
            assertTrue(cursor.advance(10_000).isEmpty(), "${beat.event.id}: nothing replays after the end")
            assertEquals(1f, cursor.progress)
        }
    }

    @Test
    fun `a cue at time zero fires on the first tick, even with no time left`() {
        val beat = HokmChoreography.beat(HokmChoreography.Event.CELEBRATE, reducedMotion = true)
        assertEquals(0, beat.totalMs)
        val cursor = HokmBeatCursor(beat)
        assertTrue(cursor.advance(0).isEmpty(), "reduced celebrate has no cues left")
        assertTrue(cursor.done)
    }

    @Test
    fun `the clock never rewinds, so a resync cannot replay a sound`() {
        val beat = HokmChoreography.beat(HokmChoreography.Event.PLAY)
        val cursor = HokmBeatCursor(beat)
        assertEquals(listOf("play.slide", "play.cut", "play.cut.haptic"), cursor.advance(1).map { it.id })
        // A backwards jump is ignored: there is no "un-play a sound".
        assertTrue(cursor.advance(-100).isEmpty())
        assertEquals(1, cursor.elapsedMs)
        val late = cursor.jumpTo(200)
        assertEquals(listOf("play.contact", "play.contact.haptic"), late.map { it.id })
        assertTrue(cursor.jumpTo(100).isEmpty(), "a stale resync cannot move the clock back")
        assertEquals(200, cursor.elapsedMs)
        assertTrue(cursor.advance(10_000).isEmpty(), "the cues already fired are never replayed")
        assertEquals(beat.totalMs, cursor.elapsedMs)
        assertTrue(cursor.advance(1).isEmpty(), "the second pass over the same cues fires nothing")
    }

    @Test
    fun `skipping a movement still delivers its cues, once`() {
        val beat = HokmChoreography.beat(HokmChoreography.Event.COLLECT)
        val cursor = HokmBeatCursor(beat)
        assertEquals(listOf("collect.whoosh"), cursor.advance(30).map { it.id }, "only the sweep has started")
        val rest = cursor.finish() // the player skipped: the result sound and the spark still land
        assertEquals(listOf("collect.result", "collect.spark"), rest.map { it.id })
        assertTrue(cursor.finish().isEmpty(), "finishing twice must not replay anything")
        assertEquals(beat.cues.map { it.id }, (cursor.fired).map { it.id })
    }

    @Test
    fun `progress is monotone and stays inside the beat`() {
        HokmChoreography.beats.forEach { beat ->
            val cursor = HokmBeatCursor(beat)
            var last = -1f
            repeat(80) {
                val p = cursor.progress
                assertTrue(p in 0f..1f, "${beat.event.id}: progress $p")
                assertTrue(p >= last, "${beat.event.id}: progress went backwards")
                last = p
                cursor.advance(40)
            }
            assertEquals(1f, cursor.progress)
        }
    }

    // ------------------------------------------------------------------ the loop contract

    @Test
    fun `a runner always terminates, so no movement can hang the table`() {
        val modes = listOf(false, true)
        HokmChoreography.beats.forEach { beat ->
            modes.forEach { reduced ->
                val playing = HokmChoreography.beat(beat.event, reducedMotion = reduced)
                val runner = HokmCueRunner(playing)
                val fired = ArrayList<String>()
                var waitedTotal = 0L
                var steps = 0
                while (true) {
                    val wait = runner.waitMs() ?: break
                    assertTrue(wait >= 0, "${beat.event.id}: a negative wait would spin the loop")
                    waitedTotal += wait
                    fired += runner.due(wait).map { it.id }
                    if (++steps > 32) break
                }
                assertTrue(runner.finished, "${beat.event.id} (reduced=$reduced) never finished")
                assertTrue(waitedTotal <= runner.totalMs.toLong(), "${beat.event.id}: waited $waitedTotal > ${runner.totalMs}")
                // Whatever the mode, the runner must deliver exactly the cues the beat declares.
                assertEquals(playing.cues.map { it.id }, fired, "${beat.event.id}: runner fired the wrong cues")
            }
        }
    }

    @Test
    fun `waiting then finishing yields the same cues as waiting alone`() {
        HokmChoreography.beats.forEach { beat ->
            val runner = HokmCueRunner(beat)
            val first = runner.due(runner.waitMs() ?: 0L).map { it.id }
            val rest = runner.finish().map { it.id }
            assertEquals(beat.cues.map { it.id }, first + rest, "${beat.event.id}: skip lost or duplicated a cue")
        }
    }

    @Test
    fun `the runner exposes what the loop needs and nothing more`() {
        val runner = HokmCueRunner(HokmChoreography.beat(HokmChoreography.Event.DEAL))
        assertEquals(1400, runner.totalMs)
        assertEquals(0L, runner.waitMs(), "the shuffle sound is due immediately")
        assertEquals(listOf("deal.shuffle"), runner.due(0).map { it.id })
        assertEquals(260L, runner.waitMs(), "then the loop waits out the riffle")
        assertEquals(listOf("deal.out"), runner.due(260).map { it.id })
        assertEquals(1400L - 260L, runner.waitMs(), "the loop then waits out the deal itself")
        assertTrue(runner.due(1400).isEmpty())
        assertTrue(runner.finished)
        assertNull(runner.waitMs(), "a finished movement has nothing left to wait for")
    }
}
