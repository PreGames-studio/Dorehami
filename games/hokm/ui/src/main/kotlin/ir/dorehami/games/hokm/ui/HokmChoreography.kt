package ir.dorehami.games.hokm.ui

/**
 * GX-04 — card-motion choreography as **pure data + pure clock** (no Android, no Compose).
 *
 * Before this file, every movement on the table carried its own literal: a `delay(1150)` for the
 * trick to stay on the felt, `tween(480)` for the sweep, `delay(420)` per flipped hakem card, a
 * sound fired at the moment a card *started* flying instead of when it landed. The roadmap's GX-04
 * asks for one place that answers three questions per event — **how long, what happens when, and
 * what is left of it under reduced motion** — so behaviour can be read, diffed and tested instead
 * of scattered and guessed.
 *
 * Three pieces:
 *
 * * [HokmBeat] — the timing table: one row per event, split into contiguous [HokmPhase]s plus the
 *   [HokmCue]s that must fire at fixed offsets (a card's *contact* sound, its haptic, its spark).
 *   Durations are the ones already shipped (pinned by tests) and named in [HokmTokens.Motion]
 *   where a token exists, so GX-04 changes *structure*, not the feel of the table;
 * * [HokmBeatCursor] / [HokmCueRunner] — the clock. Forward-only, so a skipped movement can be
 *   resumed or dropped without ever replaying a sound, and no movement can hang the table: every
 *   beat ends at a bounded time (see the loop-safety test);
 * * [HokmChoreography.beat] — the reduced-motion variant of each row: movements compress to at
 *   most [HokmMotion.REDUCED_MAX_MS], read pauses keep their length (they *are* information), and
 *   pure decoration disappears — but no cue is ever silently deleted, because a sound is
 *   information too.
 *
 * Design rules this file obeys:
 * * **Presentation never decides the game.** A beat only schedules sound/haptic/fx: hand, turn,
 *   scores and the winner still come from snapshots alone (HK-010).
 * * **Once and only once.** A cue id is unique inside its beat and the cursor never rewinds, so a
 *   resync (a jump forward, a skip, a re-delivered notice) cannot double a sound.
 * * **Sound names are real.** Every SOUND cue names one of the nineteen shipped sounds
 *   ([SOUNDS]); where the choice depends on the game (a trump cut, a lost trick, a kot), the cue
 *   carries the *default* name and the caller renames the slot with [HokmBeat.withSound].
 */
object HokmChoreography {

    /**
     * Longest a movement may take. Anything above this can hold the table (and the felt's next
     * move) behind an animation, which the roadmap forbids; tests pin every beat under it.
     */
    const val MAX_EVENT_MS: Int = 2600

    /**
     * The nineteen sounds bundled in `res/raw` (see [HokmSounds]). A cue may not invent a name:
     * an unknown sound would either be silent or — worse — a typo that nobody notices for months.
     */
    val SOUNDS: Set<String> = setOf(
        "card_play", "card_deal", "shuffle", "trick_won", "trick_lost", "hokm_chosen", "hand_won",
        "hand_lost", "kot", "game_won", "game_lost", "tap", "your_turn", "sticker", "card_cut",
        "card_slide", "timer_tick", "timer_warn", "whoosh",
    )

    /** Cue kinds. A cue is a *moment*, not a UI effect: the table turns it into sound/haptic/fx. */
    enum class CueKind { SOUND, HAPTIC, FX }

    /**
     * One moment inside a beat, at a fixed offset from the beat's start. [name] is the sound name,
     * the haptic slot (`card_lift`, `card_contact`, `cut`) or the fx id (`cut`, `trick_win`).
     */
    data class Cue(val id: String, val kind: CueKind, val name: String, val atMs: Int)

    /** A contiguous stretch of one beat: `[fromMs, toMs)` with its own curve. */
    data class Phase(val id: String, val titleFa: String, val fromMs: Int, val toMs: Int, val curve: Curve) {
        val durationMs: Int get() = toMs - fromMs
    }

    /** Easing families. The Compose layer is the only place that turns these into `Easing`. */
    enum class Curve { STANDARD, DECELERATE, LINEAR }

    /**
     * What a beat is *for*, which is exactly what the reduced-motion policy needs to know:
     * a movement is compressed, a read pause is preserved, decoration is dropped.
     */
    enum class BeatKind {
        /** Cards move: compress to `REDUCED_MAX_MS`, keep every cue. */
        MOVEMENT,

