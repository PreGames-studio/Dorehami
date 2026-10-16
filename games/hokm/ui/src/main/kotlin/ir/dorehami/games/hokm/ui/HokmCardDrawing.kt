package ir.dorehami.games.hokm.ui

/**
 * GX-03 — a card as a **list of drawing operations**, not as drawing code.
 *
 * [HokmCardFaces] says *what* is on a card (ranks, inks, pips, figures, backs); this file says *how*
 * those pieces compose into one card. Two executors run the very same list:
 *
 * * the table (`PlayingCardView`) turns each op into Compose `drawPath` / `drawText`;
 * * the review sheet (`tools/hokm/CardSheet.kt`) turns each op into SVG — so the owner can inspect
 *   all 52 faces without an APK **and** see exactly what the app draws.
 *
 * Size convention (one rule, no exceptions):
 * * `x`-style fields (`x`, `left`, `width`, `height`, `radius`, `pipSize`, `sizeFraction`) are
 *   fractions of the card **width**;
 * * `y`-style fields (`y`, `top`) are fractions of the card **height**;
 * * [hy] converts a width-fraction into the matching height-fraction, so a shape drawn with the
 *   same number for width and height stays square on screen (the card is 0.7 wide per unit height).
 *
 * Colours are opaque ARGB longs straight from the tokens, so both executors agree to the bit.
 */
data class PlacedGlyph(
    val commands: List<GlyphCommand>,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float,
    val rotationDeg: Float = 0f,
)

enum class GradientKind { VERTICAL, RADIAL }

/** A gradient between two opaque colours (ARGB longs). */
data class GradientSpec(val from: Long, val to: Long, val kind: GradientKind = GradientKind.VERTICAL)

/** Maps every anchor and control point of a command list through [map] (used for mirroring). */
fun List<GlyphCommand>.mapCodePoints(map: (Float, Float) -> Pair<Float, Float>): List<GlyphCommand> = map { c ->
    when (c) {
        is GlyphCommand.MoveTo -> map(c.x, c.y).let { GlyphCommand.MoveTo(it.first, it.second) }
        is GlyphCommand.LineTo -> map(c.x, c.y).let { GlyphCommand.LineTo(it.first, it.second) }
        is GlyphCommand.QuadTo -> map(c.cx, c.cy).let { ctl ->
            map(c.x, c.y).let { GlyphCommand.QuadTo(ctl.first, ctl.second, it.first, it.second) }
        }

        is GlyphCommand.CurveTo -> {
            val c1 = map(c.c1x, c.c1y)
            val c2 = map(c.c2x, c.c2y)
            val p = map(c.x, c.y)
            GlyphCommand.CurveTo(c1.first, c1.second, c2.first, c2.second, p.first, p.second)
        }

        GlyphCommand.Close -> GlyphCommand.Close
    }
}

/** Vertical: a width-fraction expressed as a height-fraction. */
fun hy(widthFraction: Float): Float = widthFraction * CardMetrics.ASPECT

sealed interface CardOp {

    /** One or more glyph outlines painted with a fill, an optional gradient and an optional outline. */
    data class Paths(
        val shapes: List<PlacedGlyph>,
        val fillArgb: Long? = null,
        val fill: GradientSpec? = null,
        val strokeArgb: Long? = null,
        val strokeFraction: Float = 0.008f,
        val alpha: Float = 1f,
    ) : CardOp

    /** An axis-aligned rectangle (optionally rounded) with a fill and/or an outline. */
    data class Rect(
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val radius: Float = 0f,
        val fillArgb: Long? = null,
        val fill: GradientSpec? = null,
        val strokeArgb: Long? = null,
        val strokeFraction: Float = 0.01f,
        val alpha: Float = 1f,
    ) : CardOp

    /** A straight line between two points. */
    data class Line(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val argb: Long,
        val strokeFraction: Float,
        val alpha: Float = 1f,
    ) : CardOp

    /** A filled or outlined circle. */
    data class Dot(
        val cx: Float,
        val cy: Float,
        val radius: Float,
        val argb: Long,
        val alpha: Float = 1f,
        val stroke: Boolean = false,
        val strokeFraction: Float = 0.01f,
    ) : CardOp

