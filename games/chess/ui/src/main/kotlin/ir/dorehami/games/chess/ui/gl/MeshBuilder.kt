package ir.dorehami.games.chess.ui.gl

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * CPU-side triangle mesh: interleaved `x y z nx ny nz shade` per vertex + 16-bit indices.
 * `shade` darkens details (bishop slit, knight eyes): 1 = material colour, 0 = black.
 */
class MeshData(val vertices: FloatArray, val indices: ShortArray) {
    val vertexCount: Int get() = vertices.size / STRIDE
    val indexCount: Int get() = indices.size

    /** Highest y of any vertex (used for picking cylinders). */
    val maxY: Float
        get() {
            var m = Float.NEGATIVE_INFINITY
            for (i in 0 until vertexCount) m = maxOf(m, vertices[i * STRIDE + 1])
            return m
        }

    companion object {
        /** Floats per vertex. */
        const val STRIDE = 7
    }
}

/**
 * Procedural geometry: surfaces of revolution (the turned Staunton pieces), boxes, spheres and
 * bevelled extrusions (the knight's head). No model files, no loaders, no licences.
 */
class MeshBuilder {
    private var v = FloatArray(4096)
    private var vSize = 0
    private var idx = ShortArray(8192)
    private var iSize = 0

    val vertexCount: Int get() = vSize / MeshData.STRIDE