        /** The felt/banner holds still so the player can read it: never shortened. */
        READ,

        /** Sparkles, confetti, endless glows: nothing remains in reduced mode. */
        DECORATION,
    }

    /** Every event the table can play. `id` is stable and appears in logs and the docs table. */
    enum class Event(val id: String, val titleFa: String, val kind: BeatKind) {
        SELECT("select", "بالا آمدن کارتِ زیر انگشت", BeatKind.MOVEMENT),
        PLAY("play", "انداختن کارت روی میز (بریدن = همان پرتاب با صدای و جرقهٔ دیگر)", BeatKind.MOVEMENT),
        COLLECT("collect", "جمع‌کردن ترفند", BeatKind.MOVEMENT),
        DEAL("deal", "برزدن و پخش کارت‌ها", BeatKind.MOVEMENT),
        HAKEM_DRAW("hakem_draw", "تعیین حاکم — رو شدن هر کارت", BeatKind.MOVEMENT),
        TRICK_SHOW("trick_show", "ماندن ترفندِ کامل روی میز", BeatKind.READ),
        HAKEM_BANNER("hakem_banner", "بنر تعیین حاکم", BeatKind.READ),
        BANNER("banner", "بنر کوتاه (حکم، «دست‌ها کامل شد»)", BeatKind.READ),
        HAND_BANNER("hand_banner", "بنر پایان دست", BeatKind.READ),
        MATCH_BANNER("match_banner", "بنر پایان مسابقه", BeatKind.READ),
        CELEBRATE("celebrate", "جرقه/کاغذرنگی برد", BeatKind.DECORATION),
    }

    /** One event's row in the timing table. */
    data class Beat(
        val event: Event,
        val totalMs: Int,
        val phases: List<Phase>,
        val cues: List<Cue>,
    ) {
        /** The phase with [id] (`riffle`, `travel`, `hold`, …). */
        fun phase(id: String): Phase? = phases.firstOrNull { it.id == id }

        /** The cue with [id]. */
        fun cue(id: String): Cue? = cues.firstOrNull { it.id == id }

        /**
         * The same beat with the SOUND cue [slot] renamed to [name] — how the call site resolves a
         * choice the table cannot know (a trump cut, a lost trick, a kot). An unknown [slot] is a
         * programming error and fails loudly instead of silently playing the default sound.
         */
        /**
         * The same beat without the cues [ids] — how a *conditional* moment is dropped: a throw
         * that is not a trump cut must not play the cut's spark or its stronger buzz.
         */
        fun withoutCues(vararg ids: String): Beat = copy(cues = cues.filterNot { it.id in ids })

        fun withSound(slot: String, name: String): Beat {
            require(cues.any { it.id == slot && it.kind == CueKind.SOUND }) {
                "no SOUND cue `$slot` in beat ${event.id}"
            }
            return copy(cues = cues.map { if (it.id == slot) it.copy(name = name) else it })
        }
    }

    /** The phases of the play movement: the travel to the felt, then the settle. */
    private const val PLAY_TRAVEL_MS = 192 // ≈60% of the throw: the moment the card touches the felt

