package ir.dorehami.games.backgammon.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The module's colours: **the app's own scheme**, value for value — cream paper, white glass
 * cards, burnt orange, teal, violet, gold outline. Chess uses exactly these numbers, so the
 * backgammon room is the same house as the shell, the game picker and chess; only the board
 * itself is allowed to look like an object sitting on the table.
 */
object BackgammonPalette {
    /** Page background (app `background`). */
    val paper = Color(0xFFFFF4DC)

    /** Card/panel surface (app `surface`). */
    val card = Color(0xFFFFFFFF)

    /** Secondary surface (app `surfaceVariant`). */
    val cardAlt = Color(0xFFFFE9C2)

    /** Primary text (app `onSurface`). */
    val ink = Color(0xFF2E2415)

    /** Secondary text (app `onSurfaceVariant`). */
    val inkSoft = Color(0xFF5B4B33)

    /** Accent / primary (app `primary`). */
    val accent = Color(0xFFD9480F)

    /** Secondary accent (app `secondary`). */
    val teal = Color(0xFF0E8F87)

    /** Tertiary accent (app `tertiary`). */
    val violet = Color(0xFF7B4CC2)

    /** Hairline outline (app `outline`). */
    val line = Color(0xFFC9A96B)

    /** Errors and warnings (app `error`). */
    val danger = Color(0xFFB3261E)

    /** Board light points: warm bone. */
    val pointLight = Color(0xFFF3E2BE)

    /** Board dark points: the app's teal. */
    val pointDark = Color(0xFF0E8F87)

    /** The board's centre bar and frame trim. */
    val bar = Color(0xFFC9A96B)

    /** Checker face / highlight. */
    val ivory = Color(0xFFFDFBF4)

    /** Deepest ink used inside the board (point edges, dice pips). */
    val ebony = Color(0xFF2E2415)
}

private const val BACKGAMMON_TYPE_SCALE = 1.08f

/** Same bundled Persian face as the app shell, chess and Hokm. */
private val BackgammonFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.scaled() = copy(
    fontFamily = BackgammonFontFamily,
    fontSize = if (fontSize.isSpecified) fontSize * BACKGAMMON_TYPE_SCALE else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * BACKGAMMON_TYPE_SCALE else lineHeight,
)

private fun backgammonTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),
        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),
        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),
        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),
        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

@Composable
fun BackgammonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = backgammonTypography(),
        colorScheme = lightColorScheme(
            primary = BackgammonPalette.accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE08A),
            onPrimaryContainer = Color(0xFF5C2A00),
            secondary = BackgammonPalette.teal,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFC9F2EC),
            onSecondaryContainer = Color(0xFF00302C),
            tertiary = BackgammonPalette.violet,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEEE7FF),
            onTertiaryContainer = Color(0xFF3A1A6B),
            background = BackgammonPalette.paper,
            onBackground = BackgammonPalette.ink,
            surface = BackgammonPalette.card,
            onSurface = BackgammonPalette.ink,
            surfaceVariant = BackgammonPalette.cardAlt,
            onSurfaceVariant = BackgammonPalette.inkSoft,
            outline = BackgammonPalette.line,
            outlineVariant = BackgammonPalette.cardAlt,
            error = BackgammonPalette.danger,
            onError = Color.White,
            surfaceContainer = BackgammonPalette.card,
        ),
        content = content,
    )
}

/**
 * The module backdrop: the generated table art under the **same light veil** the app shell uses,
 * plus three soft colour glows (orange, teal, violet). No dark scrim — the artwork has to stay
 * visible, exactly like the home screen, the game picker and chess.
 */
@Composable
fun BackgammonBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backgammonBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "backgammonBackdropPhase",
    )
    val drift = sin(phase * 2f * PI.toFloat())

    Box(modifier.fillMaxSize().background(BackgammonPalette.paper)) {
        Image(
            painter = painterResource(R.drawable.backgammon_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = 1.025f + 0.012f * drift
                    scaleX = zoom
                    scaleY = zoom
                    rotationZ = 0.18f * drift
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BackgammonPalette.paper.copy(alpha = 0.20f),
                            BackgammonPalette.paper.copy(alpha = 0.14f),
                            BackgammonPalette.paper.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.43f
            val glows = listOf(
                Triple(Offset(size.width * 0.02f, size.height * 0.20f), BackgammonPalette.accent, 0.11f),
                Triple(Offset(size.width * 0.98f, size.height * 0.60f), BackgammonPalette.teal, 0.10f),
                Triple(Offset(size.width * 0.72f, size.height * 1.02f), BackgammonPalette.violet, 0.09f),
            )
            glows.forEachIndexed { index, (origin, color, alpha) ->
                val sway = sin(phase * 2f * PI.toFloat() + index) * size.minDimension * 0.018f
                val center = Offset(origin.x + sway, origin.y - sway * 0.35f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    center = center,
                    radius = radius,
                )
            }
        }
    }
}

/** Glass-ish card used by every backgammon menu and HUD row. */
@Composable
fun BackgammonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(BackgammonPalette.card.copy(alpha = 0.82f))
            .border(1.dp, BackgammonPalette.line.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

/** Big menu row: the offline/online split reads the same as the chess menu. */
@Composable
fun BackgammonMenuAction(
    icon: BackgammonIcon,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BackgammonPalette.cardAlt.copy(alpha = if (enabled) 0.95f else 0.5f))
            .border(1.dp, BackgammonPalette.line.copy(alpha = if (enabled) 0.55f else 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgammonPalette.accent.copy(alpha = 0.16f))
                .border(1.dp, BackgammonPalette.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BackgammonIconView(icon = icon, color = if (enabled) BackgammonPalette.accent else BackgammonPalette.inkSoft)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BackgammonPalette.inkSoft, fontSize = 12.sp, lineHeight = 17.sp)
        }
        BackgammonIconView(BackgammonIcon.Back, color = if (enabled) BackgammonPalette.accent else BackgammonPalette.inkSoft)
    }
}

/** Header with the app identity and a back button; used by every screen of this module. */
@Composable
fun BackgammonHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = BackgammonPalette.inkSoft, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BackgammonPalette.cardAlt)
                .border(1.dp, BackgammonPalette.line.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                BackgammonIconView(BackgammonIcon.Back, color = BackgammonPalette.ink, size = 14.dp)
                Text("بازگشت", color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shared page scaffold of this module (backdrop + safe padding + scrolling column). */
@Composable
fun BackgammonPage(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BackgammonBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Center-aligned short note (status line under the board). */
@Composable
fun BackgammonNote(text: String, color: Color = BackgammonPalette.inkSoft, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
