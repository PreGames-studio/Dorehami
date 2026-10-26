package ir.dorehami.games.hokm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit

/** Palette of a real deck: ivory stock, deep red and near-black ink. */
object CardColors {
    val ivory = Color(0xFFFDFBF4)
    val ivoryShade = Color(0xFFEFEBE0)
    val edge = Color(0xFFCFC9B8)
    val red = Color(0xFFB3202B)
    val black = Color(0xFF1C1C1E)
    val gold = Color(0xFFD4AF37)
    val backA = Color(0xFF7A1B22)
    val backB = Color(0xFF3F0B10)
    val backPattern = Color(0xFFF1D9A4)
}

val Suit.inkColor: Color
    get() = if (this == Suit.HEARTS || this == Suit.DIAMONDS) CardColors.red else CardColors.black

/** Width:height of a poker card. */
const val CARD_ASPECT = 0.7f

/**
 * A playing card drawn entirely with vectors — crisp at any size, no font surprises (♥ never
 * turns into an emoji), no asset licensing.
 */
@Composable
fun PlayingCardView(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 6.dp,
    dimmed: Boolean = false,
    sheen: State<HokmSheen> = NeutralSheenState,
) {
    val height = width / CARD_ASPECT
    val corner = width * 0.09f
    val shape = RoundedCornerShape(corner)
    val measurer = rememberTextMeasurer()
    val ink = card.suit.inkColor
    val rankLabel = when (card.rank) {
        Rank.TEN -> "10"
        else -> card.rank.code.toString()
    }

    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(CardColors.ivory, CardColors.ivoryShade)))
            .border(1.dp, CardColors.edge, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val indexSize = w * 0.19f
            val style = TextStyle(
                color = ink,
                fontSize = (indexSize / density).sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
            )
            val layout = measurer.measure(rankLabel, style)
            val pad = w * 0.06f

            // --- corner indices (top-left and rotated bottom-right)
            fun drawIndex() {
                drawText(layout, topLeft = Offset(pad, pad * 0.6f))
                val s = indexSize * 0.78f
                drawSuit(card.suit, Offset(pad + layout.size.width / 2f, pad * 0.6f + layout.size.height + s * 0.55f), s, ink)
            }
            drawIndex()
            rotate(180f, pivot = Offset(w / 2f, h / 2f)) { drawIndex() }

            // --- centre
            val cx = w / 2f
            when (card.rank) {
                Rank.ACE -> drawSuit(card.suit, Offset(cx, h / 2f), w * 0.46f, ink, ornate = true)
                Rank.JACK, Rank.QUEEN, Rank.KING -> drawCourt(card, measurer, ink)
                else -> drawPips(card, ink)
            }

            if (dimmed) drawRect(Color.Black.copy(alpha = 0.38f))

            // Tilt-reactive specular band: read in the draw phase (no recomposition per sensor
            // event), capped at 12% alpha, and never drawn on a dimmed/illegal card.
            val reflection = sheen.value
            val alpha = HokmReflection.specularAlpha(reflection, dimmed)
            if (alpha > 0f) {
                val bandWidth = w * 0.44f
                val left = (HokmReflection.bandCenter(reflection) * w - bandWidth / 2f)
                    .coerceIn(0f, w - bandWidth)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = alpha),
                            Color(0xFFFFE0B2).copy(alpha = alpha * 0.6f),
                            Color.Transparent,
                        ),
                        startX = left,
                        endX = left + bandWidth,
                    ),
                    topLeft = Offset(left, 0f),
                    size = Size(bandWidth, h),
                )
            }
        }
    }
}

