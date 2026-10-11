package ir.dorehami.games.chess.ui.gl

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Staunton-style pieces built from turned profiles (a real chess set is made on a lathe, so is
 * ours). One square of the board = 1 unit; pieces stand on y = 0 and are centred on the Y axis.
 * The knight faces +X.
 */
object PieceMeshes {
    /** Piece types use the same numbers as the rules (`Piece.PAWN = 1 … Piece.KING = 6`). */
    fun build(type: Int, slices: Int = 40): MeshData {
        val mb = MeshBuilder()
        when (type) {
            1 -> pawn(mb, slices)
            2 -> knight(mb, slices)
            3 -> bishop(mb, slices)
            4 -> rook(mb, slices)
            5 -> queen(mb, slices)
            6 -> king(mb, slices)
            else -> error("unknown piece type $type")
        }
        return mb.build()
    }

    private class Profile {
        val pts = ArrayList<Pair<Float, Float>>()

        fun p(r: Float, y: Float): Profile {
            pts += Pair(r, y)
            return this
        }

        /** Arc of a circle in profile space, angles in degrees (0 = outward, 90 = up). */
        fun arc(cx: Float, cy: Float, radius: Float, a0: Float, a1: Float, steps: Int = 6): Profile {
            for (i in 0..steps) {
                val a = Math.toRadians((a0 + (a1 - a0) * i / steps).toDouble())
                pts += Pair(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
            }
            return this
        }
    }

    /** Common turned foot: flat bottom, a rounded bead, a cove and a small ring. */
    private fun base(r: Float): Profile = Profile()
        .p(0f, 0f)
        .p(r, 0f)
        .p(r, 0.025f)
        .arc(r - 0.012f, 0.052f, 0.028f, -70f, 80f, 7)
        .p(r * 0.82f, 0.098f)
        .p(r * 0.76f, 0.112f)
        .arc(r * 0.78f, 0.13f, 0.018f, -90f, 90f, 6)
        .p(r * 0.68f, 0.16f)

    private fun collar(p: Profile, r: Float, y: Float, inner: Float): Profile = p
        .p(r, y)
        .arc(r, y + 0.015f, 0.015f, -90f, 90f, 5)
        .p(inner, y + 0.035f)

    private fun pawn(mb: MeshBuilder, slices: Int) {
        val p = base(0.30f)
            .p(0.19f, 0.185f)
            .p(0.145f, 0.25f)
            .p(0.118f, 0.32f)
            .p(0.105f, 0.36f)
        collar(p, 0.17f, 0.37f, 0.10f)
        p.arc(0f, 0.515f, 0.115f, -55f, 90f, 14)
        mb.lathe(p.pts, slices)
    }

    private fun rook(mb: MeshBuilder, slices: Int) {
        val p = base(0.33f)
            .p(0.25f, 0.185f)
            .p(0.225f, 0.24f)
            .p(0.205f, 0.45f)
            .p(0.215f, 0.50f)
            .p(0.25f, 0.52f)
            .p(0.25f, 0.54f)
            .p(0.225f, 0.555f)
            .p(0.24f, 0.58f)
            .p(0.27f, 0.60f)
            .p(0.27f, 0.665f)
            .p(0.19f, 0.665f)
            .p(0.19f, 0.625f)
            .p(0f, 0.625f)
        mb.lathe(p.pts, slices)
        // Battlements: six merlons around the rim.
        val count = 6
        for (i in 0 until count) {
            val a = (2.0 * PI * (i + 0.5) / count).toFloat()
            mb.box(0.23f * cos(a), 0.70f, 0.23f * sin(a), 0.08f, 0.07f, 0.13f, yawRad = -a)
        }
    }

    /** Horse-head silhouette (x = back→nose, y = bottom→ears), normalised to the unit square. */
    private val KNIGHT_OUTLINE = listOf(
        0.12f to 0.00f,
        0.88f to 0.00f,
        0.82f to 0.12f,
        0.72f to 0.27f,
        0.67f to 0.38f,
        0.76f to 0.46f,
        0.92f to 0.50f,
        0.99f to 0.56f,
        1.00f to 0.62f,
        0.96f to 0.68f,
        0.84f to 0.76f,
        0.74f to 0.84f,
        0.69f to 0.93f,
        0.62f to 1.00f,
        0.56f to 0.93f,
        0.50f to 0.91f,
        0.40f to 0.88f,
        0.28f to 0.79f,
        0.19f to 0.64f,
        0.13f to 0.47f,
        0.10f to 0.30f,
        0.08f to 0.14f,
    )