    /**
     * A text run (corner index). [left] / [top] are the top-left corner of the run and
     * [sizeFraction] its font size (a width-fraction). A rotation happens about [rotationPivot]
     * (card fractions) when given, otherwise about the run's own centre — so the bottom-right index
     * is literally the top-left one spun 180° around the middle of the card.
     */
    data class Text(
        val text: String,
        val left: Float,
        val top: Float,
        val sizeFraction: Float,
        val argb: Long,
        val bold: Boolean = true,
        val serif: Boolean = false,
        val rotationDeg: Float = 0f,
        val rotationPivot: Pair<Float, Float>? = null,
        val alpha: Float = 1f,
    ) : CardOp

    /**
     * A clipped group: [ops] are painted only inside [region] (a closed outline in card
     * fractions). This is what keeps the two court portraits on their own side of the diagonal —
     * the authentic double-portrait cut, and the only way a big, readable figure can fill half a
     * card without ever covering its twin.
     */
    /**
     * A clipped group: [ops] are painted only inside [region]. Unlike [PlacedGlyph], the region's
     * commands are in **absolute card fractions** (`x` of width, `y` of height) — a clip outline is
     * never scaled or rotated, which keeps the court card's diagonal cut exactly where it is drawn.
     */
    data class Clip(val region: List<GlyphCommand>, val ops: List<CardOp>) : CardOp

    /**
     * The bevelled hairline of the paper edge: a rounded-rect stroke whose colour runs from a
     * highlight at the top-left to a shade at the bottom-right.
     */
    data class Bezel(
        val cornerRadius: Float,
        val edgeWidth: Float,
        val lightArgb: Long,
        val shadeArgb: Long,
        val lightAlpha: Float,
        val shadeAlpha: Float,
    ) : CardOp
}

private const val PAPER = HokmTokens.Color.CARD_IVORY
private const val PAPER_SHADE = HokmTokens.Color.CARD_IVORY_SHADE
private const val GOLD = HokmTokens.Color.CARD_GOLD
private const val PARCHMENT_TOP = HokmTokens.Color.COURT_PARCHMENT_TOP
private const val PARCHMENT_BOTTOM = HokmTokens.Color.COURT_PARCHMENT_BOTTOM

/** Composition of one card face, built from its [CardRenderPlan]. */
object CardFaceDrawing {

    /** Everything the table paints for [plan], in paint order. */
    fun of(plan: CardRenderPlan): List<CardOp> {
        val ops = ArrayList<CardOp>()
        val ink = plan.inkArgb
        val bezel = plan.bezel

        // ---- paper, then the bevelled edge
        ops += CardOp.Rect(
            left = 0f,
            top = 0f,
            width = 1f,
            height = 1f,
            radius = bezel.cornerRadiusFraction,
            fill = GradientSpec(PAPER, PAPER_SHADE),
        )
        ops += CardOp.Bezel(
            cornerRadius = bezel.cornerRadiusFraction,
            edgeWidth = bezel.edgeWidthFraction,
            lightArgb = HokmTokens.Color.IVORY,
            shadeArgb = HokmTokens.Color.INK_BLACK,
            lightAlpha = bezel.lightAlpha,
            shadeAlpha = bezel.shadeAlpha,
        )

        // ---- corner indices (top-left, then the same spun 180° into the bottom-right)
        cardIndexOps(plan, ink).forEach { ops += it }

        // ---- centre: ace symbol, court figure or pips
        when {
            plan.aceOrnate -> {
                val size = CardMetrics.ACE_SIZE_FRACTION
                val glyph = PlacedGlyph(CardGlyphs.suit(plan.suit).commands, 0.5f, 0.5f, size, size)
                ops += CardOp.Paths(
                    shapes = listOf(glyph),
                    strokeArgb = ink,
                    strokeFraction = size * 0.10f,
                    alpha = 0.14f,
                )
                ops += CardOp.Paths(shapes = listOf(glyph), fillArgb = ink)
            }

            plan.court != null -> courtOps(plan, ink).forEach { ops += it }
            else -> pipOps(plan, ink).forEach { ops += it }
        }
        return ops
    }

