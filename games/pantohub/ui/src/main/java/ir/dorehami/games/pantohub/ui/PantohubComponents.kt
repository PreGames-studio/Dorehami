package ir.dorehami.games.pantohub.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldColors
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

/**
 * Visual families for [PantohubButton], following the design system:
 * Primary (orange) is the one main action of a screen, Secondary (teal outline) supports it,
 * Accent (violet) carries brand-flavored navigation. They never compete with each other.
 */
enum class PantohubButtonStyle { Primary, Secondary, Accent }

private data class PantohubButtonPalette(val gradient: List<Color>, val content: Color, val border: Color)

private fun paletteFor(style: PantohubButtonStyle): PantohubButtonPalette = when (style) {
    PantohubButtonStyle.Primary -> PantohubButtonPalette(
        gradient = listOf(PantohubDesign.Orange, PantohubDesign.OrangeDeep),
        content = Color.White,
        border = Color.White.copy(alpha = 0.22f),
    )
    PantohubButtonStyle.Secondary -> PantohubButtonPalette(
        gradient = listOf(PantohubDesign.SurfaceSoft, PantohubDesign.SurfaceRaised),
        content = PantohubDesign.Teal,
        border = PantohubDesign.Teal.copy(alpha = 0.65f),
    )
    PantohubButtonStyle.Accent -> PantohubButtonPalette(
        gradient = listOf(PantohubDesign.Violet, PantohubDesign.VioletDeep),
        content = Color.White,
        border = Color.White.copy(alpha = 0.20f),
    )
}

/**
 * Shared pill button: fits its label (no fixed height), squishes gently when pressed,
 * and keeps a ripple for accessibility. The squish is skipped when animations are off.
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
        targetValue = if (pressed && motion && enabled) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pantohubButtonScale",
    )
    val indication: Indication = LocalIndication.current
    val shape = RoundedCornerShape(PantohubDesign.Radius.Button)

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .clip(shape)
            .background(Brush.horizontalGradient(palette.gradient))
            .border(1.dp, palette.border, shape)
            .heightIn(min = 54.dp)
            .clickable(
                interactionSource = interaction,
                indication = indication,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (emoji != null) {
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
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

/**
 * Accent families for form fields and chips. On the navy identity, every tint is a deep,
 * calm surface wash with a single clear accent — readable text always wins over decoration.
 */
enum class PantohubTint(val container: Color, val accent: Color) {
    Mint(container = Color(0xFF173B3A), accent = PantohubDesign.Teal),
    Peach(container = Color(0xFF3B2340), accent = PantohubDesign.Violet),
    Lavender(container = Color(0xFF2C2A55), accent = Color(0xFFA78BFA)),
    Butter(container = Color(0xFF3A2E17), accent = PantohubDesign.Warning),
}

/** Colors for `OutlinedTextField`: a deep tinted box with a clear accent border/label. */
@Composable
internal fun pantohubFieldColors(tint: PantohubTint = PantohubTint.Mint): TextFieldColors {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = onSurface,
        unfocusedTextColor = onSurface,
        focusedContainerColor = tint.container,
        unfocusedContainerColor = tint.container.copy(alpha = 0.75f),
        disabledContainerColor = tint.container.copy(alpha = 0.45f),
        focusedBorderColor = tint.accent,
        unfocusedBorderColor = tint.accent.copy(alpha = 0.45f),
        focusedLabelColor = tint.accent,
        unfocusedLabelColor = secondary,
        cursorColor = tint.accent,
    )
}

/** Colors for `FilterChip`: calm surface when idle, solid violet with white text when selected. */
@Composable
internal fun pantohubChipColors(tint: PantohubTint = PantohubTint.Mint) =
    FilterChipDefaults.filterChipColors(
        containerColor = PantohubDesign.SurfaceRaised,
        labelColor = PantohubDesign.TextSecondary,
        iconColor = PantohubDesign.TextSecondary,
        selectedContainerColor = PantohubDesign.Violet,
        selectedLabelColor = Color.White,
        selectedLeadingIconColor = Color.White,
        selectedTrailingIconColor = Color.White,
    )

/** Colors for `Switch`: teal when on, muted navy track with a warm thumb edge when off. */
@Composable
internal fun pantohubSwitchColors() =
    SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = PantohubDesign.TealDeep,
        checkedBorderColor = Color.Transparent,
        checkedIconColor = PantohubDesign.Teal,
        uncheckedThumbColor = PantohubDesign.TextSecondary,
        uncheckedTrackColor = PantohubDesign.SurfaceSoft,
        uncheckedBorderColor = PantohubDesign.Outline,
        uncheckedIconColor = PantohubDesign.Surface,
    )