/** The back of a card: deep red stock with a Persian "girih" lattice and a thin gold frame. */
@Composable
fun CardBackView(
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
) {
    // Photographic back (crimson girih on gold); the painted lattice below stays as a fallback.
    val height = width / CARD_ASPECT
    val corner = width * 0.09f
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(CardColors.ivory)
            .border(width * 0.015f, Color(0x33000000), shape),
    ) {
        Image(
            painter = painterResource(R.drawable.hokm_card_back),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Vector-drawn card back (used nowhere by default; kept for low-memory devices / tests). */
@Composable
fun PaintedCardBackView(
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
) {
    val height = width / CARD_ASPECT
    val corner = width * 0.09f
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(width, height)
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            .background(CardColors.ivory),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = w * 0.07f
            // red field
            drawRoundRect(
                brush = Brush.radialGradient(listOf(CardColors.backA, CardColors.backB), center = Offset(w / 2f, h / 2f), radius = h * 0.75f),
                topLeft = Offset(m, m),
                size = Size(w - 2 * m, h - 2 * m),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx() * 0.6f),
            )
            // lattice: diagonal grid of small rhombi
            val step = w * 0.11f
            val stroke = Stroke(width = w * 0.006f)
            val inner = androidx.compose.ui.geometry.Rect(m * 1.6f, m * 1.6f, w - m * 1.6f, h - m * 1.6f)
            clipRect(left = inner.left, top = inner.top, right = inner.right, bottom = inner.bottom) {
                var y = inner.top - step
                while (y < inner.bottom + step) {
                    var x = inner.left - step
                    while (x < inner.right + step) {
                        val p = Path().apply {
                            moveTo(x + step / 2, y)
                            lineTo(x + step, y + step / 2)
                            lineTo(x + step / 2, y + step)
                            lineTo(x, y + step / 2)
                            close()
                        }
                        drawPath(p, CardColors.backPattern.copy(alpha = 0.55f), style = stroke)
                        drawCircle(CardColors.backPattern.copy(alpha = 0.7f), radius = w * 0.009f, center = Offset(x + step / 2, y + step / 2))
                        x += step
                    }
                    y += step
                }
            }
            // gold frame + central eight-point star
            drawRoundRect(
                color = CardColors.gold,
                topLeft = Offset(m * 1.6f, m * 1.6f),
                size = Size(w - 3.2f * m, h - 3.2f * m),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner.toPx() * 0.4f),
                style = Stroke(width = w * 0.012f),
            )
            val c = Offset(w / 2f, h / 2f)
            drawCircle(CardColors.backB, radius = w * 0.2f, center = c)
            drawCircle(CardColors.gold, radius = w * 0.2f, center = c, style = Stroke(w * 0.012f))
            drawStar(c, w * 0.15f, CardColors.gold)
        }
    }
}

// ----------------------------------------------------------------------------- vector shapes

/** Eight-pointed star (two squares) — the classic Persian motif. */
private fun DrawScope.drawStar(center: Offset, radius: Float, color: Color) {
    repeat(2) { i ->
        rotate(45f * i, pivot = center) {
            drawRect(
                color = color,
                topLeft = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f),
                size = Size(radius * 1.4f, radius * 1.4f),
                style = Stroke(width = radius * 0.1f),
            )
        }
    }
    drawCircle(color, radius * 0.18f, center)
}

/** Draws [suit] centred on [center] with the given [size] (height of the glyph). */
fun DrawScope.drawSuit(suit: Suit, center: Offset, size: Float, color: Color, ornate: Boolean = false) {
    val path = suitPath(suit, size)
    translate(center.x - size / 2f, center.y - size / 2f) {
        if (ornate) {
            // soft halo for aces
            drawPath(path, color.copy(alpha = 0.12f), style = Stroke(width = size * 0.08f))
        }
        drawPath(path, color)
    }
}

