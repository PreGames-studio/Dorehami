package ir.dorehami.games.hokm.ui

import kotlin.math.sqrt

/** How much decorative work a device is allowed to do. Readability never depends on this. */
enum class HokmQualityLevel {
    /** Economy phones: no specular sheen, far fewer particles. */
    LOW,

    /** Default: subtle sheen, three quarters of the particle budget. */
    STANDARD,

    /** Roomy device with full motion enabled: the complete effect. */
    HIGH,
}

/**
 * A normalized specular highlight on a card face.
 *
 * Offsets are in `-0.5..0.5` of the card width/height and [intensity] is the maximum alpha of the
 * highlight. [NEUTRAL] means "draw nothing". The rank and suit are drawn underneath and are never
 * covered by an opaque layer.
 */
data class HokmSheen(val offsetX: Float, val offsetY: Float, val intensity: Float) {
    companion object {
        /** No tilt, no reflection: safe default for every call site. */
        val NEUTRAL = HokmSheen(0f, 0f, 0f)
    }
}

/**
 * Pure, deterministic policy for the luxury stage's environmental reflection and effect budget.
 *
 * This object has no Android, Compose, sensor, clock or random dependency: the sensor supplies
 * normalized tilt values, [HokmCinematics.motionScale] supplies the user's motion preference, and
 * the device supplies its memory/core count. Everything here is a pure function of its arguments,
 * so it can be regression-tested on the JVM without a device or an emulator.
 */
object HokmReflection {
    /**
     * Hard ceiling on the highlight's alpha. Above this, a light-coloured court card stops being
     * readable for older players; the ceiling is structural, not a style preference.
     */
    const val MAX_SHEEN_INTENSITY = 0.12f

    private const val SQRT_2 = 1.4142135f

    /**
     * Maps device tilt (each axis already normalized to roughly `-1..1`) to a sheen.
     *
     * At rest and when [motionScale] is zero the result is exactly [HokmSheen.NEUTRAL]; opposite
     * tilts produce mirrored offsets, and out-of-range or non-finite sensor values are clamped.
     */
    fun sheen(tiltX: Float, tiltY: Float, motionScale: Float): HokmSheen {
        val scale = motionScale.coerceIn(0f, 1f)
        if (scale <= 0f || !tiltX.isFinite() || !tiltY.isFinite()) return HokmSheen.NEUTRAL
        val x = tiltX.coerceIn(-1f, 1f)
        val y = tiltY.coerceIn(-1f, 1f)
        val magnitude = (sqrt(x * x + y * y) / SQRT_2).coerceIn(0f, 1f)
        return HokmSheen(
            offsetX = (x * 0.5f * scale).coerceIn(-0.5f, 0.5f),
            offsetY = (y * 0.5f * scale).coerceIn(-0.5f, 0.5f),
            intensity = (MAX_SHEEN_INTENSITY * scale * magnitude).coerceIn(0f, MAX_SHEEN_INTENSITY),
        )
    }

    /** Centre of the highlight band as a fraction of card width, kept away from both edges. */
    fun bandCenter(sheen: HokmSheen): Float = (0.5f + sheen.offsetX * 0.6f).coerceIn(0.2f, 0.8f)

    /** Alpha of that band. Dimmed (illegal) cards never get a reflection. */
    fun specularAlpha(sheen: HokmSheen, dimmed: Boolean): Float =
        if (dimmed) 0f else sheen.intensity.coerceIn(0f, MAX_SHEEN_INTENSITY)

    /** Decides the effect budget from injected device facts and the user's motion preference. */
    fun quality(maxMemoryMb: Int, cores: Int, level: HokmMotionLevel): HokmQualityLevel = when {
        level == HokmMotionLevel.OFF || maxMemoryMb < 192 || cores < 4 -> HokmQualityLevel.LOW
        level == HokmMotionLevel.FULL && maxMemoryMb >= 512 && cores >= 8 -> HokmQualityLevel.HIGH
        else -> HokmQualityLevel.STANDARD
    }

    /** Fraction of the particle budget this device may use. */
    fun particleScale(quality: HokmQualityLevel): Float = when (quality) {
        HokmQualityLevel.LOW -> 0.4f
        HokmQualityLevel.STANDARD -> 0.75f
        HokmQualityLevel.HIGH -> 1f
    }

    /** Fraction of the sheen amplitude this device may use; economy phones get none. */
    fun sheenScale(quality: HokmQualityLevel): Float = when (quality) {
        HokmQualityLevel.LOW -> 0f
        HokmQualityLevel.STANDARD -> 0.7f
        HokmQualityLevel.HIGH -> 1f
    }
}