    private fun knight(mb: MeshBuilder, slices: Int) {
        val p = base(0.33f)
            .p(0.25f, 0.185f)
            .p(0.235f, 0.215f)
            .p(0.24f, 0.235f)
            .p(0f, 0.235f)
        mb.lathe(p.pts, slices)
        val x0 = -0.25f
        val w = 0.50f
        val y0 = 0.21f
        val h = 0.66f
        val outline = KNIGHT_OUTLINE.map { (x, y) -> Pair(x0 + x * w, y0 + y * h) }
        mb.extrudeBevelled(outline, halfDepth = 0.12f, bevel = 0.035f)
        // Eyes and nostrils.
        mb.shade = 0.25f
        for (side in intArrayOf(1, -1)) {
            mb.sphere(x0 + 0.70f * w, y0 + 0.74f * h, side * 0.115f, 0.024f, 8, 12)
            mb.sphere(x0 + 0.96f * w, y0 + 0.60f * h, side * 0.07f, 0.016f, 6, 10)
        }
        mb.shade = 1f
        // Mane: a row of small ridges down the back of the neck.
        for (i in 0 until 6) {
            val t = i / 5f
            val x = x0 + (0.42f - 0.30f * t) * w
            val y = y0 + (0.90f - 0.50f * t) * h
            mb.box(x, y, 0f, 0.05f, 0.05f, 0.09f, yawRad = 0.5f)
        }
    }

    private fun bishop(mb: MeshBuilder, slices: Int) {
        val p = base(0.32f)
            .p(0.21f, 0.185f)
            .p(0.15f, 0.26f)
            .p(0.112f, 0.40f)
            .p(0.10f, 0.50f)
        collar(p, 0.17f, 0.515f, 0.11f)
        p.p(0.15f, 0.565f)
            .p(0.15f, 0.58f)
            .p(0.10f, 0.592f)
            .p(0.11f, 0.615f)
            .p(0.128f, 0.65f)
            .p(0.135f, 0.69f)
            .p(0.128f, 0.735f)
            .p(0.108f, 0.78f)
            .p(0.078f, 0.825f)
            .p(0.04f, 0.862f)
            .p(0.022f, 0.874f)
            .arc(0f, 0.903f, 0.034f, -42f, 90f, 8)
        mb.lathe(p.pts, slices)
        // The mitre's diagonal slit: a thin dark wedge set into the surface.
        mb.shade = 0.18f
        mb.box(0.118f, 0.725f, 0f, 0.05f, 0.13f, 0.022f, rollRad = 0.6f)
        mb.shade = 1f
    }

    private fun queen(mb: MeshBuilder, slices: Int) {
        val p = base(0.35f)
            .p(0.23f, 0.185f)
            .p(0.16f, 0.27f)
            .p(0.122f, 0.45f)
            .p(0.11f, 0.58f)
        collar(p, 0.19f, 0.60f, 0.12f)
        p.p(0.17f, 0.65f)
            .p(0.17f, 0.665f)
            .p(0.12f, 0.676f)
            .p(0.13f, 0.70f)
            .p(0.17f, 0.78f)
            .p(0.205f, 0.855f)
            .p(0.215f, 0.88f)
            .p(0.17f, 0.885f)
            .p(0.13f, 0.915f)
            .p(0.08f, 0.947f)
            .p(0.03f, 0.962f)
            .p(0f, 0.965f)
        mb.lathe(p.pts, slices)
        mb.sphere(0f, 1.0f, 0f, 0.045f, 10, 16)
        val n = 9
        for (i in 0 until n) {
            val a = (2.0 * PI * i / n).toFloat()
            mb.sphere(0.20f * cos(a), 0.905f, 0.20f * sin(a), 0.029f, 8, 12)
        }
    }

    private fun king(mb: MeshBuilder, slices: Int) {
        val p = base(0.36f)
            .p(0.24f, 0.185f)
            .p(0.17f, 0.27f)
            .p(0.132f, 0.47f)
            .p(0.12f, 0.62f)
        collar(p, 0.20f, 0.64f, 0.13f)
        p.p(0.18f, 0.69f)
            .p(0.18f, 0.705f)
            .p(0.13f, 0.716f)
            .p(0.14f, 0.74f)
            .p(0.18f, 0.82f)
            .p(0.20f, 0.895f)
            .p(0.20f, 0.92f)
            .p(0.15f, 0.94f)
            .p(0.10f, 0.968f)
            .p(0.05f, 0.984f)
            .p(0f, 0.99f)
        mb.lathe(p.pts, slices)
        mb.sphere(0f, 0.995f, 0f, 0.04f, 8, 14)
        mb.box(0f, 1.085f, 0f, 0.055f, 0.19f, 0.055f)
        mb.box(0f, 1.11f, 0f, 0.165f, 0.05f, 0.055f)
    }
}