/** Unit-space path for each suit, scaled to [s] × [s]. */
fun suitPath(suit: Suit, s: Float): Path = Path().apply {
    when (suit) {
        Suit.HEARTS -> {
            moveTo(0.5f * s, 0.95f * s)
            cubicTo(0.1f * s, 0.62f * s, 0.0f, 0.42f * s, 0.05f * s, 0.26f * s)
            cubicTo(0.1f * s, 0.08f * s, 0.36f * s, 0.02f * s, 0.5f * s, 0.26f * s)
            cubicTo(0.64f * s, 0.02f * s, 0.9f * s, 0.08f * s, 0.95f * s, 0.26f * s)
            cubicTo(1.0f * s, 0.42f * s, 0.9f * s, 0.62f * s, 0.5f * s, 0.95f * s)
            close()
        }

        Suit.DIAMONDS -> {
            moveTo(0.5f * s, 0.02f * s)
            quadraticTo(0.72f * s, 0.32f * s, 0.95f * s, 0.5f * s)
            quadraticTo(0.72f * s, 0.68f * s, 0.5f * s, 0.98f * s)
            quadraticTo(0.28f * s, 0.68f * s, 0.05f * s, 0.5f * s)
            quadraticTo(0.28f * s, 0.32f * s, 0.5f * s, 0.02f * s)
            close()
        }

        Suit.SPADES -> {
            // inverted heart
            moveTo(0.5f * s, 0.02f * s)
            cubicTo(0.86f * s, 0.34f * s, 1.0f * s, 0.5f * s, 0.95f * s, 0.66f * s)
            cubicTo(0.9f * s, 0.84f * s, 0.64f * s, 0.9f * s, 0.52f * s, 0.68f * s)
            lineTo(0.48f * s, 0.68f * s)
            cubicTo(0.36f * s, 0.9f * s, 0.1f * s, 0.84f * s, 0.05f * s, 0.66f * s)
            cubicTo(0.0f, 0.5f * s, 0.14f * s, 0.34f * s, 0.5f * s, 0.02f * s)
            close()
            // stem
            moveTo(0.5f * s, 0.62f * s)
            cubicTo(0.52f * s, 0.8f * s, 0.6f * s, 0.9f * s, 0.7f * s, 0.98f * s)
            lineTo(0.3f * s, 0.98f * s)
            cubicTo(0.4f * s, 0.9f * s, 0.48f * s, 0.8f * s, 0.5f * s, 0.62f * s)
            close()
        }

        Suit.CLUBS -> {
            val r = 0.22f * s
            addOval(androidx.compose.ui.geometry.Rect(Offset(0.5f * s, 0.25f * s), r))
            addOval(androidx.compose.ui.geometry.Rect(Offset(0.26f * s, 0.58f * s), r))
            addOval(androidx.compose.ui.geometry.Rect(Offset(0.74f * s, 0.58f * s), r))
            // joint + stem
            moveTo(0.5f * s, 0.5f * s)
            cubicTo(0.52f * s, 0.78f * s, 0.6f * s, 0.9f * s, 0.7f * s, 0.98f * s)
            lineTo(0.3f * s, 0.98f * s)
            cubicTo(0.4f * s, 0.9f * s, 0.48f * s, 0.78f * s, 0.5f * s, 0.5f * s)
            close()
        }
    }
}

/** Standard pip positions for 2–10 in a 3-column grid (x in 0..1, y in 0..1, flipped = drawn upside down). */
private data class Pip(val x: Float, val y: Float, val flipped: Boolean = false)

private val PIP_LAYOUTS: Map<Rank, List<Pip>> = run {
    val l = 0.26f
    val c = 0.5f
    val r = 0.74f
    val rows = listOf(0.17f, 0.335f, 0.5f, 0.665f, 0.83f)
    val top = rows[0]
    val q1 = rows[1]
    val mid = rows[2]
    val q3 = rows[3]
    val bot = rows[4]
    mapOf(
        Rank.TWO to listOf(Pip(c, top), Pip(c, bot, true)),
        Rank.THREE to listOf(Pip(c, top), Pip(c, mid), Pip(c, bot, true)),
        Rank.FOUR to listOf(Pip(l, top), Pip(r, top), Pip(l, bot, true), Pip(r, bot, true)),
        Rank.FIVE to listOf(Pip(l, top), Pip(r, top), Pip(c, mid), Pip(l, bot, true), Pip(r, bot, true)),
        Rank.SIX to listOf(Pip(l, top), Pip(r, top), Pip(l, mid), Pip(r, mid), Pip(l, bot, true), Pip(r, bot, true)),
        Rank.SEVEN to listOf(
            Pip(l, top), Pip(r, top), Pip(c, (top + mid) / 2f), Pip(l, mid), Pip(r, mid), Pip(l, bot, true), Pip(r, bot, true),
        ),
        Rank.EIGHT to listOf(
            Pip(l, top), Pip(r, top), Pip(c, (top + mid) / 2f), Pip(l, mid), Pip(r, mid),
            Pip(c, (mid + bot) / 2f, true), Pip(l, bot, true), Pip(r, bot, true),
        ),
        Rank.NINE to listOf(
            Pip(l, top), Pip(r, top), Pip(l, q1 + 0.02f), Pip(r, q1 + 0.02f), Pip(c, mid),
            Pip(l, q3 - 0.02f, true), Pip(r, q3 - 0.02f, true), Pip(l, bot, true), Pip(r, bot, true),
        ),
        Rank.TEN to listOf(
            Pip(l, top), Pip(r, top), Pip(c, (top + q1) / 2f + 0.02f), Pip(l, q1 + 0.02f), Pip(r, q1 + 0.02f),
            Pip(l, q3 - 0.02f, true), Pip(r, q3 - 0.02f, true), Pip(c, (q3 + bot) / 2f - 0.02f, true),
            Pip(l, bot, true), Pip(r, bot, true),
        ),
    )
}

