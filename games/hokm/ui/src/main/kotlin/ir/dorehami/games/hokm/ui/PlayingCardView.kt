package ir.dorehami.games.hokm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Suit
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * GX-03 — the card is painted here, but *what* is painted comes from [CardFaceCatalog] (pure data,
 * machine-verified) and *how it composes* comes from [CardFaceDrawing] / [CardBackDrawing] (pure
 * vector ops, shared with the SVG review sheet in `tools/hokm`). This file is only the Compose
 * executor: it turns every [CardOp] into `drawPath` / `drawText` calls.
 *
 * Everything the player can change is read from [LocalHokmCardStyle], provided once at the Hokm
 * roots, so no call site has to pass flags around. Still no raster asset anywhere (decision D-10):
 * the court figures, the pips and the backs are geometry.
 */

/**
 * The one switch that carries the card presentation (deck style, jumbo index, chosen back) from the
 * settings screen down to every card on the table. Provided once per Hokm composition root — the
 * same pattern as `LocalHokmReducedMotion` — so no call site passes flags around.
 */
val LocalHokmCardStyle = staticCompositionLocalOf { HokmCardStyle.DEFAULT }

/** Palette of a real deck: ivory stock, deep inks and the GX-01 backs (values: [HokmTokens]). */
object CardColors {
    val ivory = Color(HokmTokens.Color.CARD_IVORY)
    val ivoryShade = Color(HokmTokens.Color.CARD_IVORY_SHADE)
    val edge = Color(HokmTokens.Color.CARD_EDGE)
    val red = Color(HokmTokens.Color.INK_RED)
    val black = Color(HokmTokens.Color.INK_BLACK)
    val blue = Color(HokmTokens.Color.INK_BLUE)
    val green = Color(HokmTokens.Color.INK_GREEN)
    val gold = Color(HokmTokens.Color.CARD_GOLD)
    val parchmentTop = Color(HokmTokens.Color.COURT_PARCHMENT_TOP)
    val parchmentBottom = Color(HokmTokens.Color.COURT_PARCHMENT_BOTTOM)

    // Kept for the emerald table accents and any caller still holding the old palette.
    val backA = Color(HokmArtDirection.FELT_SPOTLIGHT)
    val backB = Color(HokmArtDirection.FELT_EDGE)
    val backPattern = Color(HokmArtDirection.BRASS_LIGHT)

    /** Ink of [ink] on the card stock, or its readable variant when drawn on the dark felt. */
    fun ink(ink: CardInk, onDark: Boolean = false): Color =
        Color(if (onDark) CardInks.onDark(ink) else CardInks.argb(ink))

    /** Any token colour as a Compose colour. */
    fun of(argb: Long): Color = Color(argb)
}

/** Classic-deck ink of a suit; four-colour callers read [CardInks.of] with the current style. */
val Suit.inkColor: Color
    get() = if (this == Suit.HEARTS || this == Suit.DIAMONDS) CardColors.red else CardColors.black

/** Width:height of a poker card. */
const val CARD_ASPECT = CardMetrics.ASPECT

/**
 * A playing card drawn entirely with vectors — crisp at any size, no font surprises (♥ never turns
 * into an emoji), no asset licensing.
 */
@Composable
fun PlayingCardView(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    dimmed: Boolean = false,
    /** Side thickness shown when this card sits on top of a stack (0 = a lone card). */
    thickness: Dp = 0.dp,
) {
    val style = LocalHokmCardStyle.current
    val plan = remember(card, style) { CardFaceCatalog.plan(card, style.deck, style.jumbo) }
    val ops = remember(plan) { CardFaceDrawing.of(plan) }
    val height = width / CARD_ASPECT
    val shape = RoundedCornerShape(width * plan.bezel.cornerRadiusFraction)
    val measurer = rememberTextMeasurer()

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(CardColors.ivory)
            .border(1.dp, CardColors.edge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCardOps(ops, measurer)
            if (thickness > 0.dp) {
                drawSideThickness(max(thickness.toPx(), size.width * plan.bezel.thicknessFraction))
            }
            if (dimmed) drawRect(Color.Black.copy(alpha = 0.38f))
        }
    }
}

/** The back of a card, drawn from one of the original [HokmCardBacks] designs. */
@Composable
fun CardBackView(
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
    backId: String = LocalHokmCardStyle.current.back,
    thickness: Dp = 0.dp,
) {
    val spec = remember(backId) { HokmCardBacks.byId(backId) }
    val ops = remember(spec) { CardBackDrawing.of(spec) }
    val height = width / CARD_ASPECT
    val shape = RoundedCornerShape(width * CardMetrics.CORNER_RADIUS_FRACTION)
    val measurer = rememberTextMeasurer()
    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(CardColors.ivory)
            .border(1.dp, CardColors.edge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCardOps(ops, measurer)
            if (thickness > 0.dp) {
                drawSideThickness(max(thickness.toPx(), size.width * CardMetrics.THICKNESS_FRACTION))
            }
        }
    }
}

