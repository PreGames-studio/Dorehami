package ir.dorehami.games.hokm.ui

/**
 * HK-026 (first slice) — quality tier selection and per-tier feature gates as **pure, unit-testable**
 * logic. The actual device measurement (RAM/thermal) is M-1; this object only decides what to do
 * with the measured inputs, so the decision itself can be tested without a device.
 *
 * Contract (the numbers the tests pin):
 *  * **Economy** (weak device / hot / low RAM): no particles, no sheen, no vignette, shadows
 *    simplified, reduced motion forced. Game correctness is never affected.
 *  * **Standard** (typical mid-range): full particles, sheen, vignette, shadows.
 *  * **High** (strong device, cool): standard + enhanced particle counts.
 *  * Selection is monotonic: more RAM / cooler never lowers the tier.
 *  * Reduced motion always forces at least economy-level decoration removal (reusing HokmMotion).
 */
object HokmQuality {

    enum class Tier { ECONOMY, STANDARD, HIGH }

    /** Thermal state reported by the device (Android PowerManager / Thermal API). */
    enum class Thermal { COOL, WARM, HOT, CRITICAL }

    /** RAM thresholds (GB) for tier selection. */
    const val ECONOMY_RAM_GB: Float = 3f
    const val HIGH_RAM_GB: Float = 6f

    /**
     * Selects a tier from measured inputs. Monotonic: more RAM / cooler never lowers the tier.
     * Reduced motion does not lower the tier by itself (it removes decoration via HokmMotion),
     * but a hot/critical device or low RAM forces economy.
     */
    fun selectTier(ramGb: Float, thermal: Thermal, reducedMotion: Boolean): Tier {
        if (ramGb < ECONOMY_RAM_GB) return Tier.ECONOMY
        if (thermal == Thermal.HOT || thermal == Thermal.CRITICAL) return Tier.ECONOMY
        if (ramGb >= HIGH_RAM_GB && thermal == Thermal.COOL && !reducedMotion) return Tier.HIGH
        return Tier.STANDARD
    }

    /** Whether particles are allowed at this tier. */
    fun particlesEnabled(tier: Tier, reducedMotion: Boolean): Boolean =
        tier != Tier.ECONOMY && !reducedMotion

    /** Whether sheen (winner/trump brightening) is allowed. */
    fun sheenEnabled(tier: Tier): Boolean = tier != Tier.ECONOMY

    /** Whether the full vignette is drawn (economy uses a flat darkening). */
    fun fullVignette(tier: Tier): Boolean = tier != Tier.ECONOMY

    /** Whether contact shadows use the full blur (economy uses a hard shadow). */
    fun softShadows(tier: Tier): Boolean = tier != Tier.ECONOMY

    /** Particle multiplier for this tier (reuses HokmEffects multipliers). */
    fun particleMultiplier(tier: Tier): Float = when (tier) {
        Tier.ECONOMY -> HokmEffects.ECONOMY_MULTIPLIER
        Tier.STANDARD -> HokmEffects.STANDARD_MULTIPLIER
        Tier.HIGH -> HokmEffects.HIGH_MULTIPLIER
    }

    /** True when the tier is at least standard. */
    fun isAtLeastStandard(tier: Tier): Boolean = tier == Tier.STANDARD || tier == Tier.HIGH


    /** Whether coaching hints are allowed at this tier (economy disables to save CPU). */
    fun coachingEnabled(tier: Tier): Boolean = tier != Tier.ECONOMY

    /** Whether sound effects are allowed (always true; economy may reduce variety). */
    fun fullSoundBank(tier: Tier): Boolean = tier != Tier.ECONOMY

    /** Whether card sheen animation runs. */
    fun cardSheenEnabled(tier: Tier, reducedMotion: Boolean): Boolean =
        sheenEnabled(tier) && !reducedMotion

    /** Whether the trump reveal uses the full flip animation. */
    fun trumpRevealFull(tier: Tier, reducedMotion: Boolean): Boolean =
        tier != Tier.ECONOMY && !reducedMotion

    /** Whether opponent hand fan is animated (economy shows static). */
    fun fanAnimationEnabled(tier: Tier, reducedMotion: Boolean): Boolean =
        tier != Tier.ECONOMY && !reducedMotion

    /** Whether the match-win celebration runs. */
    fun celebrationEnabled(tier: Tier, reducedMotion: Boolean): Boolean =
        tier == Tier.HIGH && !reducedMotion
}
