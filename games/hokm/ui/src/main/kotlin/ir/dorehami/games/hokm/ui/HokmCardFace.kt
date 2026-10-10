package ir.dorehami.games.hokm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit

/** نسبتِ استانداردِ ورق (عرض/ارتفاع). */
const val CARD_ASPECT = 0.72f

private val CardCorner = RoundedCornerShape(9.dp)
private val SuitRed = Color(0xFFC62828)
private val SuitBlack = Color(0xFF1B1B1F)

/**
 * یک ورقِ بازی.
 *
 * روی کارت با وِکتور رسم می‌شود (کند و تیز روی همهٔ صفحه‌ها) و پشتِ آن تصویرِ واقع‌گرایانه است؛
 * یعنی هم حجمِ برنامه کم می‌ماند و هم ظاهرِ کارت روی گوشی‌های ارزان افت نمی‌کند.
 *
 * @param card کارت؛ اگر `null` باشد فقط جایِ خالی رسم می‌شود (برای انیمیشنِ پخش).
 * @param faceUp رو باشد یا پشت.
 * @param dim کم‌رنگ (برای کارت‌هایی که مجاز نیستند).
 * @param lift بالا آمدنِ کارت (برای انیمیشنِ انتخاب).
 */
@Composable
fun PlayingCard(
    card: UiCard?,
    modifier: Modifier = Modifier,
    faceUp: Boolean = true,
    dim: Boolean = false,
    lift: Dp = 0.dp,
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
) {
    // داخلِ کارت همیشه چپ‌به‌راست است (اندیسِ گوشه‌ها درست بماند) حتی وقتی برنامه راست‌به‌چپ است.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .aspectRatio(CARD_ASPECT)
                .offset(y = -lift)
                .shadow(elevation, CardCorner)
                .clip(CardCorner)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFFFFFDF7), Color(0xFFF0EDE3), Color(0xFFE6E1D4)),
                    ),
                )
                .border(0.8.dp, Color(0x33000000), CardCorner)
                .then(if (onClick != null) Modifier.clickable(enabled = !dim) { onClick() } else Modifier)
                .then(if (dim) Modifier.background(Color(0x66000000)) else Modifier),
        ) {
            if (card != null) {
                if (faceUp) CardFace(card) else CardBack()
            }
        }
    }
}

@Composable
private fun BoxScope.CardFace(card: UiCard) {
    val color = if (card.suit.isRed) SuitRed else SuitBlack

    Canvas(modifier = Modifier.fillMaxSize()) {
        when (card.rank) {
            Rank.ACE -> drawPip(card.suit, color, Offset(center.x, center.y), size.minDimension * 0.46f)
            Rank.JACK, Rank.QUEEN, Rank.KING -> drawCourt(card, color)
            else -> {
                val positions = pipPositions(card.rank.order)
                val pipSize = size.minDimension * 0.20f
                for ((nx, ny) in positions) {
                    drawPip(
                        suit = card.suit,
                        color = color,
                        center = Offset(size.width * nx, size.height * ny),
                        size = pipSize,
                    )
                }
            }
        }
    }

    // اندیسِ گوشهٔ بالا-چپ
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 5.dp, top = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = card.rank.label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text = card.suit.symbol, color = color, fontSize = 12.sp)
    }

    // اندیسِ گوشهٔ پایین-راست (چرخیده)
    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 5.dp, bottom = 3.dp)
            .rotate(180f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = card.rank.label, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(text = card.suit.symbol, color = color, fontSize = 12.sp)
    }
}

@Composable
private fun BoxScope.CardBack() {
    Image(
        painter = painterResource(id = R.drawable.hokm_card_back),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

/** کارت‌های سر (سرباز، بی‌بی، شاه): قابِ تزئینی با لوزی و نمادِ خال. */
private fun DrawScope.drawCourt(card: UiCard, color: Color) {
    val w = size.width
    val h = size.height
    val inset = w * 0.16f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset * 0.9f),
        size = Size(w - inset * 2, h - inset * 1.8f),
        cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        style = Stroke(width = w * 0.028f),
    )
    val cx = center.x
    val cy = center.y
    val r = w * 0.20f
    drawPath(
        path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r, cy)
            lineTo(cx, cy + r)
            lineTo(cx - r, cy)
            close()
        },
        color = color,
        alpha = 0.14f,
    )
    drawPip(card.suit, color, Offset(cx, cy), w * 0.30f)
}