// ------------------------------------------------------------------------------------ executor

/** Paints a card (face or back) from the shared op list. */
private fun DrawScope.drawCardOps(ops: List<CardOp>, measurer: TextMeasurer) {
    val w = size.width
    val h = size.height
    ops.forEach { op ->
        when (op) {
            is CardOp.Clip -> {
                val region = absolutePath(op.region, w, h)
                clipPath(region) { drawCardOps(op.ops, measurer) }
            }

            is CardOp.Rect -> {
                val topLeft = Offset(op.left * w, op.top * h)
                val rectSize = Size(op.width * w, op.height * h)
                val radius = CornerRadius(op.radius * w)
                op.fill?.let { fill ->
                    drawRoundRect(
                        brush = brushOf(fill, topLeft, rectSize),
                        topLeft = topLeft,
                        size = rectSize,
                        cornerRadius = radius,
                        alpha = op.alpha,
                    )
                }
                op.fillArgb?.let { argb ->
                    drawRoundRect(
                        color = Color(argb),
                        topLeft = topLeft,
                        size = rectSize,
                        cornerRadius = radius,
                        alpha = op.alpha,
                    )
                }
                op.strokeArgb?.let { argb ->
                    drawRoundRect(
                        color = Color(argb),
                        topLeft = topLeft,
                        size = rectSize,
                        cornerRadius = radius,
                        alpha = op.alpha,
                        style = Stroke(width = max(1f, op.strokeFraction * w)),
                    )
                }
            }

            is CardOp.Paths -> {
                val paths = op.shapes.map { it.toPath(w, h) }
                op.fill?.let { fill ->
                    paths.forEach { drawPath(it, brushOf(fill, Offset.Zero, Size(w, h)), alpha = op.alpha) }
                }
                op.fillArgb?.let { argb ->
                    paths.forEach { drawPath(it, Color(argb), alpha = op.alpha) }
                }
                op.strokeArgb?.let { argb ->
                    val stroke = Stroke(width = max(1f, op.strokeFraction * w))
                    paths.forEach { drawPath(it, Color(argb), alpha = op.alpha, style = stroke) }
                }
            }

            is CardOp.Line -> drawLine(
                color = Color(op.argb),
                start = Offset(op.x1 * w, op.y1 * h),
                end = Offset(op.x2 * w, op.y2 * h),
                strokeWidth = max(1f, op.strokeFraction * w),
                alpha = op.alpha,
            )

            is CardOp.Dot -> {
                val center = Offset(op.cx * w, op.cy * h)
                val radius = op.radius * w
                if (op.stroke) {
                    drawCircle(
                        color = Color(op.argb),
                        radius = radius,
                        center = center,
                        alpha = op.alpha,
                        style = Stroke(width = max(1f, op.strokeFraction * w)),
                    )
                } else {
                    drawCircle(color = Color(op.argb), radius = radius, center = center, alpha = op.alpha)
                }
            }

            is CardOp.Text -> {
                val layout = measurer.measure(
                    op.text,
                    TextStyle(
                        color = Color(op.argb),
                        fontSize = (op.sizeFraction * w / density).sp,
                        fontWeight = if (op.bold) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = if (op.serif) FontFamily.Serif else FontFamily.SansSerif,
                    ),
                )
                val topLeft = Offset(op.left * w, op.top * h)
                if (op.rotationDeg == 0f) {
                    drawText(layout, topLeft = topLeft, alpha = op.alpha)
                } else {
                    val pivot = op.rotationPivot
                        ?.let { Offset(it.first * w, it.second * h) }
                        ?: (topLeft + Offset(layout.size.width / 2f, layout.size.height / 2f))
                    rotate(op.rotationDeg, pivot = pivot) {
                        drawText(layout, topLeft = topLeft, alpha = op.alpha)
                    }
                }
            }

            is CardOp.Bezel -> drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(op.lightArgb).copy(alpha = op.lightAlpha),
                        Color.Transparent,
                        Color(op.shadeArgb).copy(alpha = op.shadeAlpha),
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h),
                ),
                topLeft = Offset(op.edgeWidth * w / 2f, op.edgeWidth * h / 2f),
                size = Size(w - op.edgeWidth * w, h - op.edgeWidth * h),
                cornerRadius = CornerRadius(op.cornerRadius * w),
                style = Stroke(width = max(1f, op.edgeWidth * w)),
            )
        }
    }
}

private fun brushOf(spec: GradientSpec, topLeft: Offset, size: Size): Brush = when (spec.kind) {
    GradientKind.VERTICAL -> Brush.verticalGradient(
        colors = listOf(Color(spec.from), Color(spec.to)),
        startY = topLeft.y,
        endY = topLeft.y + size.height,
    )

    GradientKind.RADIAL -> Brush.radialGradient(
        colors = listOf(Color(spec.from), Color(spec.to)),
        center = Offset(topLeft.x + size.width / 2f, topLeft.y + size.height / 2f),
        radius = max(size.width, size.height) * 0.62f,
    )
}

