package ir.dorehami.games.pantohub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import ir.dorehami.core.uikit.DorehamiPageFrame

/** A bright, playful palette for Pantohub: sunny orange, lively teal, and warm paper tones. */
@Composable
internal fun PantohubTheme(content: @Composable () -> Unit) {
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
    MaterialTheme(colorScheme = colors, content = content)
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
    }
}
