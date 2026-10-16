package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * GX-03 guard for the *composition* layer: the shared drawing list is what both the table painter
 * and the SVG review sheet execute, so it gets its own checks — every face is drawable, nothing
 * escapes the card, the mirrored half is really mirrored, and the art uses the tokens' colours and
 * no one-off literals.
 */
class HokmCardDrawingTest {

    private fun allOps(plan: CardRenderPlan): List<CardOp> = CardFaceDrawing.of(plan)

    /** The extreme points a painted op can reach — used to prove it stays inside its clip. */
    private fun CardOp.extremes(): List<Pair<Float, Float>> = when (this) {
        is CardOp.Rect -> listOf(
            left to top,
            (left + width) to top,
            left to (top + height),
            (left + width) to (top + height),
        )

        is CardOp.Paths -> shapes.flatMap { shape ->
            listOf(
                shape.cx to shape.cy,
                (shape.cx - shape.width / 2f) to (shape.cy - shape.height * CardMetrics.ASPECT / 2f),
                (shape.cx + shape.width / 2f) to (shape.cy + shape.height * CardMetrics.ASPECT / 2f),
            )
        }

        is CardOp.Line -> listOf(x1 to y1, x2 to y2)
        is CardOp.Dot -> listOf(
            (cx - radius) to (cy - radius * CardMetrics.ASPECT),
            (cx + radius) to (cy + radius * CardMetrics.ASPECT),
        )

        is CardOp.Text -> listOf(left to top)
        else -> emptyList()
    }

    /** Every op, including the ones nested inside clip groups. */
    private fun flatten(ops: List<CardOp>): List<CardOp> = ops.flatMap { op ->
        if (op is CardOp.Clip) listOf(op) + flatten(op.ops) else listOf(op)
    }

    @Test
    fun `every one of the 52 faces composes into a drawable list`() {
        CardFaceCatalog.all().forEach { plan ->
            val ops = allOps(plan)
            assertTrue(ops.size >= 5, "${plan.code}: too few ops (${ops.size})")
            assertTrue(ops.first() is CardOp.Rect, "${plan.code}: the stock must be painted first")
            assertTrue(ops.any { it is CardOp.Bezel }, "${plan.code}: the edge bevel is missing")
            // the corner index is drawn twice: upright and spun 180° into the opposite corner
            val texts = flatten(ops).filterIsInstance<CardOp.Text>()
            assertEquals(2, texts.count { it.text == plan.index.glyph }, "${plan.code}: corner index count")
            assertEquals(1, texts.count { it.rotationDeg == 180f }, "${plan.code}: mirrored index")
            when {
                plan.aceOrnate -> assertTrue(
                    flatten(ops).filterIsInstance<CardOp.Paths>().any { it.strokeArgb != null },
                    "${plan.code}: the ace needs its halo",
                )

                plan.isCourt -> assertTrue(
                    ops.any { it is CardOp.Line } && flatten(ops).any { it is CardOp.Clip },
                    "${plan.code}: a court card needs the parchment's diagonal split",
                )

                else -> {
                    // pips are the only ops sized exactly like the plan's pip glyph
                    val pipOps = flatten(ops).filterIsInstance<CardOp.Paths>().filter { op ->
                        val shape = op.shapes.singleOrNull()
                        shape != null && shape.width == plan.pipField.pipSize && op.fillArgb == plan.inkArgb
                    }
                    assertEquals(plan.pips.size, pipOps.size, "${plan.code}: pip ops must match the pip layout")
                }
            }
        }
    }

    @Test
    fun `nothing is drawn outside the card`() {
        fun check(x: Float, y: Float, label: String, code: String) {
            assertTrue(x in -0.25f..1.25f, "$code: $label runs off the card horizontally (x=$x)")
            assertTrue(y in -0.25f..1.25f, "$code: $label runs off the card vertically (y=$y)")
        }
        CardFaceCatalog.all().forEach { plan ->
            flatten(allOps(plan)).forEach { op ->
                when (op) {
                    is CardOp.Rect -> {
                        check(op.left, op.top, "rect", plan.code)
                        check(op.left + op.width, op.top + op.height, "rect", plan.code)
                    }
                    is CardOp.Paths -> op.shapes.forEach { s ->
                        check(s.cx - s.width, s.cy - s.height, "glyph", plan.code)
                        check(s.cx + s.width, s.cy + s.height, "glyph", plan.code)
                    }
                    is CardOp.Line -> {
                        check(op.x1, op.y1, "line", plan.code)
                        check(op.x2, op.y2, "line", plan.code)
                    }
                    is CardOp.Dot -> {
                        check(op.cx - op.radius, op.cy - op.radius / CardMetrics.ASPECT, "dot", plan.code)
                        check(op.cx + op.radius, op.cy + op.radius / CardMetrics.ASPECT, "dot", plan.code)
                    }
                    is CardOp.Text -> check(op.left, op.top, "text", plan.code)
                    is CardOp.Bezel, is CardOp.Clip -> Unit
                }
            }
        }
    }

