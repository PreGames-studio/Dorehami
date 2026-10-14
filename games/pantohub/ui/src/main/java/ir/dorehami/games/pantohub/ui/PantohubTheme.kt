package ir.dorehami.games.pantohub.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import ir.dorehami.core.uikit.DorehamiPageFrame
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pantohub design tokens — the single source of truth for the game's visual identity.
 *
 * Identity: deep navy night background, violet as the brand color, teal for interactive
 * and informational elements, orange reserved for the primary start action and exciting
 * moments, and a calm success green. Every Pantohub screen and component reads its colors,
 * radii and spacing from here so the UI stays consistent and maintainable.
 */
object PantohubDesign {
    val Background = Color(0xFF101426)
    val Surface = Color(0xFF1D2340)
    val SurfaceRaised = Color(0xFF252C4E)
    val SurfaceSoft = Color(0xFF2A3157)
    val Violet = Color(0xFF8B5CF6)
    val VioletDeep = Color(0xFF6D28D9)
    val Teal = Color(0xFF2DD4BF)
    val TealDeep = Color(0xFF0F766E)
    val Orange = Color(0xFFFF7849)
    val OrangeDeep = Color(0xFFEA580C)
    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFFB8C0D9)
    val Success = Color(0xFF4ADE80)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val Outline = Color(0xFF3D4570)

    /** Harmonious team palette: vivid on the navy surface without breaking the identity. */
    val TeamColors: List<Int> = listOf(
        0xFF8B5CF6.toInt(),
        0xFF2DD4BF.toInt(),
        0xFFFF7849.toInt(),
        0xFF4ADE80.toInt(),
        0xFF60A5FA.toInt(),
        0xFFF472B6.toInt(),
        0xFFFBBF24.toInt(),
        0xFF22D3EE.toInt(),
    )

    object Radius {
        val Card: Dp = 20.dp
        val CardLarge: Dp = 24.dp
        val Field: Dp = 14.dp
        val Chip: Dp = 999.dp
        val Button: Dp = 999.dp
    }

    object Spacing {
        val Xs: Dp = 4.dp
        val Sm: Dp = 8.dp
        val Md: Dp = 12.dp
        val Lg: Dp = 16.dp
        val Xl: Dp = 20.dp
        val Xxl: Dp = 28.dp
    }
}

/** Vazirmatn (OFL): a clean Persian/Latin typeface bundled in res/font. */
private val PantohubFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Material type scale with Vazirmatn, slightly enlarged for comfortable Persian reading. */
private fun persianTypography(scale: Float): Typography {
    fun TextStyle.fa() = copy(
        fontFamily = PantohubFontFamily,
        fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
    )
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.fa(),
        displayMedium = base.displayMedium.fa(),
        displaySmall = base.displaySmall.fa(),
        headlineLarge = base.headlineLarge.fa(),
        headlineMedium = base.headlineMedium.fa(),
        headlineSmall = base.headlineSmall.fa(),
        titleLarge = base.titleLarge.fa(),
        titleMedium = base.titleMedium.fa(),
        titleSmall = base.titleSmall.fa(),
        bodyLarge = base.bodyLarge.fa(),
        bodyMedium = base.bodyMedium.fa(),
        bodySmall = base.bodySmall.fa(),
        labelLarge = base.labelLarge.fa(),
        labelMedium = base.labelMedium.fa(),
        labelSmall = base.labelSmall.fa(),
    )
}

private val PantohubTypography: Typography = persianTypography(scale = 1.12f)

/** Whether decorative motion is enabled; set from Pantohub settings, frozen when off. */
internal val LocalPantohubMotion = compositionLocalOf { true }

/**
 * The Pantohub theme is intentionally always the deep-navy dark identity (day and night),
 * so the game has one recognizable brand look. Contrast is checked against [PantohubDesign].
 */