    private fun cardIndexOps(plan: CardRenderPlan, ink: Long): List<CardOp> {
        val pad = plan.index.padFraction
        val size = plan.index.fraction
        val suitSize = size * plan.index.suitScale
        val suitCx = pad + CardMetrics.indexWidthFraction(plan.rank, plan.jumbo) / 2f
        val suitCy = pad * 0.6f + size * 1.15f + suitSize * 0.55f
        val suit = PlacedGlyph(CardGlyphs.suit(plan.suit).commands, suitCx, suitCy, suitSize, suitSize)
        return listOf(
            CardOp.Text(
                text = plan.index.glyph,
                left = pad,
                top = pad * 0.6f,
                sizeFraction = size,
                argb = ink,
            ),
            CardOp.Paths(shapes = listOf(suit), fillArgb = ink),
            CardOp.Text(
                text = plan.index.glyph,
                left = pad,
                top = pad * 0.6f,
                sizeFraction = size,
                argb = ink,
                rotationDeg = 180f,
                rotationPivot = 0.5f to 0.5f,
            ),
            CardOp.Paths(shapes = listOf(suit.copy(rotationDeg = 180f)), fillArgb = ink),
        )
    }

    private fun pipOps(plan: CardRenderPlan, ink: Long): List<CardOp> {
        val field = plan.pipField
        if (plan.pips.isEmpty()) return emptyList()
        val glyph = CardGlyphs.suit(plan.suit).commands
        return plan.pips.map { pip ->
            CardOp.Paths(
                shapes = listOf(
                    PlacedGlyph(
                        commands = glyph,
                        cx = field.left + field.width * pip.x,
                        cy = field.top + field.height * pip.y,
                        width = field.pipSize,
                        height = field.pipSize,
                        rotationDeg = if (pip.rotated) 180f else 0f,
                    ),
                ),
                fillArgb = ink,
            )
        }
    }