    /**
     * The timing table. Every duration is the one the table already shipped (`TRICK_SHOW_MS`
     * 1150, `COLLECT_MS` 520, `INTRO_CARD_MS` 420, `DEAL_MS` 1400, `HAND_BANNER_MS` 2600) or a
     * named GX-02 token — GX-04 makes them readable and testable, it does not re-time the game.
     */
    private val table: Map<Event, Beat> = listOf(
        Beat(
            event = Event.SELECT,
            totalMs = 180, // HokmTokens.Motion.FADE_MS neighbourhood: a lift must feel instant
            phases = listOf(Phase("lift", "بالا آمدن", 0, 180, Curve.STANDARD)),
            cues = listOf(Cue("select.lift", CueKind.HAPTIC, "card_lift", 0)),
        ),
        Beat(
            event = Event.PLAY,
            totalMs = 320, // HokmTokens.Motion.CARD_MS
            phases = listOf(
                Phase("travel", "رفتن تا وسط میز", 0, PLAY_TRAVEL_MS, Curve.DECELERATE),
                Phase("settle", "نشستن روی نمد", PLAY_TRAVEL_MS, 320, Curve.STANDARD),
            ),
            cues = listOf(
                // Leaves the hand: cloth-on-cloth slide.
                Cue("play.slide", CueKind.SOUND, "card_slide", 0),
                // `بریدن` is not a separate movement: it is this same throw with two swaps — the
                // click becomes a snappier `card_cut` (withSound) and these two conditional cues
                // fire. A throw that is not a cut drops them (withoutCues).
                Cue("play.cut", CueKind.FX, "cut", 0),
                Cue("play.cut.haptic", CueKind.HAPTIC, "cut", 0),
                // Touches the felt: the click and the tick in the hand must land together.
                Cue("play.contact", CueKind.SOUND, "card_play", PLAY_TRAVEL_MS),
                Cue("play.contact.haptic", CueKind.HAPTIC, "card_contact", PLAY_TRAVEL_MS),
            ),
        ),
        Beat(
            event = Event.COLLECT,
            totalMs = 520, // HokmTokens.Motion.COLLECT_MS
            phases = listOf(
                Phase("sweep", "جارو شدن کارت‌ها", 0, 400, Curve.STANDARD),
                Phase("fade", "محو شدن روی برنده", 400, 520, Curve.LINEAR),
            ),
            cues = listOf(
                Cue("collect.whoosh", CueKind.SOUND, "whoosh", 0),
                // The result sound lands a hair after the sweep starts so the two never blur into
                // one noise; `trick_won` is renamed to `trick_lost` when the other side took it.
                Cue("collect.result", CueKind.SOUND, "trick_won", 60),
                Cue("collect.spark", CueKind.FX, "trick_win", 60),
            ),
        ),
        Beat(
            event = Event.DEAL,
            totalMs = 1400, // DEAL_MS: the whole shuffle-and-deal performance
            phases = listOf(
                Phase("riffle", "برزدن (فازِ پخش)", 0, 260, Curve.LINEAR),
                Phase("deal_out", "رو شدن کارت‌ها", 260, 1400, Curve.STANDARD),
            ),
            cues = listOf(
                Cue("deal.shuffle", CueKind.SOUND, "shuffle", 0),
                Cue("deal.out", CueKind.SOUND, "card_deal", 260),
            ),
        ),
        Beat(
            event = Event.HAKEM_DRAW,
            totalMs = 420, // INTRO_CARD_MS per flipped card
            phases = listOf(Phase("flip", "رو شدن کارتِ حاکم", 0, 420, Curve.STANDARD)),
            cues = listOf(Cue("hakem.draw", CueKind.SOUND, "card_deal", 0)),
        ),
        Beat(
            event = Event.TRICK_SHOW,
            totalMs = 1150, // TRICK_SHOW_MS: how long a full trick stays readable on the felt
            phases = listOf(Phase("hold", "ماندن برای خواندن", 0, 1150, Curve.LINEAR)),
            cues = emptyList(),
        ),
        Beat(
            event = Event.HAKEM_BANNER,
            totalMs = 1500, // the hakem announcement: shown long enough to read before the deal
            phases = listOf(
                Phase("in", "آمدن بنر", 0, 240, Curve.DECELERATE),
                Phase("hold", "ماندن بنر", 240, 1300, Curve.LINEAR),
                Phase("out", "رفتن بنر", 1300, 1500, Curve.STANDARD),
            ),
            cues = listOf(Cue("hakem.in", CueKind.SOUND, "hokm_chosen", 240)),
        ),
        Beat(
            event = Event.BANNER,
            totalMs = 1300, // the short banners the table already showed (hokm chosen, cards dealt)
            phases = listOf(
                Phase("in", "آمدن بنر", 0, 200, Curve.DECELERATE),
                Phase("hold", "ماندن بنر", 200, 1100, Curve.LINEAR),
                Phase("out", "رفتن بنر", 1100, 1300, Curve.STANDARD),
            ),
            cues = emptyList(),
        ),
        Beat(
            event = Event.HAND_BANNER,
            totalMs = 2600, // HAND_BANNER_MS
            phases = listOf(
                Phase("in", "آمدن بنر", 0, 240, Curve.DECELERATE),
                Phase("hold", "ماندن بنر", 240, 2340, Curve.LINEAR),
                Phase("out", "رفتن بنر", 2340, 2600, Curve.STANDARD),
            ),
            cues = listOf(Cue("banner.in", CueKind.SOUND, "hand_won", 240)),
        ),
        Beat(
            event = Event.MATCH_BANNER,
            totalMs = 1800,
            phases = listOf(
                Phase("in", "آمدن بنر", 0, 240, Curve.DECELERATE),
                Phase("hold", "ماندن بنر", 240, 1560, Curve.LINEAR),
                Phase("out", "رفتن بنر", 1560, 1800, Curve.STANDARD),
            ),
            cues = listOf(Cue("match.in", CueKind.SOUND, "game_won", 240)),
        ),
        Beat(
            event = Event.CELEBRATE,
            totalMs = 1600,
            phases = listOf(Phase("burst", "جرقه و کاغذرنگی", 0, 1600, Curve.DECELERATE)),
            cues = listOf(Cue("celebrate.burst", CueKind.FX, "confetti", 0)),
        ),
    ).associateBy { it.event }

