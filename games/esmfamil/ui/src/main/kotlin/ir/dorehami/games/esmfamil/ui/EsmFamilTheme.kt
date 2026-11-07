package ir.dorehami.games.esmfamil.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The module's colours: **the app's own scheme, value for value** — the same cream paper, white
 * glass cards and burnt-orange accent that the shell, chess, Hokm and backgammon use
 * (`docs/DESIGN_LANGUAGE.md`). اسم فامیل adds only one thing of its own: the pen-blue used for the
 * handwritten answers, so a sheet reads like ink on paper without inventing a second theme.
 */
object EsmFamilPalette {
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

    /** Pen ink for a written answer — a deep blue, the only new colour in this module. */
    val pen = Color(0xFF1F3A63)

    /** Ruled paper line under a writing row. */
    val rule = Color(0xFFDCC9A4)

    /** The big round letter card. */
    val letterCard = Color(0xFFFFF9EC)

    /** Points badges: unique / shared / nothing. */
    val pointsFull = Color(0xFF0E8F87)
    val pointsHalf = Color(0xFFC9A96B)
    val pointsNone = Color(0xFF9B8B72)
}

/** Same 15% bump as the shell, chess, Hokm, Pantohub and backgammon: one type scale app-wide. */
private const val ESFAMIL_TYPE_SCALE = 1.15f

private val EsmFamilFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.scaled() = copy(
    fontFamily = EsmFamilFontFamily,
    fontSize = if (fontSize.isSpecified) fontSize * ESFAMIL_TYPE_SCALE else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * ESFAMIL_TYPE_SCALE else lineHeight,
)

private fun esmFamilTypography(): Typography {
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
fun EsmFamilTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = esmFamilTypography(),
        colorScheme = lightColorScheme(
            primary = EsmFamilPalette.accent,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFFE08A),
            onPrimaryContainer = Color(0xFF5C2A00),
            secondary = EsmFamilPalette.teal,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFC9F2EC),
            onSecondaryContainer = Color(0xFF00302C),
            tertiary = EsmFamilPalette.violet,
            onTertiary = Color.White,
            tertiaryContainer = Color(0xFFEEE7FF),
            onTertiaryContainer = Color(0xFF3A1A6B),
            background = EsmFamilPalette.paper,
            onBackground = EsmFamilPalette.ink,
            surface = EsmFamilPalette.card,
            onSurface = EsmFamilPalette.ink,
            surfaceVariant = EsmFamilPalette.cardAlt,
            onSurfaceVariant = EsmFamilPalette.inkSoft,
            outline = EsmFamilPalette.line,
            outlineVariant = EsmFamilPalette.cardAlt,
            error = EsmFamilPalette.danger,
            onError = Color.White,
            surfaceContainer = EsmFamilPalette.card,
        ),
        content = content,
    )
}

/**
 * The module backdrop: its own artwork under the exact veil and glow recipe the other game modules
 * use (0.18 / 0.12 / 0.32 of the page colour plus orange, teal and violet glows) — so the game
 * picker, chess, Hokm, backgammon and this table read as one house.
 */
@Composable
fun EsmFamilBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "esmFamilBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "esmFamilBackdropPhase",
    )
    val drift = sin(phase * 2f * PI.toFloat())

    Box(modifier.fillMaxSize().background(EsmFamilPalette.paper)) {
        Image(
            painter = painterResource(R.drawable.esmfamil_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = 1.025f + 0.012f * drift
                    scaleX = zoom
                    scaleY = zoom
                    rotationZ = 0.16f * drift
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            EsmFamilPalette.paper.copy(alpha = 0.18f),
                            EsmFamilPalette.paper.copy(alpha = 0.12f),
                            EsmFamilPalette.paper.copy(alpha = 0.32f),
                        ),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.43f
            val glows = listOf(
                Triple(Offset(size.width * 0.04f, size.height * 0.22f), EsmFamilPalette.accent, 0.10f),
                Triple(Offset(size.width * 0.96f, size.height * 0.58f), EsmFamilPalette.teal, 0.10f),
                Triple(Offset(size.width * 0.70f, size.height * 1.02f), EsmFamilPalette.violet, 0.09f),
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

/** Glass card used by every menu and HUD row of this module. */
@Composable
fun EsmFamilCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(EsmFamilPalette.card.copy(alpha = 0.84f))
            .border(1.dp, EsmFamilPalette.line.copy(alpha = 0.72f), RoundedCornerShape(24.dp))
            .padding(16.dp),
        content = content,
    )
}

/** Big menu row: the offline/bluetooth/online split reads like every other game's menu. */
@Composable
fun EsmFamilMenuAction(
    icon: EsmFamilIcon,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(EsmFamilPalette.cardAlt.copy(alpha = if (enabled) 0.95f else 0.5f))
            .border(1.dp, EsmFamilPalette.line.copy(alpha = if (enabled) 0.55f else 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EsmFamilPalette.accent.copy(alpha = 0.16f))
                .border(1.dp, EsmFamilPalette.accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            EsmFamilIconView(icon = icon, color = if (enabled) EsmFamilPalette.accent else EsmFamilPalette.inkSoft)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = EsmFamilPalette.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = EsmFamilPalette.inkSoft, fontSize = 12.sp, lineHeight = 17.sp)
        }
        EsmFamilIconView(EsmFamilIcon.Back, color = if (enabled) EsmFamilPalette.accent else EsmFamilPalette.inkSoft)
    }
}

/** Header with a back button; used by every screen of this module. */
@Composable
fun EsmFamilHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = EsmFamilPalette.ink, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = EsmFamilPalette.inkSoft, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(EsmFamilPalette.cardAlt)
                .border(1.dp, EsmFamilPalette.line.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                EsmFamilIconView(EsmFamilIcon.Back, color = EsmFamilPalette.ink, size = 14.dp)
                Text("بازگشت", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Shared page scaffold (backdrop + safe padding + scrolling column). */
@Composable
fun EsmFamilPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        EsmFamilBackdrop(Modifier.fillMaxSize())
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

/** Center-aligned short note. */
@Composable
fun EsmFamilNote(text: String, color: Color = EsmFamilPalette.inkSoft, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Primary/secondary action button, the same shape the other tables use. */
@Composable
fun EsmFamilButton(
    text: String,
    primary: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> EsmFamilPalette.cardAlt.copy(alpha = 0.5f)
                    primary -> EsmFamilPalette.accent
                    else -> EsmFamilPalette.cardAlt
                },
            )
            .border(
                1.dp,
                if (primary && enabled) EsmFamilPalette.accent else EsmFamilPalette.line.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> EsmFamilPalette.inkSoft
                primary -> Color.White
                else -> EsmFamilPalette.ink
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
