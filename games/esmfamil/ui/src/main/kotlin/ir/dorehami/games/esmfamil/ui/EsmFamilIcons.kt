package ir.dorehami.games.esmfamil.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Icons of the اسم فامیل module, drawn with the canvas.
 *
 * The module text is Vazirmatn, which has no dingbat glyphs, and Android's fallback chain does not
 * cover all of them — that is how menu rows end up with empty boxes. Every icon here is a vector
 * shape, so it renders identically on every device (the same reason the other modules draw theirs).
 */
enum class EsmFamilIcon {
    Offline,
    Online,
    Bluetooth,
    Users,
    Pen,
    Timer,
    Trophy,
    Letters,
    Back,
    Check,
    Sparkle,
}

@Composable
fun EsmFamilIconView(
    icon: EsmFamilIcon,
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
            EsmFamilIcon.Offline -> {
                // Lightning: instant local play.
                drawPath(
                    Path().apply {
                        moveTo(w * 0.56f, h * 0.08f)
                        lineTo(w * 0.26f, h * 0.55f)
                        lineTo(w * 0.47f, h * 0.55f)
                        lineTo(w * 0.42f, h * 0.92f)
                        lineTo(w * 0.75f, h * 0.44f)
                        lineTo(w * 0.52f, h * 0.44f)
                        close()
                    },
                    color,
                )
            }

            EsmFamilIcon.Online -> {
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
            }

            EsmFamilIcon.Bluetooth -> {
                drawLine(color, p(0.36f, 0.16f), p(0.68f, 0.84f), strokeWidth = stroke.width)
                drawLine(color, p(0.68f, 0.16f), p(0.36f, 0.84f), strokeWidth = stroke.width)
                drawLine(color, p(0.36f, 0.16f), p(0.52f, 0.32f), strokeWidth = stroke.width)
                drawLine(color, p(0.36f, 0.84f), p(0.52f, 0.68f), strokeWidth = stroke.width)
                drawLine(color, p(0.52f, 0.32f), p(0.52f, 0.68f), strokeWidth = stroke.width)
                drawLine(color, p(0.52f, 0.50f), p(0.86f, 0.50f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Users -> {
                drawCircle(color, radius = w * 0.15f, center = p(0.34f, 0.33f), style = stroke)
                drawCircle(color, radius = w * 0.15f, center = p(0.68f, 0.40f), style = stroke)
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = p(0.14f, 0.52f),
                    size = Size(w * 0.40f, h * 0.36f),
                    style = stroke,
                )
                drawArc(
                    color = color,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = p(0.50f, 0.58f),
                    size = Size(w * 0.36f, h * 0.32f),
                    style = stroke,
                )
            }

            EsmFamilIcon.Pen -> {
                // A nib: diagonal body with a tip.
                drawLine(color, p(0.24f, 0.76f), p(0.74f, 0.24f), strokeWidth = stroke.width * 1.4f)
                drawPath(
                    Path().apply {
                        moveTo(w * 0.20f, h * 0.80f)
                        lineTo(w * 0.16f, h * 0.95f)
                        lineTo(w * 0.32f, h * 0.86f)
                        close()
                    },
                    color,
                )
                drawLine(color, p(0.62f, 0.20f), p(0.84f, 0.42f), strokeWidth = stroke.width * 1.1f)
            }

            EsmFamilIcon.Timer -> {
                drawCircle(color, radius = w * 0.38f, center = p(0.5f, 0.55f), style = stroke)
                drawLine(color, p(0.5f, 0.55f), p(0.5f, 0.32f), strokeWidth = stroke.width)
                drawLine(color, p(0.5f, 0.55f), p(0.68f, 0.64f), strokeWidth = stroke.width)
                drawLine(color, p(0.36f, 0.10f), p(0.64f, 0.10f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Trophy -> {
                drawPath(
                    Path().apply {
                        moveTo(w * 0.32f, h * 0.14f)
                        lineTo(w * 0.68f, h * 0.14f)
                        lineTo(w * 0.64f, h * 0.52f)
                        lineTo(w * 0.36f, h * 0.52f)
                        close()
                    },
                    color,
                )
                drawLine(color, p(0.50f, 0.52f), p(0.50f, 0.76f), strokeWidth = stroke.width)
                drawLine(color, p(0.30f, 0.88f), p(0.70f, 0.88f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Letters -> {
                // A sheet with a ruled line and a big first letter.
                drawRect(color, topLeft = p(0.16f, 0.12f), size = Size(w * 0.68f, h * 0.76f), style = stroke)
                drawLine(color, p(0.30f, 0.34f), p(0.70f, 0.34f), strokeWidth = stroke.width)
                drawLine(color, p(0.30f, 0.52f), p(0.62f, 0.52f), strokeWidth = stroke.width)
                drawLine(color, p(0.30f, 0.70f), p(0.54f, 0.70f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Back -> {
                drawLine(color, p(0.62f, 0.20f), p(0.34f, 0.50f), strokeWidth = stroke.width)
                drawLine(color, p(0.34f, 0.50f), p(0.62f, 0.80f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Check -> {
                drawLine(color, p(0.24f, 0.54f), p(0.44f, 0.74f), strokeWidth = stroke.width)
                drawLine(color, p(0.44f, 0.74f), p(0.78f, 0.28f), strokeWidth = stroke.width)
            }

            EsmFamilIcon.Sparkle -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val r = w * 0.34f
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = stroke.width)
                drawLine(color, Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = stroke.width)
                drawLine(color, Offset(cx - r * 0.7f, cy - r * 0.7f), Offset(cx + r * 0.7f, cy + r * 0.7f), strokeWidth = stroke.width * 0.7f)
            }
        }
    }
}