    @Test
    fun `the art only uses token colours`() {
        val allowed = setOf(
            HokmTokens.Color.CARD_IVORY,
            HokmTokens.Color.CARD_IVORY_SHADE,
            HokmTokens.Color.CARD_EDGE,
            HokmTokens.Color.IVORY,
            HokmTokens.Color.INK_BLACK,
            HokmTokens.Color.INK_RED,
            HokmTokens.Color.INK_BLUE,
            HokmTokens.Color.INK_GREEN,
            HokmTokens.Color.CARD_GOLD,
            HokmTokens.Color.COURT_PARCHMENT_TOP,
            HokmTokens.Color.COURT_PARCHMENT_BOTTOM,
        )
        val accents = CourtFigures.ALL.map { it.accentArgb }.toSet()
        CardFaceCatalog.all().forEach { plan ->
            flatten(allOps(plan)).forEach { op ->
                val colours = when (op) {
                    is CardOp.Rect -> listOfNotNull(op.fillArgb, op.strokeArgb) + listOfNotNull(op.fill?.from, op.fill?.to)
                    is CardOp.Paths -> listOfNotNull(op.fillArgb, op.strokeArgb)
                    is CardOp.Line -> listOf(op.argb)
                    is CardOp.Dot -> listOf(op.argb)
                    is CardOp.Text -> listOf(op.argb)
                    is CardOp.Bezel -> listOf(op.lightArgb, op.shadeArgb)
                    is CardOp.Clip -> emptyList()
                }
                colours.forEach { argb ->
                    assertTrue(
                        argb in allowed || argb in accents,
                        "${plan.code}: colour ${argb.toString(16)} is not a token or a figure accent",
                    )
                }
            }
        }
    }

    @Test
    fun `the two court portraits are cut apart by the diagonal`() {
        // A real court card cuts its figures at the diagonal, so an op of one half may reach across
        // it — the **clip** is what keeps the halves apart. The invariant that matters is therefore
        // that the two clip regions are disjoint, that both halves are actually painted, and that
        // the lower half is the true 180° mirror of the upper one. (The earlier bug was not a
        // clipped overflow: it was a mirrored robe painted *outside* any clip.)
        fun inside(px: Float, py: Float, a: Pair<Float, Float>, b: Pair<Float, Float>, c: Pair<Float, Float>): Boolean {
            fun sign(p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>) =
                (p1.first - p3.first) * (p2.second - p3.second) - (p2.first - p3.first) * (p1.second - p3.second)

            val d1 = sign(px to py, a, b)
            val d2 = sign(px to py, b, c)
            val d3 = sign(px to py, c, a)
            return !((d1 < 0f || d2 < 0f || d3 < 0f) && (d1 > 0f || d2 > 0f || d3 > 0f))
        }

        CardFaceCatalog.all().filter { it.isCourt }.forEach { plan ->
            val clips = allOps(plan).filterIsInstance<CardOp.Clip>()
            assertEquals(2, clips.size, "${plan.code}: a court card needs exactly two portrait regions")
            val triangles = clips.map { clip ->
                clip.region.mapNotNull { command ->
                    when (command) {
                        is GlyphCommand.MoveTo -> command.x to command.y
                        is GlyphCommand.LineTo -> command.x to command.y
                        else -> null
                    }
                }.also { assertEquals(3, it.size, "${plan.code}: the region must be a triangle") }
            }

            // 1. the regions do not overlap: sample the card and prove no point is in both
            val (upper, lower) = triangles
            var samples = 0
            for (i in 0..40) {
                for (j in 0..40) {
                    val x = i / 40f
                    val y = j / 40f
                    assertTrue(
                        !(inside(x, y, upper[0], upper[1], upper[2]) && inside(x, y, lower[0], lower[1], lower[2])),
                        "${plan.code}: the two portraits share the point ($x, $y) — they would overlap",
                    )
                    if (inside(x, y, upper[0], upper[1], upper[2])) samples++
                }
            }
            assertTrue(samples > 100, "${plan.code}: the upper portrait region is too small to hold a figure")

            // 2. both halves are painted, and the lower one is the mirror of the upper one
            val upperOps = flatten(clips[0].ops)
            val lowerOps = flatten(clips[1].ops)
            assertEquals(upperOps.size, lowerOps.size, "${plan.code}: the two halves must mirror each other")
            val upperPaths = upperOps.filterIsInstance<CardOp.Paths>()
            val lowerPaths = lowerOps.filterIsInstance<CardOp.Paths>()
            assertEquals(upperPaths.size, lowerPaths.size, "${plan.code}: path counts must mirror")
            upperPaths.zip(lowerPaths).forEachIndexed { i, (up, down) ->
                assertEquals(up.alpha, down.alpha, "${plan.code}: op $i changed alpha in the mirror")
                assertEquals(up.fillArgb, down.fillArgb, "${plan.code}: op $i changed colour in the mirror")
                up.shapes.zip(down.shapes).forEach { (u, d) ->
                    assertEquals(180f, d.rotationDeg - u.rotationDeg, "${plan.code}: op $i is not rotated")
                }
            }
            // the centre of the mirror is the middle of the parchment frame
            val frameCx = CardMetrics.COURT_FRAME_LEFT + CardMetrics.COURT_FRAME_WIDTH / 2f
            val frameCy = CardMetrics.COURT_FRAME_TOP + CardMetrics.COURT_FRAME_HEIGHT / 2f
            upperPaths.zip(lowerPaths).forEach { (u, d) ->
                assertEquals(2f * frameCx, u.shapes.first().cx + d.shapes.first().cx, 0.002f, "${plan.code}: mirror axis")
                assertEquals(2f * frameCy, u.shapes.first().cy + d.shapes.first().cy, 0.002f, "${plan.code}: mirror axis")
            }
        }
    }

