package ir.dorehami.games.hokm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.dorehami.games.hokm.Suit

/**
 * Every menu and button icon of the Hokm module, drawn with the Compose canvas.
 *
 * The bundled Vazirmatn face has no dingbat/suit glyphs (`⌁ ◎ ✦ ♛ ✓ ▶ ♠ ♥ ♦ ♣` are all missing),
 * and Android's fallback chain does not cover all of them either — the missing ones showed up as
 * empty boxes on the "آفلاین"/"آنلاین" cards. Drawing the shapes ourselves removes the dependency
 * on any font coverage and keeps the icons crisp at every size.
 */
enum class HokmIcon {
    Deck,
    Globe,
    Crown,
    Star,
    Users,
    Target,
    Check,
    Play,
    Back,
    Settings,
    Shop,
    Palette,
    Bolt,
    Trash,
    Trophy,
    Sparkle,
    Clock,
    Lock,
}

@Composable
fun HokmIconView(
    icon: HokmIcon,
    color: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.11f)
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        when (icon) {
            HokmIcon.Deck -> {
                // Two offset cards: the module's own mark.
                drawRoundRect(
                    color = color.copy(alpha = 0.55f),
                    topLeft = p(0.10f, 0.22f),
                    size = Size(w * 0.52f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawRoundRect(
                    color = color,
                    topLeft = p(0.34f, 0.14f),
                    size = Size(w * 0.52f, h * 0.62f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawCircle(color, radius = w * 0.055f, center = p(0.60f, 0.45f))
            }

            HokmIcon.Globe -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawOvalLine(color, p(0.5f, 0.10f), p(0.5f, 0.90f), w * 0.40f, stroke)
                drawLine(color, p(0.10f, 0.5f), p(0.90f, 0.5f), strokeWidth = stroke.width)
            }

            HokmIcon.Crown -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.76f)
                    lineTo(w * 0.20f, h * 0.30f)
                    lineTo(w * 0.36f, h * 0.52f)
                    lineTo(w * 0.50f, h * 0.22f)
                    lineTo(w * 0.64f, h * 0.52f)
                    lineTo(w * 0.80f, h * 0.30f)
                    lineTo(w * 0.88f, h * 0.76f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawLine(color, p(0.12f, 0.88f), p(0.88f, 0.88f), strokeWidth = w * 0.10f)
            }

            HokmIcon.Star -> drawPath(starPath(w, h, 0.46f, 0.20f), color)

            HokmIcon.Sparkle -> {
                drawPath(starPath(w, h, 0.40f, 0.16f), color, style = stroke)
                drawCircle(color, radius = w * 0.05f, center = p(0.82f, 0.20f))
            }

            HokmIcon.Users -> {
                drawCircle(color, radius = w * 0.15f, center = p(0.36f, 0.32f), style = stroke)
                drawCircle(color.copy(alpha = 0.65f), radius = w * 0.12f, center = p(0.70f, 0.38f), style = stroke)
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = p(0.12f, 0.44f),
                    size = Size(w * 0.48f, h * 0.42f),
                    style = stroke,
                )
                drawArc(
                    color = color.copy(alpha = 0.65f),
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = p(0.52f, 0.50f),
                    size = Size(w * 0.36f, h * 0.34f),
                    style = stroke,
                )
            }

            HokmIcon.Target -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawCircle(color.copy(alpha = 0.6f), radius = w * 0.24f, center = p(0.5f, 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.08f, center = p(0.5f, 0.5f))
            }

            HokmIcon.Check -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.52f)
                    lineTo(w * 0.42f, h * 0.76f)
                    lineTo(w * 0.84f, h * 0.26f)
                }
                drawPath(path, color, style = Stroke(width = w * 0.14f))
            }

            HokmIcon.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.30f, h * 0.20f)
                    lineTo(w * 0.82f, h * 0.50f)
                    lineTo(w * 0.30f, h * 0.80f)
                    close()
                }
                drawPath(path, color)
            }

            HokmIcon.Back -> {
                val path = Path().apply {
                    moveTo(w * 0.62f, h * 0.18f)
                    lineTo(w * 0.30f, h * 0.50f)
                    lineTo(w * 0.62f, h * 0.82f)
                }
                drawPath(path, color, style = Stroke(width = w * 0.14f))
            }

            HokmIcon.Settings -> {
                drawCircle(color, radius = w * 0.20f, center = p(0.5f, 0.5f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    val cx = 0.5f + (Math.cos(a) * 0.36f).toFloat()
                    val cy = 0.5f + (Math.sin(a) * 0.36f).toFloat()
                    drawCircle(color, radius = w * 0.055f, center = p(cx, cy))
                }
            }

            HokmIcon.Shop -> {
                // A little bag: body + handle.
                drawRoundRect(
                    color = color,
                    topLeft = p(0.16f, 0.38f),
                    size = Size(w * 0.68f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.10f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(0.34f, 0.16f),
                    size = Size(w * 0.32f, h * 0.40f),
                    style = stroke,
                )
            }

            HokmIcon.Palette -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.07f, center = p(0.38f, 0.38f))
                drawCircle(color, radius = w * 0.07f, center = p(0.62f, 0.36f))
                drawCircle(color, radius = w * 0.07f, center = p(0.66f, 0.62f))
            }

            HokmIcon.Bolt -> {
                val path = Path().apply {
                    moveTo(w * 0.56f, h * 0.10f)
                    lineTo(w * 0.28f, h * 0.54f)
                    lineTo(w * 0.48f, h * 0.54f)
                    lineTo(w * 0.42f, h * 0.90f)
                    lineTo(w * 0.74f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }
                drawPath(path, color)
            }

            HokmIcon.Trash -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(0.26f, 0.30f),
                    size = Size(w * 0.48f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = stroke,
                )
                drawLine(color, p(0.18f, 0.26f), p(0.82f, 0.26f), strokeWidth = w * 0.10f)
                drawLine(color, p(0.40f, 0.16f), p(0.60f, 0.16f), strokeWidth = w * 0.08f)
            }

            HokmIcon.Trophy -> {
                val path = Path().apply {
                    moveTo(w * 0.30f, h * 0.16f)
                    lineTo(w * 0.70f, h * 0.16f)
                    lineTo(w * 0.66f, h * 0.54f)
                    lineTo(w * 0.34f, h * 0.54f)
                    close()
                }
                drawPath(path, color, style = stroke)
                drawLine(color, p(0.50f, 0.54f), p(0.50f, 0.78f), strokeWidth = w * 0.10f)
                drawLine(color, p(0.30f, 0.86f), p(0.70f, 0.86f), strokeWidth = w * 0.10f)
            }

            HokmIcon.Lock -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(0.22f, 0.44f),
                    size = Size(w * 0.56f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(0.32f, 0.20f),
                    size = Size(w * 0.36f, h * 0.40f),
                    style = stroke,
                )
            }

            HokmIcon.Clock -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawLine(color, p(0.50f, 0.50f), p(0.50f, 0.28f), strokeWidth = w * 0.10f)
                drawLine(color, p(0.50f, 0.50f), p(0.66f, 0.58f), strokeWidth = w * 0.10f)
            }
        }
    }
}

