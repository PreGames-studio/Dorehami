package ir.dorehami.games.pantohub.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified
import ir.dorehami.core.uikit.DorehamiPageFrame
import kotlin.math.PI
import kotlin.math.sin

/** Vazirmatn (OFL): a clean Persian/Latin typeface bundled in res/font. */
private val PantohubFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

/** Material type scale with Vazirmatn and about 15% larger sizes, so text is comfortable to read. */
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

private val PantohubTypography: Typography = persianTypography(scale = 1.15f)

/** Whether decorative motion (animated buttons, sparkles) is enabled; set from Pantohub settings. */
internal val LocalPantohubMotion = compositionLocalOf { true }

/** A bright, playful palette for Pantohub: sunny orange, lively teal, and warm paper tones. */
@Composable
internal fun PantohubTheme(motionEnabled: Boolean = true, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = remember(dark) {
        if (dark) {
            darkColorScheme(
                primary = Color(0xFFFFB86B),
                onPrimary = Color(0xFF3B2000),
                primaryContainer = Color(0xFF6A3B00),
                onPrimaryContainer = Color(0xFFFFE2BF),
                secondary = Color(0xFF5FD3C7),
                onSecondary = Color(0xFF003733),
                secondaryContainer = Color(0xFF00504A),
                onSecondaryContainer = Color(0xFFB8F5EE),
                tertiary = Color(0xFFC9A5FF),
                onTertiary = Color(0xFF3A1A6B),
                background = Color(0xFF1E1A26),
                onBackground = Color(0xFFF4EEFA),
                surface = Color(0xFF2A2433),
                onSurface = Color(0xFFF4EEFA),
                surfaceVariant = Color(0xFF3A3346),
                onSurfaceVariant = Color(0xFFD2C8DC),
                outline = Color(0xFF9A90A8),
                error = Color(0xFFFFB4A9),
                onError = Color(0xFF690005),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFFD9480F),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFFFE08A),
                onPrimaryContainer = Color(0xFF5C2A00),
                secondary = Color(0xFF0E8F87),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFC9F2EC),
                onSecondaryContainer = Color(0xFF00302C),
                tertiary = Color(0xFF7B4CC2),
                onTertiary = Color(0xFFFFFFFF),
                background = Color(0xFFFFF4DC),
                onBackground = Color(0xFF3A2A12),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF2E2415),
                surfaceVariant = Color(0xFFFFE9C2),
                onSurfaceVariant = Color(0xFF5B4B33),
                outline = Color(0xFFC9A96B),
                error = Color(0xFFB3261E),
                onError = Color(0xFFFFFFFF),
            )
        }
    }
    CompositionLocalProvider(LocalPantohubMotion provides motionEnabled) {
        MaterialTheme(colorScheme = colors, typography = PantohubTypography, content = content)
    }
}

@Composable
internal fun PantohubPageFrame(
    title: String,
    onBack: () -> Unit,
    imageRes: Int = R.drawable.pantohub_bg_play,
    content: @Composable ColumnScope.() -> Unit,
) {
    DorehamiPageFrame(
        title = title,
        onBack = onBack,
        containerColor = MaterialTheme.colorScheme.background,
        backgroundLayer = { PantohubBackdrop(imageRes = imageRes) },
        content = content,
    )
}

/**
 * Full-screen cheerful artwork with a light theme-colored scrim so text and cards stay readable.
 * The artwork lives in `res/drawable-nodpi` and is shared by every Pantohub screen.
 */
@Composable
internal fun PantohubBackdrop(
    imageRes: Int = R.drawable.pantohub_bg_play,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.4f

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            background.copy(alpha = if (dark) 0.55f else 0.22f),
                            background.copy(alpha = if (dark) 0.82f else 0.48f),
                        ),
                    ),
                ),
        )
        PantohubSparkles(modifier = Modifier.fillMaxSize())
    }
}

/**
 * Slow, looping confetti sparkles drifting over the artwork. The loop is seamless (integer speeds),
 * and the motion is frozen when the user turns animations off in Pantohub settings.
 */
@Composable
private fun PantohubSparkles(modifier: Modifier = Modifier) {
    val motion = LocalPantohubMotion.current
    val transition = rememberInfiniteTransition(label = "pantohubSparkles")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pantohubSparklePhase",
    )
    val phase = if (motion) animated else 0f
    val palette = remember {
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFFFE08A),
            Color(0xFFFFB3C7),
            Color(0xFF9EF0E4),
        )
    }
    Canvas(modifier = modifier) {
        for (i in 0 until 26) {
            val baseX = ((i * 53 + 17) % 100) / 100f
            val baseY = ((i * 29 + 7) % 100) / 100f
            val speed = 1 + (i % 3)                       // integer speeds keep the loop seamless
            val y = ((baseY - phase * speed) % 1f + 1f) % 1f
            val x = baseX + 0.025f * sin(2f * PI.toFloat() * phase * (1 + i % 2) + i)
            val radius = (1.6f + (i % 4) * 0.9f) * density
            val alpha = 0.3f + ((i * 7) % 5) / 10f
            drawCircle(
                color = palette[i % palette.size].copy(alpha = alpha),
                radius = radius,
                center = Offset(x * size.width, y * size.height),
            )
        }
    }
}