@Composable
internal fun PantohubTheme(motionEnabled: Boolean = true, content: @Composable () -> Unit) {
    val colors = remember {
        darkColorScheme(
            primary = PantohubDesign.Violet,
            onPrimary = Color.White,
            primaryContainer = PantohubDesign.VioletDeep,
            onPrimaryContainer = Color(0xFFEDE9FE),
            secondary = PantohubDesign.Teal,
            onSecondary = Color(0xFF04211D),
            secondaryContainer = PantohubDesign.TealDeep,
            onSecondaryContainer = Color(0xFFCCFBF1),
            tertiary = PantohubDesign.Orange,
            onTertiary = Color.White,
            tertiaryContainer = PantohubDesign.OrangeDeep,
            onTertiaryContainer = Color(0xFFFFEDD5),
            background = PantohubDesign.Background,
            onBackground = PantohubDesign.TextPrimary,
            surface = PantohubDesign.Surface,
            onSurface = PantohubDesign.TextPrimary,
            surfaceVariant = PantohubDesign.SurfaceRaised,
            onSurfaceVariant = PantohubDesign.TextSecondary,
            outline = PantohubDesign.Outline,
            outlineVariant = PantohubDesign.Outline.copy(alpha = 0.55f),
            error = PantohubDesign.Error,
            onError = Color(0xFF450A0A),
        )
    }
    CompositionLocalProvider(LocalPantohubMotion provides motionEnabled) {
        MaterialTheme(colorScheme = colors, typography = PantohubTypography, content = content)
    }
}

@Composable
internal fun PantohubPageFrame(
    title: String,
    onBack: () -> Unit,
    imageRes: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    DorehamiPageFrame(
        title = title,
        onBack = onBack,
        containerColor = MaterialTheme.colorScheme.background,
        backgroundLayer = { PantohubBackdrop() },
        content = content,
    )
}

/**
 * Signature Pantohub backdrop: a deep-navy vertical gradient with two soft, controlled
 * light halos (violet at the top, teal at the bottom) and a very light dusting of slow
 * particles. It stays behind content in every sense — cards and text always win.
 * The old busy photo backgrounds were retired in the redesign; [imageRes] is kept only
 * for call-site compatibility and is no longer rendered.
 */
@Composable
internal fun PantohubBackdrop(
    @Suppress("UNUSED_PARAMETER") imageRes: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF141A33),
                            PantohubDesign.Background,
                            Color(0xFF0C1022),
                        ),
                    ),
                ),
        )
        // Soft violet halo, top corner.
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = (-90).dp, y = (-120).dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PantohubDesign.Violet.copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        // Soft teal halo, bottom corner.
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = 190.dp, y = 560.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PantohubDesign.Teal.copy(alpha = 0.16f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        PantohubParticles(modifier = Modifier.fillMaxSize())
    }
}

/** Very limited, slow particles; frozen when the user turns animations off. */
@Composable
private fun PantohubParticles(modifier: Modifier = Modifier) {
    val motion = LocalPantohubMotion.current
    val transition = rememberInfiniteTransition(label = "pantohubParticles")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 18_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pantohubParticlePhase",
    )
    val phase = if (motion) animated else 0f
    val palette = remember {
        listOf(
            Color(0xFF8B5CF6),
            Color(0xFF2DD4BF),
            Color(0xFFF8FAFC),
        )
    }
    Canvas(modifier = modifier) {
        for (i in 0 until 14) {
            val baseX = ((i * 53 + 17) % 100) / 100f
            val baseY = ((i * 29 + 7) % 100) / 100f
            val speed = 1 + (i % 2)
            val y = ((baseY - phase * speed * 0.5f) % 1f + 1f) % 1f
            val x = baseX + 0.018f * sin(2f * PI.toFloat() * phase + i)
            val radius = (1.3f + (i % 3) * 0.7f) * density
            val alpha = 0.10f + ((i * 7) % 4) / 25f
            drawCircle(
                color = palette[i % palette.size].copy(alpha = alpha),
                radius = radius,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}