private fun starPath(w: Float, h: Float, outer: Float, inner: Float): Path = Path().apply {
    val cx = 0.5f
    val cy = 0.5f
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians((-90 + i * 36).toDouble())
        val x = (cx + (Math.cos(a) * r).toFloat()) * w
        val y = (cy + (Math.sin(a) * r).toFloat()) * h
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Two vertical arcs make a globe meridian without needing text glyphs. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOvalLine(
    color: Color,
    topLeft: Offset,
    bottomRight: Offset,
    radiusX: Float,
    stroke: Stroke,
) {
    drawArc(
        color = color,
        startAngle = 90f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(topLeft.x - radiusX / 2f, topLeft.y),
        size = Size(radiusX, bottomRight.y - topLeft.y),
        style = stroke,
    )
    drawArc(
        color = color,
        startAngle = 270f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(topLeft.x - radiusX / 2f, topLeft.y),
        size = Size(radiusX, bottomRight.y - topLeft.y),
        style = stroke,
    )
}

/**
 * Suit pip drawn as geometry: Vazirmatn ships no `♠♥♦♣`, so the hokm chooser used to show blank
 * keys. The shapes below match the printed pips closely enough at chip size.
 */
@Composable
fun HokmSuitPip(
    suit: Suit,
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        when (suit) {
            Suit.SPADES -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.08f)
                    cubicTo(w * 0.92f, h * 0.44f, w * 0.86f, h * 0.72f, w * 0.62f, h * 0.72f)
                    cubicTo(w * 0.56f, h * 0.72f, w * 0.52f, h * 0.70f, w * 0.50f, h * 0.66f)
                    cubicTo(w * 0.48f, h * 0.70f, w * 0.44f, h * 0.72f, w * 0.38f, h * 0.72f)
                    cubicTo(w * 0.14f, h * 0.72f, w * 0.08f, h * 0.44f, w * 0.50f, h * 0.08f)
                    close()
                }
                drawPath(path, color)
                drawPath(Path().apply {
                    moveTo(w * 0.50f, h * 0.62f)
                    lineTo(w * 0.30f, h * 0.94f)
                    lineTo(w * 0.70f, h * 0.94f)
                    close()
                }, color)
            }

            Suit.HEARTS -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.92f)
                    cubicTo(w * 0.02f, h * 0.58f, w * 0.10f, h * 0.10f, w * 0.50f, h * 0.34f)
                    cubicTo(w * 0.90f, h * 0.10f, w * 0.98f, h * 0.58f, w * 0.50f, h * 0.92f)
                    close()
                }
                drawPath(path, color)
            }

            Suit.DIAMONDS -> {
                val path = Path().apply {
                    moveTo(w * 0.50f, h * 0.06f)
                    lineTo(w * 0.90f, h * 0.50f)
                    lineTo(w * 0.50f, h * 0.94f)
                    lineTo(w * 0.10f, h * 0.50f)
                    close()
                }
                drawPath(path, color)
            }

            Suit.CLUBS -> {
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.50f, h * 0.28f))
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.26f, h * 0.58f))
                drawCircle(color, radius = w * 0.22f, center = Offset(w * 0.74f, h * 0.58f))
                drawPath(Path().apply {
                    moveTo(w * 0.50f, h * 0.52f)
                    lineTo(w * 0.30f, h * 0.94f)
                    lineTo(w * 0.70f, h * 0.94f)
                    close()
                }, color)
            }
        }
    }
}
