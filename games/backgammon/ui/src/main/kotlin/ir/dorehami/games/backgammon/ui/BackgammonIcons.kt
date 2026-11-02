package ir.dorehami.games.backgammon.ui

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

/**
 * Icons of the backgammon module, drawn with the canvas.
 *
 * The module text is Vazirmatn, which has no dingbat glyphs (`⌁ ☁ ♛ ♙ ＋ ↗ ✦ ⌄ ⌃ ‹`), and Android's
 * fallback chain does not cover all of them — that is how menu rows ended up with empty boxes. Every
 * icon the module needs is a vector shape here, so it renders identically on every device.
 */
enum class BackgammonIcon {
    Offline,
    Online,
    Bot,
    TwoPlayers,
    Bluetooth,
    Plus,
    Enter,
    Sparkle,
    Settings,
    Shop,
    Back,
    ChevronDown,
    ChevronUp,
    Check,
    Dice,
    Palette,
    Lock,
    Trophy,
    Trash,
}

@Composable
fun BackgammonIconView(
    icon: BackgammonIcon,
    color: Color,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = Stroke(width = w * 0.12f)
        fun p(x: Float, y: Float) = Offset(x * w, y * h)
        when (icon) {
            BackgammonIcon.Offline -> {
                // Lightning: instant local play.
                drawPath(Path().apply {
                    moveTo(w * 0.56f, h * 0.08f)
                    lineTo(w * 0.26f, h * 0.55f)
                    lineTo(w * 0.47f, h * 0.55f)
                    lineTo(w * 0.42f, h * 0.92f)
                    lineTo(w * 0.75f, h * 0.44f)
                    lineTo(w * 0.52f, h * 0.44f)
                    close()
                }, color)
            }

            BackgammonIcon.Online -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawLine(color, p(0.10f, 0.5f), p(0.90f, 0.5f), strokeWidth = stroke.width)
                drawArc(
                    color = color,
                    startAngle = 90f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(0.30f, 0.10f),
                    size = Size(w * 0.40f, h * 0.80f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 270f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = p(0.30f, 0.10f),
                    size = Size(w * 0.40f, h * 0.80f),
                    style = stroke,
                )
            }

            BackgammonIcon.Bot -> {
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

            BackgammonIcon.TwoPlayers -> {
                drawCircle(color, radius = w * 0.15f, center = p(0.36f, 0.32f), style = stroke)
                drawCircle(color.copy(alpha = 0.7f), radius = w * 0.12f, center = p(0.70f, 0.38f), style = stroke)
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = p(0.12f, 0.44f),
                    size = Size(w * 0.48f, h * 0.42f),
                    style = stroke,
                )
            }

            BackgammonIcon.Bluetooth -> {
                // The classic two triangles of the Bluetooth mark, drawn from four lines.
                drawLine(color, p(0.5f, 0.06f), p(0.5f, 0.94f), strokeWidth = stroke.width)
                drawLine(color, p(0.5f, 0.06f), p(0.76f, 0.30f), strokeWidth = stroke.width)
                drawLine(color, p(0.76f, 0.30f), p(0.26f, 0.70f), strokeWidth = stroke.width)
                drawLine(color, p(0.5f, 0.94f), p(0.76f, 0.70f), strokeWidth = stroke.width)
                drawLine(color, p(0.76f, 0.70f), p(0.26f, 0.30f), strokeWidth = stroke.width)
            }

            BackgammonIcon.Plus -> {
                drawLine(color, p(0.5f, 0.16f), p(0.5f, 0.84f), strokeWidth = w * 0.14f)
                drawLine(color, p(0.16f, 0.5f), p(0.84f, 0.5f), strokeWidth = w * 0.14f)
            }

            BackgammonIcon.Enter -> {
                drawPath(Path().apply {
                    moveTo(w * 0.20f, h * 0.72f)
                    lineTo(w * 0.72f, h * 0.20f)
                }, color, style = stroke)
                drawPath(Path().apply {
                    moveTo(w * 0.42f, h * 0.18f)
                    lineTo(w * 0.74f, h * 0.18f)
                    lineTo(w * 0.74f, h * 0.50f)
                }, color, style = stroke)
            }

            BackgammonIcon.Sparkle -> {
                drawPath(Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.62f, h * 0.38f)
                    lineTo(w * 0.92f, h * 0.5f)
                    lineTo(w * 0.62f, h * 0.62f)
                    lineTo(w * 0.5f, h * 0.92f)
                    lineTo(w * 0.38f, h * 0.62f)
                    lineTo(w * 0.08f, h * 0.5f)
                    lineTo(w * 0.38f, h * 0.38f)
                    close()
                }, color)
            }

            BackgammonIcon.Settings -> {
                drawCircle(color, radius = w * 0.20f, center = p(0.5f, 0.5f), style = stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians((i * 45).toDouble())
                    drawCircle(
                        color,
                        radius = w * 0.055f,
                        center = p(0.5f + (Math.cos(a) * 0.36f).toFloat(), 0.5f + (Math.sin(a) * 0.36f).toFloat()),
                    )
                }
            }

            BackgammonIcon.Shop -> {
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

            BackgammonIcon.Back -> {
                // RTL "back": the chevron points to the start of the line (right side in RTL).
                drawPath(Path().apply {
                    moveTo(w * 0.38f, h * 0.18f)
                    lineTo(w * 0.70f, h * 0.50f)
                    lineTo(w * 0.38f, h * 0.82f)
                }, color, style = Stroke(width = w * 0.14f))
            }

            BackgammonIcon.ChevronDown, BackgammonIcon.ChevronUp -> {
                val y1 = if (icon == BackgammonIcon.ChevronDown) 0.38f else 0.62f
                val y2 = if (icon == BackgammonIcon.ChevronDown) 0.66f else 0.34f
                drawPath(Path().apply {
                    moveTo(w * 0.18f, h * y1)
                    lineTo(w * 0.50f, h * y2)
                    lineTo(w * 0.82f, h * y1)
                }, color, style = Stroke(width = w * 0.13f))
            }

            BackgammonIcon.Check -> drawPath(Path().apply {
                moveTo(w * 0.18f, h * 0.52f)
                lineTo(w * 0.42f, h * 0.76f)
                lineTo(w * 0.84f, h * 0.26f)
            }, color, style = Stroke(width = w * 0.14f))

            BackgammonIcon.Dice -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(0.14f, 0.14f),
                    size = Size(w * 0.72f, h * 0.72f),
                    cornerRadius = CornerRadius(w * 0.16f),
                    style = stroke,
                )
                listOf(0.34f to 0.34f, 0.66f to 0.34f, 0.5f to 0.5f, 0.34f to 0.66f, 0.66f to 0.66f).forEach { (fx, fy) ->
                    drawCircle(color, radius = w * 0.07f, center = p(fx, fy))
                }
            }

            BackgammonIcon.Palette -> {
                drawCircle(color, radius = w * 0.40f, center = p(0.5f, 0.5f), style = stroke)
                drawCircle(color, radius = w * 0.07f, center = p(0.38f, 0.38f))
                drawCircle(color, radius = w * 0.07f, center = p(0.62f, 0.36f))
                drawCircle(color, radius = w * 0.07f, center = p(0.66f, 0.62f))
            }

            BackgammonIcon.Lock -> {
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

            BackgammonIcon.Trophy -> {
                drawPath(Path().apply {
                    moveTo(w * 0.30f, h * 0.16f)
                    lineTo(w * 0.70f, h * 0.16f)
                    lineTo(w * 0.66f, h * 0.54f)
                    lineTo(w * 0.34f, h * 0.54f)
                    close()
                }, color, style = stroke)
                drawLine(color, p(0.50f, 0.54f), p(0.50f, 0.78f), strokeWidth = w * 0.10f)
                drawLine(color, p(0.30f, 0.86f), p(0.70f, 0.86f), strokeWidth = w * 0.10f)
            }

            BackgammonIcon.Trash -> {
                drawRoundRect(
                    color = color,
                    topLeft = p(0.26f, 0.30f),
                    size = Size(w * 0.48f, h * 0.56f),
                    cornerRadius = CornerRadius(w * 0.07f),
                    style = stroke,
                )
                drawLine(color, p(0.18f, 0.26f), p(0.82f, 0.26f), strokeWidth = w * 0.10f)
            }
        }
    }
}
