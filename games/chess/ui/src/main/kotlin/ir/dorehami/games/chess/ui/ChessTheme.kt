package ir.dorehami.games.chess.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CHESS_TYPE_SCALE = 1.15f

/** Vazirmatn (OFL) is bundled locally so Chess stays independent of the Pantohub module. */
private val ChessFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun TextStyle.chessScaled() = copy(
    fontFamily = ChessFontFamily,
    fontSize = if (fontSize.isSpecified) fontSize * CHESS_TYPE_SCALE else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * CHESS_TYPE_SCALE else lineHeight,
)

private fun chessTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.chessScaled(),
        displayMedium = base.displayMedium.chessScaled(),
        displaySmall = base.displaySmall.chessScaled(),
        headlineLarge = base.headlineLarge.chessScaled(),
        headlineMedium = base.headlineMedium.chessScaled(),
        headlineSmall = base.headlineSmall.chessScaled(),
        titleLarge = base.titleLarge.chessScaled(),
        titleMedium = base.titleMedium.chessScaled(),
        titleSmall = base.titleSmall.chessScaled(),
        bodyLarge = base.bodyLarge.chessScaled(),
        bodyMedium = base.bodyMedium.chessScaled(),
        bodySmall = base.bodySmall.chessScaled(),
        labelLarge = base.labelLarge.chessScaled(),
        labelMedium = base.labelMedium.chessScaled(),
        labelSmall = base.labelSmall.chessScaled(),
    )
}

private val ChessTypography = chessTypography()

private val ChessLightColors = lightColorScheme(
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

private val ChessDarkColors = darkColorScheme(
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

/** Chess-local Material theme matching Pantohub's system-aware color and typography language. */
@Composable
internal fun ChessTheme(content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) ChessDarkColors else ChessLightColors,
        typography = ChessTypography,
        content = content,
    )
}

/** Warm paper gradient with restrained, static confetti glows; no Pantohub photos are reused. */
@Composable
internal fun ChessBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    val sparkles = remember(colors) {
        listOf(colors.primaryContainer, colors.secondaryContainer, colors.tertiaryContainer, colors.surface)
    }
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(
                    colors.background,
                    colors.surfaceVariant.copy(alpha = if (dark) 0.55f else 0.48f),
                    colors.background,
                ),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val shortSide = size.minDimension
            val glows = listOf(
                Triple(Offset(size.width * 0.08f, size.height * 0.16f), colors.primary, 0.13f),
                Triple(Offset(size.width * 0.94f, size.height * 0.28f), colors.secondary, 0.12f),
                Triple(Offset(size.width * 0.86f, size.height * 0.92f), colors.tertiary, 0.10f),
            )
            glows.forEach { (center, color, alpha) ->
                val radius = shortSide * 0.40f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
            for (i in 0 until 24) {
                val x = ((i * 47 + 13) % 100) / 100f * size.width
                val y = ((i * 31 + 19) % 100) / 100f * size.height
                val alpha = if (dark) 0.17f else 0.24f
                drawCircle(
                    color = sparkles[i % sparkles.size].copy(alpha = alpha),
                    radius = (1.2f + (i % 3) * 0.75f) * density,
                    center = Offset(x, y),
                )
            }
        }
    }
}

/** A 56dp, spring-pressed gradient action in the same family as Pantohub's primary buttons. */
@Composable
internal fun ChessGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.4f
    val gradient = if (dark) {
        listOf(Color(0xFFFFD09B), Color(0xFFFFB86B))
    } else {
        listOf(Color(0xFFFF8A3D), Color(0xFFD9480F))
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "chessButtonScale",
    )
    val indication: Indication = LocalIndication.current
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(gradient))
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = scheme.onPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

internal enum class ChessButtonTone { Neutral, Primary, Secondary, Accent, Danger }

/** Rounded tonal control with clear semantics and high-contrast colors. */
@Composable
internal fun ChessTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: ChessButtonTone = ChessButtonTone.Neutral,
    minHeight: Int = 48,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        ChessButtonTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        ChessButtonTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ChessButtonTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ChessButtonTone.Accent -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        ChessButtonTone.Danger -> scheme.errorContainer to scheme.onErrorContainer
    }
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = minHeight.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) container else scheme.surfaceVariant.copy(alpha = 0.48f))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            color = if (enabled) content else scheme.onSurfaceVariant.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun chessOutlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

@Composable
internal fun chessSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = MaterialTheme.colorScheme.secondary,
    checkedBorderColor = Color.Transparent,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

internal fun chessSp(size: Int) = (size * CHESS_TYPE_SCALE).sp
