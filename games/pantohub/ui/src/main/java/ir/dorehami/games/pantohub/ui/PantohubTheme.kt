package ir.dorehami.games.pantohub.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import ir.dorehami.core.uikit.DorehamiPageFrame

/** A restrained parchment, deep-green, and antique-brass palette used only by Pantohub. */
@Composable
internal fun PantohubTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = remember(dark) {
        if (dark) {
            darkColorScheme(
                primary = Color(0xFFD8BE83),
                onPrimary = Color(0xFF2C2415),
                primaryContainer = Color(0xFF51452E),
                onPrimaryContainer = Color(0xFFF2E4C2),
                secondary = Color(0xFF9DB8A5),
                onSecondary = Color(0xFF1D3026),
                secondaryContainer = Color(0xFF34483A),
                onSecondaryContainer = Color(0xFFD8E8DA),
                tertiary = Color(0xFFD5A9A0),
                onTertiary = Color(0xFF3D2522),
                background = Color(0xFF1D211E),
                onBackground = Color(0xFFEAE5D9),
                surface = Color(0xFF252A26),
                onSurface = Color(0xFFEAE5D9),
                surfaceVariant = Color(0xFF353A34),
                onSurfaceVariant = Color(0xFFC7C2B5),
                outline = Color(0xFF8C8A7D),
                error = Color(0xFFFFB4A9),
                onError = Color(0xFF690005),
            )
        } else {
            lightColorScheme(
                primary = Color(0xFF31584D),
                onPrimary = Color(0xFFFFFFFF),
                primaryContainer = Color(0xFFDCE9DC),
                onPrimaryContainer = Color(0xFF17382F),
                secondary = Color(0xFF98733A),
                onSecondary = Color(0xFFFFFFFF),
                secondaryContainer = Color(0xFFF0E3C7),
                onSecondaryContainer = Color(0xFF4A381B),
                tertiary = Color(0xFF76524D),
                onTertiary = Color(0xFFFFFFFF),
                background = Color(0xFFF3EFDF),
                onBackground = Color(0xFF282820),
                surface = Color(0xFFFFFCF4),
                onSurface = Color(0xFF282820),
                surfaceVariant = Color(0xFFE8E1CF),
                onSurfaceVariant = Color(0xFF5C594D),
                outline = Color(0xFFB6A987),
                error = Color(0xFF9C3B32),
                onError = Color(0xFFFFFFFF),
            )
        }
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun PantohubPageFrame(
    title: String,
    onBack: () -> Unit,
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

/** Soft paper-like gradient and fine medallion lines; no artwork assets or global theme change. */
@Composable
internal fun PantohubBackdrop(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val variant = MaterialTheme.colorScheme.surfaceVariant
    val ornament = MaterialTheme.colorScheme.secondary
    val dark = background.luminance() < 0.4f
    val ornamentAlpha = if (dark) 0.13f else 0.18f

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(background, variant.copy(alpha = if (dark) 0.72f else 0.55f), background),
                startY = 0f,
                endY = size.height,
            ),
        )
        val lineWidth = 1.dp.toPx()
        val radiusUnit = size.minDimension
        val upperCenter = Offset(size.width * 1.015f, size.height * 0.055f)
        for (ring in 1..6) {
            drawCircle(
                color = ornament.copy(alpha = ornamentAlpha / (1f + ring * 0.12f)),
                radius = radiusUnit * (0.10f + ring * 0.045f),
                center = upperCenter,
                style = Stroke(width = lineWidth),
            )
        }
        val lowerCenter = Offset(size.width * -0.015f, size.height * 0.99f)
        for (ring in 1..4) {
            drawCircle(
                color = ornament.copy(alpha = ornamentAlpha * 0.62f),
                radius = radiusUnit * (0.08f + ring * 0.035f),
                center = lowerCenter,
                style = Stroke(width = lineWidth),
            )
        }
    }
}
