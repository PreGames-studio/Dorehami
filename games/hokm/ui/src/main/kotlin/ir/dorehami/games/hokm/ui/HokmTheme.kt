package ir.dorehami.games.hokm.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.isSpecified

private const val HOKM_TYPE_SCALE = 1.15f

/** The same bundled Persian typeface used by the app shell and the other game modules. */
private val HokmFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.hokmStyle() = copy(
    fontFamily = HokmFontFamily,
    fontSize = if (fontSize.isSpecified) fontSize * HOKM_TYPE_SCALE else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * HOKM_TYPE_SCALE else lineHeight,
)

private fun hokmTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.hokmStyle(),
        displayMedium = base.displayMedium.hokmStyle(),
        displaySmall = base.displaySmall.hokmStyle(),
        headlineLarge = base.headlineLarge.hokmStyle(),
        headlineMedium = base.headlineMedium.hokmStyle(),
        headlineSmall = base.headlineSmall.hokmStyle(),
        titleLarge = base.titleLarge.hokmStyle(),
        titleMedium = base.titleMedium.hokmStyle(),
        titleSmall = base.titleSmall.hokmStyle(),
        bodyLarge = base.bodyLarge.hokmStyle(),
        bodyMedium = base.bodyMedium.hokmStyle(),
        bodySmall = base.bodySmall.hokmStyle(),
        labelLarge = base.labelLarge.hokmStyle(),
        labelMedium = base.labelMedium.hokmStyle(),
        labelSmall = base.labelSmall.hokmStyle(),
    )
}

private val HokmTypography = hokmTypography()

/**
 * The app's own light scheme, value for value — the same cream paper, white cards, burnt orange,
 * teal, violet and gold hairline the shell, chess and backgammon use. Hokm used to run a night-only
 * aubergine scheme, which made it the one screen that did not look like the rest of the app.
 */
private val HokmLightColors = lightColorScheme(
    primary = Color(0xFFD9480F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE9C2),
    onPrimaryContainer = Color(0xFF3B2000),
    secondary = Color(0xFF0E8F87),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC7F0EB),
    onSecondaryContainer = Color(0xFF053F3B),
    tertiary = Color(0xFF7B4CC2),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEADBFF),
    onTertiaryContainer = Color(0xFF2C1057),
    background = Color(0xFFFFF4DC),
    onBackground = Color(0xFF2E2415),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2E2415),
    surfaceVariant = Color(0xFFFFEED2),
    onSurfaceVariant = Color(0xFF5B4B33),
    outline = Color(0xFFC9A96B),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * Hokm-local wrapper around the app's shared palette and Vazirmatn type scale.
 *
 * One scheme, always light, exactly like the shell, chess and backgammon: a cream page, white glass
 * cards and orange/teal/violet accents. The table itself is warm wood and linen, so it belongs to
 * the same material family instead of being the one dark screen in the app.
 */
@Composable
internal fun HokmTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HokmLightColors,
        typography = HokmTypography,
        content = content,
    )
}