private fun DrawScope.drawPips(card: Card, ink: Color) {
    val w = size.width
    val h = size.height
    val pips = PIP_LAYOUTS[card.rank] ?: return
    val pipSize = w * 0.2f
    // inner field leaves room for the indices
    val left = w * 0.2f
    val fieldW = w * 0.6f
    val top = h * 0.12f
    val fieldH = h * 0.76f
    for (p in pips) {
        val center = Offset(left + fieldW * p.x, top + fieldH * p.y)
        if (p.flipped) {
            rotate(180f, pivot = center) { drawSuit(card.suit, center, pipSize, ink) }
        } else {
            drawSuit(card.suit, center, pipSize, ink)
        }
    }
}

/** Court cards: a framed inset with a crowned monogram — readable at a glance, looks like a real deck. */
private fun DrawScope.drawCourt(card: Card, measurer: androidx.compose.ui.text.TextMeasurer, ink: Color) {
    val w = size.width
    val h = size.height
    val left = w * 0.19f
    val top = h * 0.16f
    val fw = w * 0.62f
    val fh = h * 0.68f
    val frame = androidx.compose.ui.geometry.Rect(left, top, left + fw, top + fh)

    // inset with a subtle golden gradient
    drawRect(
        brush = Brush.verticalGradient(listOf(Color(0xFFF7EFD9), Color(0xFFEADFBF))),
        topLeft = frame.topLeft,
        size = frame.size,
    )
    drawRect(ink, topLeft = frame.topLeft, size = frame.size, style = Stroke(w * 0.012f))
    drawRect(CardColors.gold, topLeft = Offset(frame.left + w * 0.02f, frame.top + w * 0.02f), size = Size(fw - w * 0.04f, fh - w * 0.04f), style = Stroke(w * 0.008f))

    // diagonal split like a real double-headed court card
    drawLine(ink.copy(alpha = 0.35f), Offset(frame.left, frame.bottom), Offset(frame.right, frame.top), strokeWidth = w * 0.006f)

    val letter = card.rank.code.toString()
    val style = TextStyle(
        color = ink,
        fontSize = (w * 0.3f / density).sp,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Serif,
    )
    val layout = measurer.measure(letter, style)

    fun half(center: Offset) {
        // crown
        drawCrown(Offset(center.x, center.y - w * 0.2f), w * 0.2f, CardColors.gold, ink)
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f + w * 0.02f))
        drawSuit(card.suit, Offset(center.x + w * 0.17f, center.y + w * 0.1f), w * 0.12f, ink)
    }

    val c1 = Offset(frame.left + fw * 0.36f, frame.top + fh * 0.3f)
    val c2 = Offset(frame.left + fw * 0.64f, frame.top + fh * 0.7f)
    half(c1)
    rotate(180f, pivot = c2) { half(c2) }
}

private fun DrawScope.drawCrown(center: Offset, width: Float, fill: Color, ink: Color) {
    val hgt = width * 0.55f
    val p = Path().apply {
        moveTo(center.x - width / 2f, center.y + hgt / 2f)
        lineTo(center.x - width / 2f, center.y - hgt * 0.1f)
        lineTo(center.x - width * 0.25f, center.y + hgt * 0.1f)
        lineTo(center.x, center.y - hgt / 2f)
        lineTo(center.x + width * 0.25f, center.y + hgt * 0.1f)
        lineTo(center.x + width / 2f, center.y - hgt * 0.1f)
        lineTo(center.x + width / 2f, center.y + hgt / 2f)
        close()
    }
    drawPath(p, fill)
    drawPath(p, ink, style = Stroke(width * 0.04f))
    drawCircle(ink, width * 0.05f, Offset(center.x, center.y - hgt / 2f))
}