    /** Shade written into every new vertex (see [MeshData]). */
    var shade: Float = 1f

    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        if (vSize + MeshData.STRIDE > v.size) v = v.copyOf(v.size * 2)
        val l = sqrt(nx * nx + ny * ny + nz * nz).let { if (it < 1e-8f) 1f else it }
        v[vSize++] = x; v[vSize++] = y; v[vSize++] = z
        v[vSize++] = nx / l; v[vSize++] = ny / l; v[vSize++] = nz / l
        v[vSize++] = shade
        val index = vertexCount - 1
        check(index < 65536) { "mesh too large for 16-bit indices" }
        return index
    }

    fun triangle(a: Int, b: Int, c: Int) {
        if (iSize + 3 > idx.size) idx = idx.copyOf(idx.size * 2)
        idx[iSize++] = a.toShort(); idx[iSize++] = b.toShort(); idx[iSize++] = c.toShort()
    }

    /** Counter-clockwise quad a-b-c-d (seen from the front). */
    fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(a, c, d)
    }

    fun build(): MeshData = MeshData(v.copyOf(vSize), idx.copyOf(iSize))

    /**
     * Surface of revolution around the Y axis. [profile] is a list of (radius, y) pairs from the
     * bottom centre to the top centre, going *up* the outside. Corners sharper than
     * [creaseDeg] keep hard edges; gentler bends are smoothed.
     */
    fun lathe(profile: List<Pair<Float, Float>>, slices: Int = 40, creaseDeg: Float = 38f, x0: Float = 0f, z0: Float = 0f) {
        val n = profile.size
        require(n >= 2)
        // 2D outward normal of each segment: (dy, -dr).
        val segN = Array(n - 1) { k ->
            val dr = profile[k + 1].first - profile[k].first
            val dy = profile[k + 1].second - profile[k].second
            val l = sqrt(dr * dr + dy * dy).let { if (it < 1e-8f) 1f else it }
            floatArrayOf(dy / l, -dr / l)
        }
        val creaseCos = cos(Math.toRadians(creaseDeg.toDouble())).toFloat()
        fun blended(a: FloatArray, b: FloatArray): FloatArray {
            val dot = a[0] * b[0] + a[1] * b[1]
            return if (dot >= creaseCos) floatArrayOf(a[0] + b[0], a[1] + b[1]) else a
        }
        for (k in 0 until n - 1) {
            val (r0, y0) = profile[k]
            val (r1, y1) = profile[k + 1]
            if (abs(r0 - r1) < 1e-6f && abs(y0 - y1) < 1e-6f) continue
            val nStart = if (k > 0) blended(segN[k], segN[k - 1]) else segN[k]
            val nEnd = if (k < n - 2) blended(segN[k], segN[k + 1]) else segN[k]
            val base = vertexCount
            for (j in 0..slices) {
                val t = (2.0 * PI * j / slices)
                val c = cos(t).toFloat()
                val s = sin(t).toFloat()
                vertex(x0 + r0 * c, y0, z0 + r0 * s, nStart[0] * c, nStart[1], nStart[0] * s)
                vertex(x0 + r1 * c, y1, z0 + r1 * s, nEnd[0] * c, nEnd[1], nEnd[0] * s)
            }
            for (j in 0 until slices) {
                val a = base + j * 2
                val b = a + 1
                val c2 = a + 2
                val d = a + 3
                // Winding: counter-clockwise seen from outside.
                quad(a, b, d, c2)
            }
        }
    }

    /**
     * Box centred at (cx, cy, cz) with flat normals: first rolled around X by [rollRad]
     * (tilts it in the Y-Z plane), then yawed around Y by [yawRad].
     */
    fun box(cx: Float, cy: Float, cz: Float, sx: Float, sy: Float, sz: Float, yawRad: Float = 0f, rollRad: Float = 0f) {
        val hx = sx / 2; val hy = sy / 2; val hz = sz / 2
        val c = cos(yawRad); val s = sin(yawRad)
        val cr = cos(rollRad); val sr = sin(rollRad)
        fun ry(y: Float, z: Float) = y * cr - z * sr
        fun rz(y: Float, z: Float) = y * sr + z * cr
        fun px(x: Float, z: Float) = cx + x * c + z * s
        fun pz(x: Float, z: Float) = cz - x * s + z * c
        // faces: normal, then 4 corners CCW from outside
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f, hx, -hy, hz, hx, -hy, -hz, hx, hy, -hz, hx, hy, hz),
            floatArrayOf(-1f, 0f, 0f, -hx, -hy, -hz, -hx, -hy, hz, -hx, hy, hz, -hx, hy, -hz),
            floatArrayOf(0f, 1f, 0f, -hx, hy, hz, hx, hy, hz, hx, hy, -hz, -hx, hy, -hz),
            floatArrayOf(0f, -1f, 0f, -hx, -hy, -hz, hx, -hy, -hz, hx, -hy, hz, -hx, -hy, hz),
            floatArrayOf(0f, 0f, 1f, -hx, -hy, hz, hx, -hy, hz, hx, hy, hz, -hx, hy, hz),
            floatArrayOf(0f, 0f, -1f, hx, -hy, -hz, -hx, -hy, -hz, -hx, hy, -hz, hx, hy, -hz),
        )
        for (f in faces) {
            val fny = ry(f[1], f[2])
            val fnz = rz(f[1], f[2])
            val nx = f[0] * c + fnz * s
            val nz = -f[0] * s + fnz * c
            val ids = IntArray(4) { i ->
                val x = f[3 + i * 3]
                val y = ry(f[4 + i * 3], f[5 + i * 3])
                val z = rz(f[4 + i * 3], f[5 + i * 3])
                vertex(px(x, z), cy + y, pz(x, z), nx, fny, nz)
            }
            quad(ids[0], ids[1], ids[2], ids[3])
        }
    }

    fun sphere(cx: Float, cy: Float, cz: Float, r: Float, rings: Int = 12, slices: Int = 18, sy: Float = 1f) {
        val base = vertexCount
        for (i in 0..rings) {
            val phi = PI * i / rings - PI / 2
            val cp = cos(phi).toFloat(); val sp = sin(phi).toFloat()
            for (j in 0..slices) {
                val t = 2.0 * PI * j / slices
                val ct = cos(t).toFloat(); val st = sin(t).toFloat()
                vertex(cx + r * cp * ct, cy + r * sp * sy, cz + r * cp * st, cp * ct, sp / sy, cp * st)
            }
        }
        val w = slices + 1
        for (i in 0 until rings) for (j in 0 until slices) {
            val a = base + i * w + j
            val b = a + w
            quad(a, b, b + 1, a + 1)
        }
    }

    /**
     * Extrudes a simple polygon given in the X-Y plane (counter-clockwise) to thickness
     * `2·halfDepth` along Z, with a chamfer of [bevel] on both faces. Flat shaded.
     */
    fun extrudeBevelled(poly: List<Pair<Float, Float>>, halfDepth: Float, bevel: Float) {
        val inner = shrink(poly, bevel)
        val zMid = halfDepth - bevel
        // Side walls (between the two outer outlines).
        wall(poly, -zMid, poly, zMid)
        // Chamfers: outer outline at ±zMid to inner outline at ±halfDepth.
        wall(poly, zMid, inner, halfDepth)
        wall(inner, -halfDepth, poly, -zMid)
        // Caps.
        val tris = triangulate(inner)
        for (side in intArrayOf(1, -1)) {
            val nz = side.toFloat()
            val ids = IntArray(inner.size) { i -> vertex(inner[i].first, inner[i].second, halfDepth * side, 0f, 0f, nz) }
            for (t in tris) {
                if (side == 1) triangle(ids[t[0]], ids[t[1]], ids[t[2]]) else triangle(ids[t[0]], ids[t[2]], ids[t[1]])
            }
        }
    }

    /** Quads joining outline [a] at depth za to outline [b] at depth zb (same vertex count). */
    private fun wall(a: List<Pair<Float, Float>>, za: Float, b: List<Pair<Float, Float>>, zb: Float) {
        val n = a.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            val p0 = floatArrayOf(a[i].first, a[i].second, za)
            val p1 = floatArrayOf(a[j].first, a[j].second, za)
            val p2 = floatArrayOf(b[j].first, b[j].second, zb)
            val p3 = floatArrayOf(b[i].first, b[i].second, zb)
            // Normal = (p1 - p0) × (p3 - p0)  → outward for a CCW outline when za < zb.
            val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
            val vx = p3[0] - p0[0]; val vy = p3[1] - p0[1]; val vz = p3[2] - p0[2]
            var nx = uy * vz - uz * vy
            var ny = uz * vx - ux * vz
            var nz = ux * vy - uy * vx
            if (nx * nx + ny * ny + nz * nz < 1e-12f) {
                nx = 0f; ny = 0f; nz = if (zb > za) 1f else -1f
            }
            val i0 = vertex(p0[0], p0[1], p0[2], nx, ny, nz)
            val i1 = vertex(p1[0], p1[1], p1[2], nx, ny, nz)
            val i2 = vertex(p2[0], p2[1], p2[2], nx, ny, nz)
            val i3 = vertex(p3[0], p3[1], p3[2], nx, ny, nz)
            // p0,p1 at za; p2,p3 at zb. p0 → p1 → p2 → p3 is counter-clockwise seen from outside.
            quad(i0, i1, i2, i3)
        }
    }

    companion object {
        /** Moves every vertex of a CCW polygon inward along its averaged edge normals. */
        fun shrink(poly: List<Pair<Float, Float>>, d: Float): List<Pair<Float, Float>> {
            val n = poly.size
            return List(n) { i ->
                val p = poly[(i - 1 + n) % n]
                val c = poly[i]
                val q = poly[(i + 1) % n]
                // Inward normal of CCW edge (x,y)→(x',y') is (-dy, dx).
                fun inward(a: Pair<Float, Float>, b: Pair<Float, Float>): FloatArray {
                    val dx = b.first - a.first
                    val dy = b.second - a.second
                    val l = sqrt(dx * dx + dy * dy).let { if (it < 1e-8f) 1f else it }
                    return floatArrayOf(-dy / l, dx / l)
                }
                val n1 = inward(p, c)
                val n2 = inward(c, q)
                var mx = n1[0] + n2[0]
                var my = n1[1] + n2[1]
                val ml = sqrt(mx * mx + my * my)
                if (ml < 1e-6f) {
                    mx = n1[0]; my = n1[1]
                } else {
                    mx /= ml; my /= ml
                }
                // Keep the offset distance ~d along both edges; clamp on very sharp corners.
                val cosHalf = (mx * n1[0] + my * n1[1]).coerceAtLeast(0.5f)
                val k = d / cosHalf
                Pair(c.first + mx * k, c.second + my * k)
            }
        }

        /** Ear-clipping triangulation of a simple CCW polygon; returns index triples. */
        fun triangulate(poly: List<Pair<Float, Float>>): List<IntArray> {
            val out = ArrayList<IntArray>()
            val rest = MutableList(poly.size) { it }
            fun cross(o: Int, a: Int, b: Int): Float {
                val (ox, oy) = poly[o]; val (ax, ay) = poly[a]; val (bx, by) = poly[b]
                return (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)
            }
            fun inside(p: Int, a: Int, b: Int, c: Int): Boolean =
                cross(a, b, p) >= 0 && cross(b, c, p) >= 0 && cross(c, a, p) >= 0
            var guard = 0
            while (rest.size > 3 && guard < 10_000) {
                guard++
                var clipped = false
                for (i in rest.indices) {
                    val a = rest[(i - 1 + rest.size) % rest.size]
                    val b = rest[i]
                    val c = rest[(i + 1) % rest.size]
                    if (cross(a, b, c) <= 0f) continue // reflex
                    var ear = true
                    for (p in rest) {
                        if (p == a || p == b || p == c) continue
                        if (inside(p, a, b, c)) {
                            ear = false
                            break
                        }
                    }
                    if (!ear) continue
                    out += intArrayOf(a, b, c)
                    rest.removeAt(i)
                    clipped = true
                    break
                }
                if (!clipped) break // degenerate input: stop instead of looping forever
            }
            if (rest.size == 3) out += intArrayOf(rest[0], rest[1], rest[2])
            return out
        }

        /** Angle helper used by tests. */
        fun angleDeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
            val d = (ax * bx + ay * by) / (sqrt(ax * ax + ay * ay) * sqrt(bx * bx + by * by))
            return Math.toDegrees(acos(d.coerceIn(-1f, 1f).toDouble())).toFloat()
        }
    }
}