    @Test
    fun `the two court figures are mirrored, not duplicated`() {
        val kings = CardFaceCatalog.all().filter { it.rank == Rank.KING }
        assertEquals(4, kings.size, "four kings")
        kings.forEach { plan ->
            val ops = allOps(plan)
            // the two head-dresses sit on opposite sides of the parchment's diagonal
            val clips = ops.filterIsInstance<CardOp.Clip>()
            assertEquals(2, clips.size, "${plan.code}: the two portraits live in their own clip regions")
            val halves = clips.map { clip ->
                flatten(clip.ops).filterIsInstance<CardOp.Paths>()
                    .filter { it.fillArgb == HokmTokens.Color.CARD_GOLD && it.strokeArgb != null }
                    .single().shapes.first()
            }
            assertEquals(180f, halves[1].rotationDeg - halves[0].rotationDeg, "${plan.code}: the lower figure must be rotated")
            // the two head-dresses are mirror images about the frame's centre, not copies
            assertEquals(2f * (CardMetrics.COURT_FRAME_LEFT + CardMetrics.COURT_FRAME_WIDTH / 2f), halves[0].cx + halves[1].cx, 0.001f)
            assertEquals(2f * (CardMetrics.COURT_FRAME_TOP + CardMetrics.COURT_FRAME_HEIGHT / 2f), halves[0].cy + halves[1].cy, 0.001f)
        }
    }

    @Test
    fun `every court prop is thick enough to see at table size`() {
        // The first court figures failed here: a sword whose blade was ~1% of the card width turned
        // into an invisible hairline at 44 dp. A prop is only useful if its thinnest part survives.
        val smallestCardWidthDp = 44f
        CardFaceCatalog.all().filter { it.isCourt }.forEach { plan ->
            val ops = flatten(allOps(plan))
            val propOp = ops.filterIsInstance<CardOp.Paths>().firstOrNull { it.alpha < 1f }
            assertTrue(propOp != null, "${plan.code}: the court figure has no prop")
            val prop = propOp!!.shapes.single()
            // the glyph's *shaft* is only part of its bounding width; a third of it is a fair
            // estimate of the slimmest part that must still be visible
            val cardWidthFraction = prop.width / 3f
            val thinnestPx = cardWidthFraction * smallestCardWidthDp
            assertTrue(
                thinnestPx >= 0.9f,
                "${plan.code}: the prop's width is only ${"%.2f".format(thinnestPx)} dp on a 44 dp card " +
                    "— it would vanish on the table",
            )
        }
    }

    @Test
    fun `every back composes and keeps its field readable`() {
        HokmCardBacks.ALL.forEach { spec ->
            val ops = CardBackDrawing.of(spec)
            val all = flatten(ops)
            assertTrue(all.size > 20, "${spec.id}: the lattice is missing")
            val pattern = flatten(ops).filterIsInstance<CardOp.Paths>()
                .count { it.fillArgb == spec.patternArgb || it.strokeArgb == spec.patternArgb }
            assertTrue(pattern >= 8, "${spec.id}: too few lattice cells ($pattern)")
            // the centre medallion, its star motif and the accent bead are all there
            assertTrue(
                all.any { it is CardOp.Dot && it.radius == spec.medallionFraction },
                "${spec.id}: the medallion is missing",
            )
            assertTrue(
                all.any { it is CardOp.Paths && it.fillArgb == spec.frameArgb },
                "${spec.id}: the centre motif is missing",
            )
            assertTrue(
                all.any { it is CardOp.Paths && it.fillArgb == spec.accentArgb },
                "${spec.id}: the accent bead is missing",
            )
            assertTrue(
                HokmArtDirection.contrastRatio(spec.frameArgb, spec.fieldEdgeArgb) >= 3f,
                "${spec.id}: the motif vanishes on the medallion",
            )
            ops.forEach { op ->
                when (op) {
                    is CardOp.Rect -> assertTrue(op.left >= -0.01f && op.top >= -0.01f, "${spec.id}: rect escapes the card")
                    else -> Unit
                }
            }
        }
    }

    @Test
    fun `the drawing is deterministic`() {
        val first = CardFaceCatalog.all().joinToString("|") { plan -> CardFaceDrawing.of(plan).toString() }
        val second = CardFaceCatalog.all().joinToString("|") { plan -> CardFaceDrawing.of(plan).toString() }
        assertEquals(first, second)
        HokmCardBacks.ALL.forEach { spec ->
            assertEquals(CardBackDrawing.of(spec).toString(), CardBackDrawing.of(spec).toString())
        }
    }
}
