package ir.dorehami.games.backgammon.ui

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
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The module's colours. The chrome follows the app's dark scheme (`#1E1A26` / `#2A2433`, amber
 * `#FFB86B`, teal `#5FD3C7`, violet `#C9A5FF`) so backgammon looks like a room of the same house as
 * the shell and Hokm; only the board itself keeps its wooden material colours.
 */
object BackgammonPalette {
    val night = Color(0xFF1E1A26)
    val walnut = Color(0xFF2A2433)
    val walnutLight = Color(0xFF3A3346)
    val pointLight = Color(0xFFE6D3AE)
    val pointDark = Color(0xFF6E3F22)
    val bar = Color(0xFF241B14)
    val ivory = Color(0xFFFDFBF4)
    val ebony = Color(0xFF1C1C1E)
    val gold = Color(0xFFFFB86B)
    val goldDim = Color(0xFF9A90A8)
    val mint = Color(0xFF5FD3C7)
    val coral = Color(0xFFFFB4A9)
    val text = Color(0xFFF4EEFA)
    val muted = Color(0xFFD2C8DC)
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
        colorScheme = darkColorScheme(
            primary = BackgammonPalette.gold,
            onPrimary = BackgammonPalette.night,
            secondary = BackgammonPalette.mint,
            onSecondary = BackgammonPalette.night,
            tertiary = BackgammonPalette.coral,
            background = BackgammonPalette.night,
            onBackground = BackgammonPalette.text,
            surface = BackgammonPalette.walnut,
            onSurface = BackgammonPalette.text,
            surfaceVariant = BackgammonPalette.walnutLight,
            onSurfaceVariant = BackgammonPalette.muted,
            outline = BackgammonPalette.goldDim,
            error = BackgammonPalette.coral,
            onError = BackgammonPalette.night,
            outlineVariant = BackgammonPalette.walnutLight,
            surfaceContainer = BackgammonPalette.walnut,
        ),
        content = content,
    )
}

/**
 * The module backdrop: the generated table photograph under the app's dark scrim, plus two soft
 * corner glows so the screen stays readable on cheap panels.
 */
@Composable
fun BackgammonBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(BackgammonPalette.night)) {
        Image(
            painter = painterResource(R.drawable.backgammon_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            BackgammonPalette.night.copy(alpha = 0.80f),
                            BackgammonPalette.night.copy(alpha = 0.72f),
                            BackgammonPalette.night.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1B140D), BackgammonPalette.night),
                ),
            )
            drawCircle(
                color = BackgammonPalette.gold.copy(alpha = 0.07f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.9f, size.height * 0.05f),
            )
            drawCircle(
                color = BackgammonPalette.mint.copy(alpha = 0.05f),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.05f, size.height * 0.95f),
            )
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
            .clip(RoundedCornerShape(22.dp))
            .background(BackgammonPalette.walnut.copy(alpha = 0.92f))
            .border(1.dp, BackgammonPalette.goldDim.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
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
            .background(BackgammonPalette.walnutLight.copy(alpha = if (enabled) 0.95f else 0.5f))
            .border(1.dp, BackgammonPalette.goldDim.copy(alpha = if (enabled) 0.55f else 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(BackgammonPalette.gold.copy(alpha = 0.16f))
                .border(1.dp, BackgammonPalette.gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BackgammonIconView(icon = icon, color = if (enabled) BackgammonPalette.gold else BackgammonPalette.muted)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BackgammonPalette.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        BackgammonIconView(BackgammonIcon.Back, color = if (enabled) BackgammonPalette.gold else BackgammonPalette.muted)
    }
}

/** Header with the app identity and a back button; used by every screen of this module. */
@Composable
fun BackgammonHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = BackgammonPalette.muted, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BackgammonPalette.walnutLight)
                .border(1.dp, BackgammonPalette.goldDim.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                BackgammonIconView(BackgammonIcon.Back, color = BackgammonPalette.text, size = 14.dp)
                Text("بازگشت", color = BackgammonPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
fun BackgammonNote(text: String, color: Color = BackgammonPalette.muted, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
