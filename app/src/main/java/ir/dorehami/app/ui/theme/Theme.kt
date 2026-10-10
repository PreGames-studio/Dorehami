package ir.dorehami.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D32),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7E4B9),
    onPrimaryContainer = Color(0xFF0B3D10),
    secondary = Color(0xFF00695C),
    onSecondary = Color.White,
    background = Color(0xFFF7FBF4),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF7FBF4),
    onSurface = Color(0xFF1A1C1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD694),
    onPrimary = Color(0xFF0B3D10),
    primaryContainer = Color(0xFF2E7D32),
    onPrimaryContainer = Color(0xFFB7E4B9),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00332C),
)

@Composable
fun DorehamiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
