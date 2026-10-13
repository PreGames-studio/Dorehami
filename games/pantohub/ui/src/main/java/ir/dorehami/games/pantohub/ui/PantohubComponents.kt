package ir.dorehami.games.pantohub.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Visual families for [PantohubButton]; each uses a warm gradient with high-contrast text. */
enum class PantohubButtonStyle { Primary, Secondary, Accent }

private data class PantohubButtonPalette(val gradient: List<Color>, val content: Color)

private fun paletteFor(style: PantohubButtonStyle): PantohubButtonPalette = when (style) {
    PantohubButtonStyle.Primary -> PantohubButtonPalette(
        gradient = listOf(Color(0xFFFF8A3D), Color(0xFFD9480F)),
        content = Color(0xFFFFFFFF),
    )
    PantohubButtonStyle.Secondary -> PantohubButtonPalette(
        gradient = listOf(Color(0xFF2BB5A8), Color(0xFF0E8F87)),
        content = Color(0xFFFFFFFF),
    )
    PantohubButtonStyle.Accent -> PantohubButtonPalette(
        gradient = listOf(Color(0xFFB07BFF), Color(0xFF7B4CC2)),
        content = Color(0xFFFFFFFF),
    )
}

/**
 * Playful, flexible button: a gradient pill that grows to fit its label (no fixed height),
 * squishes with a springy press animation, and keeps a ripple for accessibility.
 * The squish is skipped when the user has turned animations off.
 */
@Composable
internal fun PantohubButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emoji: String? = null,
    style: PantohubButtonStyle = PantohubButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val motion = LocalPantohubMotion.current
    val palette = remember(style) { paletteFor(style) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion && enabled) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pantohubButtonScale",
    )
    val indication: Indication = LocalIndication.current

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.5f
            }
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .background(Brush.horizontalGradient(palette.gradient))
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (emoji != null) {
                Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = palette.content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}
