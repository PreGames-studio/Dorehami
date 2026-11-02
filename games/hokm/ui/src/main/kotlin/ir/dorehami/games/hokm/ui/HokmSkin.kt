package ir.dorehami.games.hokm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Pattern painted on top of a card back. Kept tiny so a hand of 13 backs stays cheap to draw. */
enum class HokmBackPattern { GIRIH, LATTICE, STARS, DAMASK, PLAIN }

/** Table pattern painted over the baize. */
enum class HokmFeltPattern { NONE, STARS, GIRIH, CLOUDS }

/**
 * A card-back design. Colour is applied as a wash over the painted girih artwork, so every design
 * keeps the hand-drawn lattice while costing one rect + a few strokes per card.
 */
data class HokmCardBackSkin(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val tintTop: Color,
    val tintBottom: Color,
    val edge: Color,
    val pattern: HokmBackPattern,
    val winsRequired: Int = 0,
)

/**
 * A table design: felt wash + rim/accent colour + optional pattern over the walnut photo.
 */
data class HokmTableSkin(
    val id: String,
    val titleFa: String,
    val subtitleFa: String,
    val wash: Color,
    val rim: Color,
    val accent: Color,
    val pattern: HokmFeltPattern,
    val winsRequired: Int = 0,
)

/** The current look of the table. Read through [LocalHokmSkin]; the default is the classic set. */
data class HokmLook(
    val cardBack: HokmCardBackSkin = HokmSkins.DefaultCardBack,
    val table: HokmTableSkin = HokmSkins.DefaultTable,
)

val LocalHokmLook = compositionLocalOf { HokmLook() }

@Composable
fun HokmLookProvider(cardBackId: String?, tableId: String?, content: @Composable () -> Unit) {
    val look = HokmLook(
        cardBack = HokmSkins.cardBack(cardBackId),
        table = HokmSkins.table(tableId),
    )
    CompositionLocalProvider(LocalHokmLook provides look, content = content)
}

/**
 * The catalogue. Everything is drawn in-house (no third-party art, no licences) and unlocked with
 * local wins, so the shop never needs a network call and works in Bluetooth-only games.
 */
object HokmSkins {
    val DefaultCardBack = HokmCardBackSkin(
        id = "girih",
        titleFa = "گره طلایی",
        subtitleFa = "طرح کلاسیک حکم",
        tintTop = Color.Transparent,
        tintBottom = Color.Transparent,
        edge = Color(0xFFFFB86B),
        pattern = HokmBackPattern.GIRIH,
        winsRequired = 0,
    )

    val DefaultTable = HokmTableSkin(
        id = "emerald",
        titleFa = "مخمل زمردی",
        subtitleFa = "میز همیشگی",
        wash = Color(0xFF0E8F87),
        rim = Color(0xFF8B5A2B),
        accent = Color(0xFFFFB86B),
        pattern = HokmFeltPattern.GIRIH,
        winsRequired = 0,
    )

    val cardBacks: List<HokmCardBackSkin> = listOf(
        DefaultCardBack,
        HokmCardBackSkin(
            id = "sapphire",
            titleFa = "شب سربی",
            subtitleFa = "آبی شبانه با ستاره‌های کوچک",
            tintTop = Color(0xCC1B3F73),
            tintBottom = Color(0xE60B1B33),
            edge = Color(0xFF7FB8FF),
            pattern = HokmBackPattern.STARS,
            winsRequired = 0,
        ),
        HokmCardBackSkin(
            id = "damask",
            titleFa = "ترمه گلدار",
            subtitleFa = "قرمز تیره با نقش ترنج",
            tintTop = Color(0xCC8E1F2E),
            tintBottom = Color(0xE63A0C13),
            edge = Color(0xFFFF9E9E),
            pattern = HokmBackPattern.DAMASK,
            winsRequired = 2,
        ),
        HokmCardBackSkin(
            id = "obsidian",
            titleFa = "سنگ سیاه",
            subtitleFa = "مینیمال و آرام",
            tintTop = Color(0xE6141119),
            tintBottom = Color(0xF208070A),
            edge = Color(0xFF9A90A8),
            pattern = HokmBackPattern.LATTICE,
            winsRequired = 5,
        ),
        HokmCardBackSkin(
            id = "sand",
            titleFa = "کاغذ کهنه",
            subtitleFa = "کرم و طلایی",
            tintTop = Color(0xCCE7CFA1),
            tintBottom = Color(0xE6B89155),
            edge = Color(0xFF6B4A16),
            pattern = HokmBackPattern.PLAIN,
            winsRequired = 8,
        ),
    )

    val tables: List<HokmTableSkin> = listOf(
        DefaultTable,
        HokmTableSkin(
            id = "night",
            titleFa = "شب بنفش",
            subtitleFa = "مخمل تیره با ستاره",
            wash = Color(0xFF6B4CC2),
            rim = Color(0xFF3A3346),
            accent = Color(0xFFC9A5FF),
            pattern = HokmFeltPattern.STARS,
            winsRequired = 0,
        ),
        HokmTableSkin(
            id = "ruby",
            titleFa = "یاقوت سرخ",
            subtitleFa = "میز گرم و پرانرژی",
            wash = Color(0xFF9E1F2E),
            rim = Color(0xFF5C2A00),
            accent = Color(0xFFFFB86B),
            pattern = HokmFeltPattern.CLOUDS,
            winsRequired = 3,
        ),
        HokmTableSkin(
            id = "royal",
            titleFa = "تخت شاهی",
            subtitleFa = "سبز سلطنتی با حاشیه طلا",
            wash = Color(0xFF0B4F3A),
            rim = Color(0xFF1F4A38),
            accent = Color(0xFFFFD98A),
            pattern = HokmFeltPattern.GIRIH,
            winsRequired = 6,
        ),
        HokmTableSkin(
            id = "tea",
            titleFa = "کهربا",
            subtitleFa = "قهوه‌ای گرم، مثل خانه",
            wash = Color(0xFF8B5A2B),
            rim = Color(0xFF3A2A12),
            accent = Color(0xFFFFE0A8),
            pattern = HokmFeltPattern.NONE,
            winsRequired = 10,
        ),
    )

