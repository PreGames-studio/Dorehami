package ir.dorehami.games.hokm.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decorative motion budget for the luxury table.
 *
 * It only changes animation amplitude and particle counts. Card faces, hit targets, contrast,
 * legal/illegal colours and game rules never depend on it, so switching it off is always safe.
 */
enum class HokmMotionLevel {
    /** Full parabolic deals, swirls, particles and the timer warning pulse. */
    FULL,

    /** Shorter, non-looping motion (also used when Android asks for reduced animations). */
    REDUCED,

    /** Static presentation; every state is still shown through colour, text and shape. */
    OFF,
    ;

    companion object {
        /** Parses a persisted value. Unknown or legacy values fall back to [FULL]. */
        fun fromStored(value: String?): HokmMotionLevel =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FULL
    }
}

/** Urgency of the room turn countdown, derived only from whole seconds left. */
enum class TurnUrgency {
    /** Plenty of time: quiet gold ring. */
    CALM,

    /** Half the allowance is gone: brighter ring. */
    GOLD,

    /** Final seconds: flashing red warning and a suspense tick. */
    WARNING,

    /** Deadline reached: the bot policy moves on the host, never here. */
    EXPIRED,
}

/** A card's position and tilt in pixels during a choreographed move. */
data class CardPose(val x: Float, val y: Float, val rotationDeg: Float)

/**
 * Pure, deterministic 2.5D choreography for the Hokm table.
 *
 * These helpers live in the UI module but have **no** Android, Compose, clock or random
 * dependency: UI-only randomness/time is injected as [progress]/[frame] by the caller, and the
 * gameplay RNG in `games/hokm/core` is untouched. Every function is a pure function of its
 * arguments so the geometry can be regression-tested on the JVM.
 */
object HokmCinematics {
    /** Suggested allowance for the room move timer. The real deadline still comes from the host. */
    const val DEFAULT_TURN_SECONDS = 10

    /** Last seconds in which the ring turns red and flashes. */
    const val WARNING_SECONDS = 3

    /** Amplitude multiplier for decorative motion; [HokmMotionLevel.OFF] is exactly zero. */
    fun motionScale(level: HokmMotionLevel): Float = when (level) {
        HokmMotionLevel.FULL -> 1f
        HokmMotionLevel.REDUCED -> 0.45f
        HokmMotionLevel.OFF -> 0f
    }

    /** Particle budget: fewer sparks on slower settings, none when motion is off. */
    fun particleCount(base: Int, level: HokmMotionLevel): Int = when (level) {
        HokmMotionLevel.FULL -> base.coerceAtLeast(0)
        HokmMotionLevel.REDUCED -> (base.coerceAtLeast(0) / 3).coerceAtLeast(4)
        HokmMotionLevel.OFF -> 0
    }

    /** Position of one dot on an elliptical ring (centre is added by the caller). */
    fun ringDot(index: Int, count: Int, radiusX: Float, radiusY: Float, rotationDeg: Float): Pair<Float, Float> {
        require(count > 0) { "ring needs at least one dot" }
        val angle = 2.0 * PI * (index.toDouble() / count) + Math.toRadians(rotationDeg.toDouble())
        return (radiusX * cos(angle)).toFloat() to (radiusY * sin(angle)).toFloat()
    }

    /**
     * Parabolic deal path from the deck to a seat: linear travel plus an apex of [liftPx] pixels
     * at the midpoint, with a fixed spin. Both endpoints are exact.
     */
    fun dealArc(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        progress: Float,
        liftPx: Float,
        spinDeg: Float,
    ): CardPose {
        val t = progress.coerceIn(0f, 1f)
        val x = startX + (endX - startX) * t
        val y = startY + (endY - startY) * t - 4f * liftPx * t * (1f - t)
        return CardPose(x, y, spinDeg * t)
    }