/** رسمِ یک خال در مربعی به مرکزِ [center] و اندازهٔ [size]. */
private fun DrawScope.drawPip(suit: Suit, color: Color, center: Offset, size: Float) {
    val path = when (suit) {
        Suit.HEARTS -> heartPath()
        Suit.DIAMONDS -> diamondPath()
        Suit.SPADES -> spadePath()
        Suit.CLUBS -> clubPath()
    }
    translate(left = center.x - size / 2f, top = center.y - size / 2f) {
        scale(scaleX = size, scaleY = size, pivot = Offset.Zero) {
            drawPath(path, color)
        }
    }
}

// --- مسیرِ خال‌ها در یک مربعِ واحد (۰ تا ۱) ----------------------------------

private fun heartPath(): Path = Path().apply {
    fillType = PathFillType.NonZero
    moveTo(0.50f, 0.94f)
    cubicTo(0.06f, 0.66f, 0.10f, 0.20f, 0.33f, 0.20f)
    cubicTo(0.44f, 0.20f, 0.50f, 0.33f, 0.50f, 0.39f)
    cubicTo(0.50f, 0.33f, 0.56f, 0.20f, 0.67f, 0.20f)
    cubicTo(0.90f, 0.20f, 0.94f, 0.66f, 0.50f, 0.94f)
    close()
}

private fun diamondPath(): Path = Path().apply {
    moveTo(0.50f, 0.04f)
    lineTo(0.96f, 0.50f)
    lineTo(0.50f, 0.96f)
    lineTo(0.04f, 0.50f)
    close()
}

private fun spadePath(): Path = Path().apply {
    moveTo(0.50f, 0.03f)
    cubicTo(0.95f, 0.36f, 0.88f, 0.68f, 0.62f, 0.68f)
    cubicTo(0.56f, 0.68f, 0.52f, 0.63f, 0.50f, 0.58f)
    cubicTo(0.48f, 0.63f, 0.44f, 0.68f, 0.38f, 0.68f)
    cubicTo(0.12f, 0.68f, 0.05f, 0.36f, 0.50f, 0.03f)
    close()
    moveTo(0.44f, 0.64f)
    lineTo(0.56f, 0.64f)
    lineTo(0.64f, 0.97f)
    lineTo(0.36f, 0.97f)
    close()
}

private fun clubPath(): Path = Path().apply {
    fillType = PathFillType.NonZero
    addOval(Rect(0.30f, 0.06f, 0.70f, 0.46f))
    addOval(Rect(0.06f, 0.40f, 0.46f, 0.80f))
    addOval(Rect(0.54f, 0.40f, 0.94f, 0.80f))
    moveTo(0.42f, 0.62f)
    lineTo(0.58f, 0.62f)
    lineTo(0.65f, 0.97f)
    lineTo(0.35f, 0.97f)
    close()
}

/**
 * جای خال‌ها روی کارت‌های عددی، به مختصاتِ نرمال (۰ تا ۱).
 *
 * همان چیدمانِ کلاسیکِ ورق‌های استاندارد است تا کارت‌ها برای بازیکنِ ایرانی آشنا باشند.
 */
private fun pipPositions(rankOrder: Int): List<Pair<Float, Float>> {
    val l = 0.30f
    val c = 0.50f
    val r = 0.70f
    val top = 0.12f
    val bottom = 0.88f
    return when (rankOrder) {
        2 -> listOf(c to top, c to bottom)
        3 -> listOf(c to top, c to 0.50f, c to bottom)
        4 -> listOf(l to top, r to top, l to bottom, r to bottom)
        5 -> listOf(l to top, r to top, c to 0.50f, l to bottom, r to bottom)
        6 -> listOf(l to top, r to top, l to 0.50f, r to 0.50f, l to bottom, r to bottom)
        7 -> listOf(l to top, r to top, c to 0.30f, l to 0.50f, r to 0.50f, l to bottom, r to bottom)
        8 -> listOf(
            l to top, r to top, c to 0.30f,
            l to 0.50f, r to 0.50f,
            c to 0.70f, l to bottom, r to bottom,
        )
        9 -> listOf(
            l to top, r to top,
            l to 0.37f, r to 0.37f,
            l to 0.63f, r to 0.63f,
            l to bottom, r to bottom,
            c to 0.50f,
        )
        10 -> listOf(
            l to top, r to top,
            l to 0.37f, r to 0.37f,
            c to 0.24f,
            l to 0.63f, r to 0.63f,
            c to 0.76f,
            l to bottom, r to bottom,
        )
        else -> listOf(c to 0.50f)
    }
}

/** نمادِ کوچکِ خال (برای نشانِ حکم). */
@Composable
fun SuitSymbol(
    suit: Suit,
    modifier: Modifier = Modifier,
    sizeSp: Int = 20,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = suit.symbol,
        color = if (suit.isRed) SuitRed else tint,
        fontSize = sizeSp.sp,
        modifier = modifier,
    )
}
