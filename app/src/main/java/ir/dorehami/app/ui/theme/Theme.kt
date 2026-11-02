package ir.dorehami.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.font.FontWeight
import ir.dorehami.app.R

private val DorehamiFontFamily = FontFamily(
    Font(R.font.dorehami_vazirmatn_regular, FontWeight.Normal),
    Font(R.font.dorehami_vazirmatn_medium, FontWeight.Medium),
    Font(R.font.dorehami_vazirmatn_bold, FontWeight.Bold),
)

/**
 * The app-wide readable scale. Every game module (chess, Hokm, Pantohub, backgammon) already uses
 * 1.15× Vazirmatn, so the shell uses the same number — one type scale for the whole app instead of
 * a slightly smaller home screen than the games it opens.
 */
private const val DOREHAMI_TYPE_SCALE = 1.15f

private fun TextStyle.withDorehamiFont() = copy(
    fontFamily = DorehamiFontFamily,
    fontSize = if (fontSize.isSpecified) fontSize * DOREHAMI_TYPE_SCALE else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * DOREHAMI_TYPE_SCALE else lineHeight,
)

private fun dorehamiTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.withDorehamiFont(),
        displayMedium = base.displayMedium.withDorehamiFont(),
        displaySmall = base.displaySmall.withDorehamiFont(),
        headlineLarge = base.headlineLarge.withDorehamiFont(),
        headlineMedium = base.headlineMedium.withDorehamiFont(),
        headlineSmall = base.headlineSmall.withDorehamiFont(),
        titleLarge = base.titleLarge.withDorehamiFont(),
        titleMedium = base.titleMedium.withDorehamiFont(),
        titleSmall = base.titleSmall.withDorehamiFont(),
        bodyLarge = base.bodyLarge.withDorehamiFont(),
        bodyMedium = base.bodyMedium.withDorehamiFont(),
        bodySmall = base.bodySmall.withDorehamiFont(),
        labelLarge = base.labelLarge.withDorehamiFont(),
        labelMedium = base.labelMedium.withDorehamiFont(),
        labelSmall = base.labelSmall.withDorehamiFont(),
    )
}

private val DorehamiTypography = dorehamiTypography()

private val LightColors = lightColorScheme(
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
    tertiaryContainer = Color(0xFFEEE7FF),
    onTertiaryContainer = Color(0xFF3A1A6B),
    background = Color(0xFFFFF4DC),
    onBackground = Color(0xFF3A2A12),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2E2415),
    surfaceVariant = Color(0xFFFFE9C2),
    onSurfaceVariant = Color(0xFF5B4B33),
    outline = Color(0xFFC9A96B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBDD),
    onErrorContainer = Color(0xFF7F1D1D),
)

private val DarkColors = darkColorScheme(
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
    tertiaryContainer = Color(0xFF4D356C),
    onTertiaryContainer = Color(0xFFE9D7FF),
    background = Color(0xFF1E1A26),
    onBackground = Color(0xFFF4EEFA),
    surface = Color(0xFF2A2433),
    onSurface = Color(0xFFF4EEFA),
    surfaceVariant = Color(0xFF3A3346),
    onSurfaceVariant = Color(0xFFD2C8DC),
    outline = Color(0xFF9A90A8),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF690005),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun DorehamiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = DorehamiTypography,
        content = content,
    )
}