    /**
     * Decoration lifetimes (ms) — the particle bursts the screen draws. They are movements' *tails*,
     * not movements: they never gate the game, and reduced motion removes them completely
     * ([HokmMotion.particleCount] already returns 0 there). Kept here so the screen owns no
     * motion literal of its own (GX-04) and GX-05 can tune one place.
     */
    object Fx {
        const val CUT_MS: Int = 900
        const val TRICK_WIN_MS: Int = 700
        const val KOT_MS: Int = 2200
        const val CONFETTI_MS: Int = 3800

        val ALL_MS: List<Int> = listOf(CUT_MS, TRICK_WIN_MS, KOT_MS, CONFETTI_MS)
    }

    /** Every row, in declaration order — the docs table and the tests read this. */
    val beats: List<Beat> = Event.entries.map { requireNotNull(table[it]) { "no beat for ${it.id}" } }

    /** The shipped (normal-motion) row of [event]. */
    fun beat(event: Event): Beat = requireNotNull(table[event]) { "no beat for ${event.id}" }

    /**
     * The row that actually plays: in normal mode it is [beat], in reduced mode movements are
     * compressed to at most [HokmMotion.REDUCED_MAX_MS] (phases scaled, cues clamped to the new
     * end so they still fire exactly once), read pauses keep their length, and decoration
     * collapses to zero length with its FX cues dropped. SOUND and HAPTIC cues are *never*
     * dropped: a sound the player needs is information, not decoration.
     */
    fun beat(event: Event, reducedMotion: Boolean): Beat {
        val base = beat(event)
        if (!reducedMotion) return base
        return when (base.event.kind) {
            BeatKind.READ -> base
            BeatKind.MOVEMENT -> compress(base, HokmMotion.REDUCED_MAX_MS)
            BeatKind.DECORATION -> compress(base, 0).copy(
                cues = base.cues.filter { it.kind != CueKind.FX },
            )
        }
    }

    /** Total length of [event] as it will actually play. */
    fun durationMs(event: Event, reducedMotion: Boolean = false): Int = beat(event, reducedMotion).totalMs

    /**
     * The haptic slot of cue [cueId] inside [event] (`card_lift`, `card_contact`, `cut`), or
     * `null` when that cue is not a haptic. Used by the screen for the moments *it* owns — a card
     * lifting under the finger — so the slot names live in this table and nowhere else.
     */
    fun hapticSlot(event: Event, cueId: String): String? =
        beat(event).cue(cueId)?.takeIf { it.kind == CueKind.HAPTIC }?.name

    /** The dominant curve of [event] (the first phase's), for call sites that need one value. */
    fun curve(event: Event): Curve = beat(event).phases.firstOrNull()?.curve ?: Curve.STANDARD

    /** True when the beat still has anything to show. */
    fun plays(event: Event, reducedMotion: Boolean): Boolean =
        beat(event, reducedMotion).totalMs > 0 || beat(event, reducedMotion).cues.isNotEmpty()

    /**
     * Scales a beat down to [capMs], preserving the shape: phase boundaries keep their relative
     * position, cue offsets move with them, and a cue that would fall past the new end is clamped
     * onto it instead of vanishing (it still fires, once, at the moment the movement stops).
     */
    private fun compress(base: Beat, capMs: Int): Beat {
        val total = minOf(base.totalMs, capMs).coerceAtLeast(0)
        if (base.totalMs == 0) return base
        fun scale(ms: Int): Int =
            if (total == 0) 0 else ((ms.toLong() * total) / base.totalMs).toInt().coerceIn(0, total)
        val phases = base.phases.map { it.copy(fromMs = scale(it.fromMs), toMs = scale(it.toMs)) }
            .fold(ArrayList<Phase>()) { acc, phase ->
                // A zero-length phase is legal, but two phases must never overlap after rounding.
                val fixed = if (acc.isEmpty()) phase else phase.copy(fromMs = maxOf(phase.fromMs, acc.last().toMs))
                acc += fixed
                acc
            }
        return base.copy(
            totalMs = total,
            phases = phases,
            cues = base.cues.map { it.copy(atMs = scale(it.atMs)) }.sortedBy { it.atMs },
        )
    }
}