    fun cardBack(id: String?): HokmCardBackSkin =
        cardBacks.firstOrNull { it.id == id } ?: DefaultCardBack

    fun table(id: String?): HokmTableSkin =
        tables.firstOrNull { it.id == id } ?: DefaultTable

    /** A design opens after [HokmCardBackSkin.winsRequired] (or [HokmTableSkin.winsRequired]) wins. */
    fun unlocked(winsRequired: Int, wins: Int): Boolean = wins >= winsRequired

    fun unlocked(skin: HokmCardBackSkin, wins: Int): Boolean = unlocked(skin.winsRequired, wins)

    fun unlocked(skin: HokmTableSkin, wins: Int): Boolean = unlocked(skin.winsRequired, wins)
}

// ------------------------------------------------------------------------------------ card back

/**
 * A card back in the current [LocalHokmLook]. The default design is exactly the bundled artwork, so
 * nothing changes for players who never open the shop.
 */
@Composable
fun HokmCardBack(
    width: Dp,
    modifier: Modifier = Modifier,
    elevation: Dp = 4.dp,
) {
    val look = LocalHokmLook.current
    CardBackView(width = width, modifier = modifier, elevation = elevation, skin = look.cardBack)
}

private fun DrawScope.drawBackPattern(pattern: HokmBackPattern, color: Color) {
    val w = size.width
    val h = size.height
    val m = w * 0.09f
    val stroke = Stroke(width = w * 0.012f)
    when (pattern) {
        HokmBackPattern.PLAIN -> Unit

        HokmBackPattern.GIRIH -> {
            // A single medallion instead of a full lattice: one path, no per-frame layout.
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w / 2f, h * 0.16f)
                lineTo(w * 0.84f, h / 2f)
                lineTo(w / 2f, h * 0.84f)
                lineTo(w * 0.16f, h / 2f)
                close()
            }
            drawPath(path, color, style = stroke)
        }

        HokmBackPattern.LATTICE -> {
            val step = w * 0.20f
            var x = m
            while (x < w - m) {
                drawLine(color, Offset(x, m), Offset(x, h - m), strokeWidth = stroke.width)
                x += step
            }
            var y = m
            while (y < h - m) {
                drawLine(color, Offset(m, y), Offset(w - m, y), strokeWidth = stroke.width)
                y += step
            }
        }

        HokmBackPattern.STARS -> {
            val points = listOf(0.30f to 0.30f, 0.62f to 0.24f, 0.44f to 0.52f, 0.70f to 0.62f, 0.34f to 0.74f)
            points.forEach { (fx, fy) ->
                drawCircle(color, radius = w * 0.035f, center = Offset(w * fx, h * fy))
            }
        }

        HokmBackPattern.DAMASK -> {
            drawCircle(color, radius = w * 0.22f, center = Offset(w / 2f, h / 2f), style = stroke)
            drawCircle(color.copy(alpha = 0.7f), radius = w * 0.12f, center = Offset(w / 2f, h / 2f), style = stroke)
        }
    }
}

// ----------------------------------------------------------------------------------------- felt

/** Paints the current table skin over the walnut/felt artwork. */
@Composable
internal fun HokmSkinWash(skin: HokmTableSkin, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(skin.wash.copy(alpha = 0.22f))
        drawFeltPattern(skin.pattern, skin.accent.copy(alpha = 0.16f))
        // Rim glow: a warm inner edge so the table still feels like a physical object.
        drawRoundRect(
            color = skin.rim.copy(alpha = 0.35f),
            topLeft = Offset(2f, 2f),
            size = Size(size.width - 4f, size.height - 4f),
            cornerRadius = CornerRadius(size.width * 0.10f),
            style = Stroke(width = 6f),
        )
    }
}

private fun DrawScope.drawFeltPattern(pattern: HokmFeltPattern, color: Color) {
    val w = size.width
    val h = size.height
    when (pattern) {
        HokmFeltPattern.NONE -> Unit

        HokmFeltPattern.STARS -> {
            listOf(
                0.18f to 0.22f, 0.78f to 0.18f, 0.12f to 0.74f, 0.86f to 0.70f,
                0.50f to 0.12f, 0.62f to 0.86f, 0.30f to 0.58f,
            ).forEach { (fx, fy) ->
                drawCircle(color, radius = w * 0.008f, center = Offset(w * fx, h * fy))
            }
        }

        HokmFeltPattern.GIRIH -> {
            val c = Offset(w / 2f, h * 0.45f)
            drawCircle(color, radius = w * 0.24f, center = c, style = Stroke(w * 0.004f))
            drawCircle(color, radius = w * 0.17f, center = c, style = Stroke(w * 0.003f))
        }

        HokmFeltPattern.CLOUDS -> {
            // Soft horizontal arcs: a woven texture rather than a motif.
            var y = h * 0.18f
            while (y < h * 0.9f) {
                drawArc(
                    color = color.copy(alpha = color.alpha * 0.6f),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.08f, y),
                    size = Size(w * 0.84f, h * 0.08f),
                    style = Stroke(w * 0.0025f),
                )
                y += h * 0.14f
            }
        }
    }
}
