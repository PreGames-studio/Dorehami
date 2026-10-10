package ir.dorehami.games.chess.ui.gl

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Tiny column-major 4×4 matrix helpers (same layout as OpenGL / `android.opengl.Matrix`).
 * Pure Kotlin on purpose: the mesh + camera code runs and is tested on the plain JVM.
 */
object Mat4 {
    fun identity(out: FloatArray = FloatArray(16)): FloatArray {
        out.fill(0f)
        out[0] = 1f; out[5] = 1f; out[10] = 1f; out[15] = 1f
        return out
    }

    /** out = a × b (b is applied first). [out] may not alias [a] or [b]. */
    fun multiply(out: FloatArray, a: FloatArray, b: FloatArray): FloatArray {
        for (c in 0 until 4) {
            for (r in 0 until 4) {
                var s = 0f
                for (k in 0 until 4) s += a[k * 4 + r] * b[c * 4 + k]
                out[c * 4 + r] = s
            }
        }
        return out
    }

    fun multiply(a: FloatArray, b: FloatArray): FloatArray = multiply(FloatArray(16), a, b)

    fun perspective(out: FloatArray, fovYDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(Math.toRadians(fovYDeg / 2.0)).toFloat()
        out.fill(0f)
        out[0] = f / aspect
        out[5] = f
        out[10] = (far + near) / (near - far)
        out[11] = -1f
        out[14] = 2f * far * near / (near - far)
        return out
    }

    fun lookAt(
        out: FloatArray,
        ex: Float, ey: Float, ez: Float,
        cx: Float, cy: Float, cz: Float,
        ux: Float, uy: Float, uz: Float,
    ): FloatArray {
        var fx = cx - ex
        var fy = cy - ey
        var fz = cz - ez
        val fl = sqrt(fx * fx + fy * fy + fz * fz)
        fx /= fl; fy /= fl; fz /= fl
        // s = f × up
        var sx = fy * uz - fz * uy
        var sy = fz * ux - fx * uz
        var sz = fx * uy - fy * ux
        val sl = sqrt(sx * sx + sy * sy + sz * sz)
        sx /= sl; sy /= sl; sz /= sl
        // u = s × f
        val vx = sy * fz - sz * fy
        val vy = sz * fx - sx * fz
        val vz = sx * fy - sy * fx
        out[0] = sx; out[1] = vx; out[2] = -fx; out[3] = 0f
        out[4] = sy; out[5] = vy; out[6] = -fy; out[7] = 0f
        out[8] = sz; out[9] = vz; out[10] = -fz; out[11] = 0f
        out[12] = -(sx * ex + sy * ey + sz * ez)
        out[13] = -(vx * ex + vy * ey + vz * ez)
        out[14] = fx * ex + fy * ey + fz * ez
        out[15] = 1f
        return out
    }

    /** Model matrix: translate(tx,ty,tz) · rotateY(yawRad) · scale(sx,sy,sz). */
    fun trs(out: FloatArray, tx: Float, ty: Float, tz: Float, yawRad: Float, sx: Float, sy: Float, sz: Float): FloatArray {
        val c = cos(yawRad)
        val s = sin(yawRad)
        out.fill(0f)
        out[0] = c * sx; out[2] = -s * sx
        out[5] = sy
        out[8] = s * sz; out[10] = c * sz
        out[12] = tx; out[13] = ty; out[14] = tz; out[15] = 1f
        return out
    }

    /** General 4×4 inverse; returns false if singular. */
    fun invert(out: FloatArray, m: FloatArray): Boolean {
        val inv = FloatArray(16)
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10]
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10]
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9]
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9]
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10]
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10]
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9]
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9]
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6]
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6]
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5]
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5]
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6]
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6]
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5]
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5]
        var det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12]
        if (det == 0f) return false
        det = 1f / det
        for (i in 0 until 16) out[i] = inv[i] * det
        return true
    }

    /** Transforms the point (x,y,z,w) by [m]; result in [out] (length ≥ 4). */
    fun transform(out: FloatArray, m: FloatArray, x: Float, y: Float, z: Float, w: Float = 1f): FloatArray {
        for (r in 0 until 4) out[r] = m[r] * x + m[4 + r] * y + m[8 + r] * z + m[12 + r] * w
        return out
    }

    /**
     * Planar shadow projection onto the plane `y = planeY` for a *directional* light travelling
     * along (lx, ly, lz) (ly < 0, i.e. pointing down).
     */
    fun shadowOnPlaneY(out: FloatArray, lx: Float, ly: Float, lz: Float, planeY: Float): FloatArray {
        // p' = p - d * (p.y - planeY) / ly
        identity(out)
        out[4] = -lx / ly
        out[5] = 0f
        out[6] = -lz / ly
        out[12] = lx / ly * planeY
        out[13] = planeY
        out[14] = lz / ly * planeY
        return out
    }
}