    /**
     * Magnetic collection path: cards travel to the winner while swirling around the straight
     * line. The swirl vanishes at both ends so the pile always lands neatly. [cardIndex] keeps
     * the four cards from overlapping.
     */
    fun collectSwirl(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        progress: Float,
        swirlPx: Float,
        cardIndex: Int,
    ): CardPose {
        val t = progress.coerceIn(0f, 1f)
        val baseX = startX + (endX - startX) * t
        val baseY = startY + (endY - startY) * t
        val dx = endX - startX
        val dy = endY - startY
        val length = sqrt(dx * dx + dy * dy)
        val nx = if (length < 0.0001f) 1f else -dy / length
        val ny = if (length < 0.0001f) 0f else dx / length
        val attenuation = sin(PI * t).toFloat() * (1f - t)
        val phase = 2.0 * PI * (1.5 * t + cardIndex * 0.25)
        val offset = swirlPx * attenuation * cos(phase).toFloat()
        val direction = if (cardIndex % 2 == 0) 1f else -1f
        return CardPose(baseX + nx * offset, baseY + ny * offset, direction * 220f * t)
    }

    /** Symmetric arc-fan tilt, in degrees, for a card at [index] of [count]. */
    fun fanAngle(index: Int, count: Int, maxDegrees: Float): Float {
        if (count <= 1) return 0f
        val mid = (count - 1) / 2f
        return ((index - mid) / mid) * maxDegrees
    }

    /** Quadratic lift of the outer cards of the fan, in the caller's unit (dp or px). */
    fun fanLift(index: Int, count: Int, maxLift: Float): Float {
        if (count <= 1) return 0f
        val mid = (count - 1) / 2f
        val rel = abs((index - mid) / mid)
        return maxLift * rel * rel
    }

    /**
     * Decaying screen shake in pixels. Deterministic: no [Random], no wall clock; [elapsedMs]
     * comes from the animation progress. Returns zero when motion is off or outside the window.
     */
    fun shake(elapsedMs: Long, durationMs: Long, amplitudePx: Float, scale: Float): Pair<Float, Float> {
        if (scale <= 0f || durationMs <= 0L || elapsedMs < 0L || elapsedMs > durationMs) return 0f to 0f
        val p = elapsedMs.toFloat() / durationMs.toFloat()
        val decay = (1f - p) * (1f - p)
        val phase = elapsedMs.toFloat() / 16f
        return (amplitudePx * scale * decay * sin(phase.toDouble()).toFloat()) to
            (amplitudePx * scale * decay * sin((phase * 1.37f).toDouble()).toFloat() * 0.6f)
    }

    /** Countdown state. The host's [totalSeconds] stays the authority; this is only presentation. */
    fun turnUrgency(secondsLeft: Int, totalSeconds: Int = DEFAULT_TURN_SECONDS): TurnUrgency = when {
        secondsLeft <= 0 -> TurnUrgency.EXPIRED
        totalSeconds <= 0 -> TurnUrgency.WARNING
        secondsLeft <= minOf(WARNING_SECONDS, (totalSeconds / 3).coerceAtLeast(1)) -> TurnUrgency.WARNING
        secondsLeft <= totalSeconds / 2 -> TurnUrgency.GOLD
        else -> TurnUrgency.CALM
    }

    /**
     * Brightness of the warning ring (0.45..1). A triangle wave over [frame] avoids a random
     * flicker; with reduced motion the ring stays at a constant, readable brightness.
     */
    fun warningPulse(secondsLeft: Int, frame: Int, scale: Float): Float {
        if (scale <= 0f || turnUrgency(secondsLeft) != TurnUrgency.WARNING) return 1f
        val step = (frame % 6).coerceAtLeast(0) / 5f
        val triangle = abs(2f * step - 1f)
        return 0.45f + 0.55f * (scale * triangle + (1f - scale))
    }

    /** Strength of the gold-foil sweep on a freshly flipped ace (0 at both ends, 1 at mid-sweep). */
    fun foilSweep(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return 1f - abs(2f * p - 1f)
    }

    /** Persian digits for the trick counter; negative values use U+2212 minus. */
    fun faDigits(value: Int): String {
        val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val magnitude = abs(value.toLong()).toString()
        val body = magnitude.map { c -> digits[c - '0'] }.joinToString("")
        return if (value < 0) "\u2212$body" else body
    }
}