/**
 * The clock of one movement: forward-only, so skipping, resyncing or finishing a beat can never
 * replay a cue. `elapsedMs` is clamped into `[0, totalMs]` — a jump backwards is ignored (there is
 * no "un-play a sound"), which is exactly the guarantee the roadmap asks for after a resync.
 */
class HokmBeatCursor(private val beat: HokmChoreography.Beat) {

    /** Cues in firing order; ties at the same offset keep declaration order. */
    private val queue: List<HokmChoreography.Cue> = beat.cues.withIndex().sortedWith(
        compareBy({ it.value.atMs }, { it.index }),
    ).map { it.value }

    private var firedCount = 0

    var elapsedMs: Int = 0
        private set

    val totalMs: Int get() = beat.totalMs

    /** How much of this movement is behind us, for a progress-driven rendering. */
    val progress: Float
        get() = if (beat.totalMs <= 0) 1f else (elapsedMs.toFloat() / beat.totalMs).coerceIn(0f, 1f)

    val fired: List<HokmChoreography.Cue> get() = queue.take(firedCount)

    /** The offset of the next unfired cue, or `null` once they are all out. */
    val nextCueAtMs: Int? get() = queue.getOrNull(firedCount)?.atMs

    /** Nothing left to wait for: the movement has reached its end and every cue has fired. */
    val done: Boolean get() = firedCount >= queue.size && elapsedMs >= beat.totalMs

    /**
     * Moves the clock forward by [deltaMs] and returns the cues that became due — each cue exactly
     * once, in order. A negative delta is treated as zero: the clock never rewinds.
     */
    fun advance(deltaMs: Int): List<HokmChoreography.Cue> {
        elapsedMs = (elapsedMs + deltaMs.coerceAtLeast(0)).coerceIn(0, beat.totalMs)
        return drain()
    }

    /** Resync: jumps the clock to [toMs] (forward-only) and returns everything that is now due. */
    fun jumpTo(toMs: Int): List<HokmChoreography.Cue> {
        elapsedMs = toMs.coerceIn(elapsedMs, beat.totalMs)
        return drain()
    }

    /**
     * Skip: jumps to the end and returns the cues that had not fired yet, so a skipped movement is
     * still *heard* — just not seen. Safe to call twice (the second call returns nothing).
     */
    fun finish(): List<HokmChoreography.Cue> {
        elapsedMs = beat.totalMs
        return drain()
    }

    private fun drain(): List<HokmChoreography.Cue> {
        val out = ArrayList<HokmChoreography.Cue>()
        while (firedCount < queue.size && queue[firedCount].atMs <= elapsedMs) {
            out += queue[firedCount]
            firedCount++
        }
        return out
    }
}

/**
 * The table's half of a beat: the wait until the next cue and the cues that are due right now.
 * The table loop polls this instead of sleeping through the whole movement, so a cue always lands
 * on the frame the loop is on and the loop can still service truth between cues (HK-010).
 */
class HokmCueRunner(private val beat: HokmChoreography.Beat) {

    private val cursor = HokmBeatCursor(beat)

    val totalMs: Int get() = beat.totalMs

    /** How long to wait before the next cue; `null` when the movement is over. */
    fun waitMs(): Long? {
        if (cursor.done) return null
        val next = cursor.nextCueAtMs ?: beat.totalMs
        return (next - cursor.elapsedMs).coerceAtLeast(0).toLong()
    }

    /** Advances the clock by the time actually waited and returns the cues now due (at most once). */
    fun due(waitedMs: Long): List<HokmChoreography.Cue> = cursor.advance(waitedMs.toInt())

    val finished: Boolean get() = cursor.done

    /** Everything still pending, for a skipped movement (it is heard, not seen). */
    fun finish(): List<HokmChoreography.Cue> = cursor.finish()
}
