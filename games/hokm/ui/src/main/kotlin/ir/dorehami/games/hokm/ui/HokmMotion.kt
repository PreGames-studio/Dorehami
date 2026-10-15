package ir.dorehami.games.hokm.ui

/**
 * HK-013.a — the table's reduced-motion policy, as pure logic (no Android, no Compose) so the
 * decision itself is JVM-testable and the Compose layer only *reads* the system setting.
 *
 * The contract is deliberately narrow, and it is the one ROADMAP §5/§10.2 asks for:
 * * the player's system choice wins: when the system says animations are off
 *   (`animator_duration_scale == 0`, so `ValueAnimator.areAnimatorsEnabled()` is false), the
 *   table is in reduced-motion mode;
 * * reduced mode removes *decoration* (particles, confetti, endless pulses and glows) but never
 *   information: a move still lands, a trick still collects, and no game state ever waits behind
 *   an animation (the HK-010 notice queue already guarantees the felt can skip ahead);
 * * essential movement is not deleted, it is *compressed* to at most [REDUCED_MAX_MS], so the
 *   eye still sees "the card went there" instead of a teleport, and animation loops always
 *   finish — nothing can hang waiting for a tween that will never run.
 */
object HokmMotion {

    /** Longest an essential animation may take in reduced-motion mode. */
    const val REDUCED_MAX_MS = 80

    /**
     * True when the system asks for reduced motion. [animatorDurationScale] is the raw
     * `Settings.Global.ANIMATOR_DURATION_SCALE` value; [animatorsEnabled] is
     * `ValueAnimator.areAnimatorsEnabled()`, which Android derives from the same scale.
     * Either signal saying "off" is enough — the player's setting is not second-guessed.
     */
    fun isReducedMotion(animatorDurationScale: Float, animatorsEnabled: Boolean): Boolean =
        !animatorsEnabled || animatorDurationScale <= 0f

    /**
     * The duration an animation should actually run for: unchanged in normal mode, compressed
     * to at most [REDUCED_MAX_MS] (never negative, never longer than requested) in reduced mode.
     */
    fun durationMs(requestedMs: Int, reducedMotion: Boolean): Int {
        val requested = requestedMs.coerceAtLeast(0)
        return if (reducedMotion) minOf(requested, REDUCED_MAX_MS) else requested
    }

    /** Decorative particle/confetti counts: full in normal mode, none in reduced mode. */
    fun particleCount(requested: Int, reducedMotion: Boolean): Int =
        if (reducedMotion) 0 else requested.coerceAtLeast(0)

    /** Endless decorative loops (turn glow, hero bob) run only in normal mode. */
    fun infiniteMotionEnabled(reducedMotion: Boolean): Boolean = !reducedMotion
}
