package ir.dorehami.games.chess.ui.gl

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tan

/**
 * Orbit camera around the board centre. `yaw = 0` looks from white's side (+Z), `yaw = 180` from
 * black's. The distance is fitted every frame so the whole board is visible on any aspect ratio
 * (portrait phones included); [zoom] scales that fitted distance.
 *
 * Pure Kotlin (no Android types) so it can be unit-tested and previewed on the JVM.
 */
class OrbitCamera {
    var width = 1
        private set
    var height = 1
        private set

    /** Current (smoothed) angles in degrees. */
    var yaw = 0f
        private set
    var pitch = DEFAULT_PITCH
        private set
    var zoom = 1f
        private set

    private var targetYaw = 0f
    private var targetPitch = DEFAULT_PITCH
    private var targetZoom = 1f

    val view = FloatArray(16)
    val proj = FloatArray(16)
    val viewProj = FloatArray(16)
    private val invViewProj = FloatArray(16)
    val eye = FloatArray(3)

    var fovY = 50f
        private set

    fun resize(w: Int, h: Int) {
        width = max(1, w)
        height = max(1, h)
        update()
    }

    /** Puts the camera behind white (`true`) or black; [animate] = swing around smoothly. */
    fun setSide(white: Boolean, animate: Boolean) {
        val base = if (white) 0f else 180f
        // Pick the representation of `base` closest to the current yaw so we turn the short way.
        var t = base
        while (t - targetYaw > 180f) t -= 360f
        while (t - targetYaw < -180f) t += 360f
        targetYaw = t
        targetPitch = DEFAULT_PITCH
        targetZoom = 1f
        if (!animate) {
            yaw = targetYaw
            pitch = targetPitch
            zoom = targetZoom
        }
        update()
    }

    /** Finger drag in pixels → orbit. */
    fun orbitBy(dxPx: Float, dyPx: Float) {
        val k = 180f / max(width, height)
        targetYaw += dxPx * k * 1.2f
        targetPitch = (targetPitch + dyPx * k).coerceIn(MIN_PITCH, MAX_PITCH)
        yaw = targetYaw
        pitch = targetPitch
        update()
    }

    fun zoomBy(factor: Float) {
        targetZoom = (targetZoom / factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        zoom = targetZoom
        update()
    }

    /** Advances the smoothing by [dtSec]; returns true while still moving. */
    fun step(dtSec: Float): Boolean {
        val a = 1f - exp(-dtSec * 7f)
        yaw += (targetYaw - yaw) * a
        pitch += (targetPitch - pitch) * a
        zoom += (targetZoom - zoom) * a
        val moving = abs(targetYaw - yaw) > 0.05f || abs(targetPitch - pitch) > 0.05f || abs(targetZoom - zoom) > 0.001f
        if (!moving) {
            yaw = targetYaw
            pitch = targetPitch
            zoom = targetZoom
        }
        update()
        return moving
    }

    private fun update() {
        val aspect = width.toFloat() / height
        // Keep at least ~44° horizontally so portrait screens do not need a telephoto lens.
        val minH = Math.toRadians(22.0)
        fovY = max(46.0, Math.toDegrees(2 * atan(tan(minH) / aspect))).coerceAtMost(78.0).toFloat()
        Mat4.perspective(proj, fovY, aspect, 0.8f, 120f)
        val d = fittedDistance(aspect) * zoom
        place(d)
    }

    private fun place(d: Float) {
        val p = Math.toRadians(pitch.toDouble())
        val y = Math.toRadians(yaw.toDouble())
        eye[0] = (d * cos(p) * sin(y)).toFloat()
        eye[1] = (d * sin(p)).toFloat()
        eye[2] = (d * cos(p) * cos(y)).toFloat()
        // Aim a little past the centre so the near rank is not glued to the bottom edge.
        val ax = (TARGET_SHIFT * sin(y)).toFloat()
        val az = (TARGET_SHIFT * cos(y)).toFloat()
        Mat4.lookAt(view, eye[0], eye[1], eye[2], ax, 0f, az, 0f, 1f, 0f)
        Mat4.multiply(viewProj, proj, view)
        Mat4.invert(invViewProj, viewProj)
    }

    /** Smallest distance at which all board corners (and piece tops) are on screen. */
    private fun fittedDistance(aspect: Float): Float {
        var lo = 4f
        var hi = 80f
        val tmp = FloatArray(4)
        repeat(24) {
            val mid = (lo + hi) / 2
            place(mid)
            var fits = true
            for (cx in floatArrayOf(-BOARD_HALF, BOARD_HALF)) for (cz in floatArrayOf(-BOARD_HALF, BOARD_HALF)) {
                for (cy in floatArrayOf(0f, 1.3f)) {
                    Mat4.transform(tmp, viewProj, cx, cy, cz)
                    if (tmp[3] <= 0f) {
                        fits = false
                        continue
                    }
                    val nx = tmp[0] / tmp[3]
                    val ny = tmp[1] / tmp[3]
                    if (abs(nx) > FIT_X || ny > FIT_TOP || ny < -FIT_BOTTOM) fits = false
                }
            }
            if (fits) hi = mid else lo = mid
        }
        return hi
    }

    /**
     * World-space ray through the pixel (sx, sy) (origin top-left). Returns
     * `[ox, oy, oz, dx, dy, dz]` with a normalised direction.
     */
    fun ray(sx: Float, sy: Float): FloatArray {
        val nx = sx / width * 2f - 1f
        val ny = 1f - sy / height * 2f
        val a = Mat4.transform(FloatArray(4), invViewProj, nx, ny, -1f)
        val b = Mat4.transform(FloatArray(4), invViewProj, nx, ny, 1f)
        val ox = a[0] / a[3]; val oy = a[1] / a[3]; val oz = a[2] / a[3]
        var dx = b[0] / b[3] - ox; var dy = b[1] / b[3] - oy; var dz = b[2] / b[3] - oz
        val l = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        dx /= l; dy /= l; dz /= l
        return floatArrayOf(ox, oy, oz, dx, dy, dz)
    }

    companion object {
        /**
         * Slightly above 45°: the board surface stays easy to read while the pieces still have
         * height, and the whole board fits the width of a tall phone.
         */
        const val DEFAULT_PITCH = 58f
        const val MIN_PITCH = 18f
        const val MAX_PITCH = 88f
        const val MIN_ZOOM = 0.55f
        const val MAX_ZOOM = 1.6f

        /** Half size of the board including its frame. */
        const val BOARD_HALF = 4.6f
        private const val TARGET_SHIFT = 0.35
        private const val FIT_X = 0.97f
        private const val FIT_TOP = 0.80f
        private const val FIT_BOTTOM = 0.78f
    }
}