    /**
     * The court composition: a parchment inset cut by a diagonal, carrying one figure design and
     * its true 180° mirror about the frame's centre — the classic double portrait.
     *
     * Two structural decisions make it read correctly:
     * * each portrait is **clipped to its own side of the diagonal**, so a big, legible figure can
     *   fill half the card without ever covering its twin (the failure of the first two versions);
     * * inside a portrait the prop is painted **over** the robe, so the figure visibly holds it.
     */
    private fun courtOps(plan: CardRenderPlan, ink: Long): List<CardOp> {
        val figure = requireNotNull(plan.court)
        val left = CardMetrics.COURT_FRAME_LEFT
        val top = CardMetrics.COURT_FRAME_TOP
        val fw = CardMetrics.COURT_FRAME_WIDTH
        val fh = CardMetrics.COURT_FRAME_HEIGHT
        val frameCx = left + fw / 2f
        val frameCy = top + fh / 2f

        fun fx(a: Float) = left + fw * a
        fun fy(b: Float) = top + fh * b
        fun gw(a: Float) = fw * a
        fun CardOp.mirror(): CardOp = mirroredAbout(frameCx, frameCy)

        val ops = ArrayList<CardOp>()

        // ---- parchment inset, brass hairline, diagonal split
        ops += CardOp.Rect(
            left = left, top = top, width = fw, height = fh,
            fill = GradientSpec(PARCHMENT_TOP, PARCHMENT_BOTTOM),
            strokeArgb = ink,
            strokeFraction = 0.012f,
        )
        ops += CardOp.Rect(
            left = left + gw(0.03f), top = top + fh * 0.03f,
            width = fw - gw(0.06f), height = fh - fh * 0.06f,
            strokeArgb = GOLD,
            strokeFraction = 0.007f,
        )
        ops += CardOp.Line(
            x1 = left, y1 = top + fh, x2 = left + fw, y2 = top,
            argb = ink, strokeFraction = 0.005f, alpha = CardMetrics.COURT_SPLIT_ALPHA,
        )

        // ---- the figure's geometry, all frame-relative
        val headW = gw(figure.headScale)
        val headH = headW * 1.2f
        val headHFrame = headH * CardMetrics.ASPECT / fh
        val dressW = gw(figure.headDressScale)
        val dressH = dressW * 0.8f
        val dressHFrame = dressH * CardMetrics.ASPECT / fh
        val robeW = gw(figure.robeWidth)
        val robeH = fh * figure.robeHeight
        val medallion = gw(figure.medallionScale)
        val propH = fh * figure.propHeight
        val propW = propH * figure.propWidth

        // The bust sits inside its triangle: head at the outer top, shoulders and robe below it, and
        // the prop at the hand — up towards the diagonal, which cuts the figure like a real deck.
        val headFx = 0.34f
        val headFy = 0.25f
        val headCx = fx(headFx)
        val headCy = fy(headFy)
        val robeCy = fy(headFy + headHFrame * 0.46f + figure.robeHeight * 0.42f)
        val dressCy = fy(headFy - headHFrame * 0.5f - dressHFrame * 0.34f)
        val propCx = fx(0.68f)
        val propCy = fy(0.34f)
        val medallionCy = robeCy - robeH * 0.26f

        val robe = CardGlyphs.byId(figure.robeGlyphId).commands
        val head = CardGlyphs.HEAD.commands
        val dress = CardGlyphs.byId(figure.headGlyphId).commands
        val prop = CardGlyphs.byId(figure.propGlyphId).commands
        val beard = CardGlyphs.BEARD.commands
        val hair = CardGlyphs.HAIR.commands
        val suitGlyph = CardGlyphs.suit(plan.suit).commands

        val portrait = ArrayList<CardOp>()
        portrait += CardOp.Paths(
            shapes = listOf(PlacedGlyph(robe, headCx, robeCy, robeW, robeH)),
            fillArgb = figure.accentArgb,
            strokeArgb = ink,
            strokeFraction = 0.008f,
        )
        portrait += CardOp.Dot(headCx, medallionCy, medallion / 2f, PARCHMENT_TOP)
        portrait += CardOp.Dot(headCx, medallionCy, medallion / 2f, GOLD, stroke = true, strokeFraction = 0.006f)
        portrait += CardOp.Paths(
            shapes = listOf(PlacedGlyph(suitGlyph, headCx, medallionCy, medallion * 0.6f, medallion * 0.6f)),
            fillArgb = ink,
        )
        // the prop is held across the body — over the robe, under the head — so blade, shaft and
        // guard all read instead of hiding behind the shoulder
        portrait += CardOp.Paths(
            shapes = listOf(PlacedGlyph(prop, propCx, propCy, propW, propH, figure.propAngleDeg)),
            fillArgb = ink,
            alpha = 0.95f,
        )
        val face = CardOp.Paths(
            shapes = listOf(PlacedGlyph(head, headCx, headCy, headW, headH)),
            fillArgb = PARCHMENT_TOP,
            strokeArgb = ink,
            strokeFraction = 0.007f,
        )
        if (figure.hasHair) {
            portrait += CardOp.Paths(
                shapes = listOf(PlacedGlyph(hair, headCx, headCy + headH * 0.02f, headW * 1.24f, headH * 1.14f)),
                fillArgb = ink,
                alpha = 0.92f,
            )
            portrait += face
        } else {
            portrait += face
            if (figure.hasBeard) {
                portrait += CardOp.Paths(
                    shapes = listOf(
                        PlacedGlyph(beard, headCx, headCy + headH * 0.34f, headW * 0.94f, headH * 0.62f),
                    ),
                    fillArgb = ink,
                    alpha = 0.92f,
                )
            }
        }
        portrait += CardOp.Paths(
            shapes = listOf(PlacedGlyph(dress, headCx, dressCy, dressW, dressH)),
            fillArgb = GOLD,
            strokeArgb = ink,
            strokeFraction = 0.007f,
        )
        portrait += CardOp.Dot(headCx, dressCy - dressH * 0.28f, dressW * 0.06f, ink)

        // ---- the two halves. The diagonal runs from the frame's bottom-left to its top-right; each
        // portrait is clipped to its own side, and each side is pulled back from the diagonal by the
        // same vertical gap, so a clean parchment band shows along the cut and the two portraits can
        // never overlap (the sampling test in `HokmCardDrawingTest` proves it).
        val gap = CardMetrics.COURT_SPLIT_GAP * fh
        val upperTriangle = listOf(
            GlyphCommand.MoveTo(left, top),
            GlyphCommand.LineTo(left + fw, top),
            GlyphCommand.LineTo(left, top + fh - gap),
            GlyphCommand.Close,
        )
        ops += CardOp.Clip(region = upperTriangle, ops = portrait)
        val lowerTriangle = upperTriangle.mapCodePoints { x, y -> Pair(2f * frameCx - x, 2f * frameCy - y) }
        ops += CardOp.Clip(region = lowerTriangle, ops = portrait.map { it.mirror() })
        return ops
    }