/**
 * The outline of a [CardOp.Clip] in pixels. A clip region is written in the same card fractions as
 * every other geometry in the deck (0..1 across and down), so mapping it is only a scale — no
 * glyph box and no rotation, unlike [glyphPath].
 */
private fun absolutePath(commands: List<GlyphCommand>, widthPx: Float, heightPx: Float): Path =
    Path().apply {
        for (command in commands) {
            when (command) {
                is GlyphCommand.MoveTo -> moveTo(command.x * widthPx, command.y * heightPx)
                is GlyphCommand.LineTo -> lineTo(command.x * widthPx, command.y * heightPx)
                is GlyphCommand.QuadTo -> quadraticTo(
                    command.cx * widthPx,
                    command.cy * heightPx,
                    command.x * widthPx,
                    command.y * heightPx,
                )

                is GlyphCommand.CurveTo -> cubicTo(
                    command.c1x * widthPx,
                    command.c1y * heightPx,
                    command.c2x * widthPx,
                    command.c2y * heightPx,
                    command.x * widthPx,
                    command.y * heightPx,
                )

                GlyphCommand.Close -> close()
            }
        }
    }

/**
 * Maps a unit-space glyph into canvas space: the unit outline is scaled into a `widthPx × heightPx`
 * box, rotated about its own centre and placed at `(centerX, centerY)`.
 */
private fun glyphPath(
    commands: List<GlyphCommand>,
    centerX: Float,
    centerY: Float,
    widthPx: Float,
    heightPx: Float,
    rotationDeg: Float,
): Path = Path().apply {
    val radians = (rotationDeg * Math.PI / 180.0).toFloat()
    val cosA = cos(radians)
    val sinA = sin(radians)

    fun point(x: Float, y: Float): Offset {
        val lx = (x - 0.5f) * widthPx
        val ly = (y - 0.5f) * heightPx
        val rx = if (rotationDeg == 0f) lx else lx * cosA - ly * sinA
        val ry = if (rotationDeg == 0f) ly else lx * sinA + ly * cosA
        return Offset(centerX + rx, centerY + ry)
    }

    for (command in commands) {
        when (command) {
            is GlyphCommand.MoveTo -> point(command.x, command.y).let { moveTo(it.x, it.y) }
            is GlyphCommand.LineTo -> point(command.x, command.y).let { lineTo(it.x, it.y) }
            is GlyphCommand.QuadTo -> {
                val c = point(command.cx, command.cy)
                val p = point(command.x, command.y)
                quadraticTo(c.x, c.y, p.x, p.y)
            }

            is GlyphCommand.CurveTo -> {
                val c1 = point(command.c1x, command.c1y)
                val c2 = point(command.c2x, command.c2y)
                val p = point(command.x, command.y)
                cubicTo(c1.x, c1.y, c2.x, c2.y, p.x, p.y)
            }

            GlyphCommand.Close -> close()
        }
    }
}

/**
 * The same mapping for a [PlacedGlyph]: the op stores card fractions, so the SVG sheet and this
 * painter can share one geometry definition to the last decimal.
 */
fun PlacedGlyph.toPath(cardWidth: Float, cardHeight: Float): Path = glyphPath(
    commands = commands,
    centerX = cx * cardWidth,
    centerY = cy * cardHeight,
    widthPx = width * cardWidth,
    heightPx = height * cardWidth,
    rotationDeg = rotationDeg,
)

/** The paper edge you see when a card lies on a stack: a sliver on the right and the bottom. */
private fun DrawScope.drawSideThickness(px: Float) {
    if (px <= 0f) return
    drawRect(
        color = CardColors.edge,
        topLeft = Offset(size.width - px, 0f),
        size = Size(px, size.height),
    )
    drawRect(
        color = CardColors.edge,
        topLeft = Offset(0f, size.height - px),
        size = Size(size.width, px),
    )
}

// ----------------------------------------------------------------------------- vector shapes

/** Draws [suit] centred on [center] with the given [size] in pixels. */
fun DrawScope.drawSuit(suit: Suit, center: Offset, size: Float, color: Color, ornate: Boolean = false) {
    val path = glyphPath(CardGlyphs.suit(suit).commands, center.x, center.y, size, size, 0f)
    if (ornate) drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width = size * 0.08f))
    drawPath(path, color)
}

/** Unit-space path for a suit scaled to `s × s` (used by previews that need a raw path). */
fun suitPath(suit: Suit, s: Float): Path =
    glyphPath(CardGlyphs.suit(suit).commands, s / 2f, s / 2f, s, s, 0f)
