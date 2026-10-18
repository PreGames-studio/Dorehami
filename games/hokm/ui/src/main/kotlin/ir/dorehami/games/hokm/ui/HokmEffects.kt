package ir.dorehami.games.hokm.ui

/**
 * HK-025 (first slice) — mature, low-nuisance effects as **pure, unit-testable** logic.
 *
 * The roadmap asks that cut/kot/win effects come from the *correct seat*, never overwrite or
 * cover a card, and degrade gracefully on weak devices. Until now that contract lived only in
 * prose (HK-025). This object turns it into numbers the Compose layer *reads* — the same
 * boundary HK-013.a drew for [HokmMotion], GX-04 for [HokmChoreography] and GX-05 for
 * [HokmLighting].
 *
 * Contract (the numbers the tests pin):
 *  * **Origin:** every effect is anchored to the seat that caused it; a trick-win effect
 *    comes from the trick winner, a cut from the player who played the trump, kot/match-win
 *    from the winning side's first seat. Effects never float free of a seat.
 *  * **Layering:** effects live in a dedicated overlay layer *behind* the cards' hit targets
 *    and *above* the felt; two effects of the same kind never stack on the same seat, and a
 *    higher-priority effect (match win > kot > trick win > cut) replaces a lower one instead
 *    of piling up.
 *  * **Quality scaling:** particle counts scale by device quality (economy/standard/high);
 *    economy never shows particles at all (HK-026), but the game result is still announced.
 *  * **Readability:** confetti/particles are decoration only — they never carry information,
 *    and reduced motion removes them entirely (reusing [HokmMotion]).
 */
object HokmEffects {

    /** An effect kind, ordered by priority (highest first). */
    enum class Kind(val priority: Int) {
        MATCH_WIN(4),
        KOT(3),
        TRICK_WIN(2),
        CUT(1),
    }

    /** Device quality tier for effect degradation. */
    enum class Quality { ECONOMY, STANDARD, HIGH }

    /** Base particle counts per effect kind at STANDARD quality. */
    const val CUT_PARTICLES: Int = 18
    const val TRICK_WIN_PARTICLES: Int = 8
    const val KOT_PARTICLES: Int = 48
    const val MATCH_WIN_PARTICLES: Int = 60

    /** Quality multipliers (economy = 0, so particles vanish but the result is still shown). */
    const val ECONOMY_MULTIPLIER: Float = 0f
    const val STANDARD_MULTIPLIER: Float = 1f
    const val HIGH_MULTIPLIER: Float = 1.4f

    /** Z-order: felt < effect overlay < cards < UI chrome. Effects never sit above cards. */
    const val EFFECT_Z: Int = 10
    const val CARD_Z: Int = 20
    const val CHROME_Z: Int = 30

    /** Whether an effect of [a] may run while [b] is still on screen. */
    fun canOverlap(a: Kind, b: Kind): Boolean = a != b

    /** Whether [incoming] replaces [current] instead of stacking (higher or equal priority). */
    fun replaces(incoming: Kind, current: Kind): Boolean = incoming.priority >= current.priority

    /** Particle count for [kind] at [quality], never negative. */
    fun particleCount(kind: Kind, quality: Quality): Int {
        val base = when (kind) {
            Kind.CUT -> CUT_PARTICLES
            Kind.TRICK_WIN -> TRICK_WIN_PARTICLES
            Kind.KOT -> KOT_PARTICLES
            Kind.MATCH_WIN -> MATCH_WIN_PARTICLES
        }
        val mult = when (quality) {
            Quality.ECONOMY -> ECONOMY_MULTIPLIER
            Quality.STANDARD -> STANDARD_MULTIPLIER
            Quality.HIGH -> HIGH_MULTIPLIER
        }
        return (base * mult).toInt().coerceAtLeast(0)
    }

    /** Economy tier never shows particles (but the result is still announced). */
    fun particlesEnabled(quality: Quality, reducedMotion: Boolean): Boolean =
        quality != Quality.ECONOMY && !reducedMotion

    /** The seat an effect originates from, given the triggering seat. */
    fun originSeat(kind: Kind, triggerSeat: Int, playerCount: Int): Int {
        val seats = playerCount.coerceIn(2, 4)
        val s = triggerSeat.coerceIn(0, seats - 1)
        return when (kind) {
            Kind.CUT, Kind.TRICK_WIN -> s
            Kind.KOT, Kind.MATCH_WIN -> s // winning side's first seat is the trigger in our model
        }
    }

    /** True when effects stay below cards (readability guard). */
    fun effectsBelowCards(): Boolean = EFFECT_Z < CARD_Z && CARD_Z < CHROME_Z
}