    /** The 180° mirror of this op about a point — the second half of a court card. */
    private fun CardOp.mirroredAbout(cx: Float, cy: Float): CardOp = when (this) {
        is CardOp.Rect -> copy(left = 2f * cx - left - width, top = 2f * cy - top - height)
        is CardOp.Paths -> copy(
            shapes = shapes.map { it.mirroredAbout(cx, cy) },
        )

        is CardOp.Line -> copy(x1 = 2f * cx - x2, y1 = 2f * cy - y2, x2 = 2f * cx - x1, y2 = 2f * cy - y1)
        is CardOp.Dot -> copy(cx = 2f * cx - cx, cy = 2f * cy - cy)
        // a text run is measured by the executor, so its mirror is a spin plus a mirrored pivot
        is CardOp.Text -> copy(
            rotationDeg = rotationDeg + 180f,
            rotationPivot = rotationPivot?.let { Pair(2f * cx - it.first, 2f * cy - it.second) },
        )
        is CardOp.Bezel -> this
        is CardOp.Clip -> copy(
            region = region.mapCodePoints { x, y -> Pair(2f * cx - x, 2f * cy - y) },
            ops = ops.map { it.mirroredAbout(cx, cy) },
        )
    }

    /** Mirror of a placed outline: centre reflected, spin flipped, and absolute command points mapped. */
    private fun PlacedGlyph.mirroredAbout(cx: Float, cy: Float): PlacedGlyph = copy(
        commands = commands.map { command ->
            when (command) {
                is GlyphCommand.MoveTo -> GlyphCommand.MoveTo(2f * cx - command.x, 2f * cy - command.y)
                is GlyphCommand.LineTo -> GlyphCommand.LineTo(2f * cx - command.x, 2f * cy - command.y)
                is GlyphCommand.QuadTo -> GlyphCommand.QuadTo(
                    2f * cx - command.cx, 2f * cy - command.cy,
                    2f * cx - command.x, 2f * cy - command.y,
                )

                is GlyphCommand.CurveTo -> GlyphCommand.CurveTo(
                    2f * cx - command.c1x, 2f * cy - command.c1y,
                    2f * cx - command.c2x, 2f * cy - command.c2y,
                    2f * cx - command.x, 2f * cy - command.y,
                )

                GlyphCommand.Close -> GlyphCommand.Close
            }
        },
        cx = 2f * cx - this.cx,
        cy = 2f * cy - this.cy,
        rotationDeg = rotationDeg + 180f,
    )
}

/**
 * Composition of one card back, built from its [CardBackSpec]: ivory paper, a hairline edge, the
 * patterned field (bright in the middle, so the medallion has something to sit on), the brass frame,
 * the centre medallion with the star motif and the accent bead.
 *
 * Note the deliberate asymmetry in the maths: a *margin* is a fraction of the card width, so its
 * y-coordinate goes through [hy]; a *span* like `1 - 2m` is already relative to each axis and must
 * not. Mixing the two is how the first version of this file gave the field only the top two thirds
 * of the card.
 */
object CardBackDrawing {

    fun of(spec: CardBackSpec): List<CardOp> {
        val ops = ArrayList<CardOp>()
        val m = spec.marginFraction
        val inset = m * 1.8f

        // paper + edge hairline
        ops += CardOp.Rect(
            left = 0f, top = 0f, width = 1f, height = 1f,
            radius = CardMetrics.CORNER_RADIUS_FRACTION,
            fillArgb = PAPER,
        )
        ops += CardOp.Rect(
            left = 0f, top = 0f, width = 1f, height = 1f,
            radius = CardMetrics.CORNER_RADIUS_FRACTION,
            strokeArgb = HokmTokens.Color.CARD_EDGE,
            strokeFraction = CardMetrics.BEZEL_EDGE_WIDTH_FRACTION * 0.6f,
        )
        // the field: a radial wash from the middle out to the edges
        ops += CardOp.Rect(
            left = m, top = hy(m),
            width = 1f - 2f * m, height = 1f - 2f * hy(m),
            radius = CardMetrics.CORNER_RADIUS_FRACTION * 0.8f,
            fill = GradientSpec(spec.fieldArgb, spec.fieldEdgeArgb, GradientKind.RADIAL),
        )

        // ---- the lattice: the back's motif, alternate cells outlined and filled. Its clip is a
        // rounded rectangle, so no cell can spill onto the ivory corner.
        val step = spec.patternStepFraction
        val stepY = hy(step)
        val motif = CardGlyphs.byId(spec.pattern.glyphId).commands
        val innerLeft = inset
        val innerRight = 1f - inset
        val innerTop = hy(inset)
        val innerBottom = 1f - hy(inset)
        val innerRadius = CardMetrics.CORNER_RADIUS_FRACTION * 0.7f
        val cells = ArrayList<CardOp>()
        var row = 0
        var y = innerTop - stepY
        while (y < innerBottom + stepY) {
            var col = 0
            var x = innerLeft - step
            while (x < innerRight + step) {
                val cellCx = x + step / 2f
                val cellCy = y + stepY / 2f
                val flipped = (row + col) % 2 == 1
                cells += if (flipped) {
                    CardOp.Paths(
                        shapes = listOf(PlacedGlyph(motif, cellCx, cellCy, step * 0.82f, stepY * 0.82f)),
                        fillArgb = spec.patternArgb,
                        alpha = spec.patternAlpha * 0.72f,
                    )
                } else {
                    CardOp.Paths(
                        shapes = listOf(PlacedGlyph(motif, cellCx, cellCy, step, stepY)),
                        strokeArgb = spec.patternArgb,
                        strokeFraction = 0.006f,
                        alpha = spec.patternAlpha,
                    )
                }
                col++
                x += step
            }
            row++
            y += stepY
        }
        ops += CardOp.Clip(
            region = roundedRect(
                left = innerLeft,
                top = innerTop,
                width = innerRight - innerLeft,
                height = innerBottom - innerTop,
                radius = innerRadius,
            ),
            ops = cells,
        )

        // ---- frame, medallion, motif, bead
        ops += CardOp.Rect(
            left = innerLeft, top = innerTop, width = innerRight - innerLeft, height = innerBottom - innerTop,
            radius = innerRadius,
            strokeArgb = spec.frameArgb,
            strokeFraction = 0.012f,
        )
        val medallion = spec.medallionFraction
        ops += CardOp.Dot(0.5f, 0.5f, medallion, spec.fieldEdgeArgb)
        ops += CardOp.Dot(0.5f, 0.5f, medallion, spec.frameArgb, stroke = true, strokeFraction = 0.012f)
        ops += CardOp.Paths(
            shapes = listOf(
                PlacedGlyph(CardGlyphs.STAR8.commands, 0.5f, 0.5f, spec.motifFraction * 2f, hy(spec.motifFraction * 2f)),
            ),
            fillArgb = spec.frameArgb,
        )
        ops += CardOp.Paths(
            shapes = listOf(
                PlacedGlyph(CardGlyphs.RHOMBUS.commands, 0.5f, 0.5f, spec.accentDotFraction * 2f, hy(spec.accentDotFraction * 2f)),
            ),
            fillArgb = spec.accentArgb,
        )
        return ops
    }
}

/**
 * A rounded rectangle as a closed command list in absolute card fractions — used as a clip outline.
 * [left], [width] and [radius] are width fractions; [top] and [height] are height fractions.
 */
fun roundedRect(left: Float, top: Float, width: Float, height: Float, radius: Float): List<GlyphCommand> {
    val r = radius
    val rY = hy(radius)
    val k = r * 0.5523f
    val kY = rY * 0.5523f
    val right = left + width
    val bottom = top + height
    return listOf(
        GlyphCommand.MoveTo(left + r, top),
        GlyphCommand.LineTo(right - r, top),
        GlyphCommand.CurveTo(right - r + k, top, right, top + kY, right, top + rY),
        GlyphCommand.LineTo(right, bottom - rY),
        GlyphCommand.CurveTo(right, bottom - rY + kY, right - r + k, bottom, right - r, bottom),
        GlyphCommand.LineTo(left + r, bottom),
        GlyphCommand.CurveTo(left + r - k, bottom, left, bottom - rY + kY, left, bottom - rY),
        GlyphCommand.LineTo(left, top + rY),
        GlyphCommand.CurveTo(left, top + rY - kY, left + r - k, top, left + r, top),
        GlyphCommand.Close,
    )
}
