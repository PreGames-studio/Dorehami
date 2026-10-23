package ir.dorehami.games.hokm.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semantic aliases kept local to Hokm; every value follows the app's current Material theme. */
object HokmPalette {
    val night: Color @Composable get() = MaterialTheme.colorScheme.background
    val deepGreen: Color @Composable get() = MaterialTheme.colorScheme.surface
    val felt: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val emerald: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val mint: Color @Composable get() = MaterialTheme.colorScheme.secondary
    val gold: Color @Composable get() = MaterialTheme.colorScheme.primary
    val goldLight: Color @Composable get() = MaterialTheme.colorScheme.primary
    val goldDark: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
    val ivory: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val text: Color @Composable get() = MaterialTheme.colorScheme.onSurface
    val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val coral: Color @Composable get() = MaterialTheme.colorScheme.error
    val panel: Color @Composable get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
    val panelLight: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
}

enum class HokmButtonTone { GOLD, EMERALD, GLASS, CORAL }

private data class ButtonPalette(
    val face: List<Color>,
    val outline: Color,
    val foreground: Color,
    val secondary: Color,
)

@Composable
private fun buttonPalette(tone: HokmButtonTone): ButtonPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.4f
    return when (tone) {
        HokmButtonTone.GOLD -> ButtonPalette(
            face = if (dark) listOf(Color(0xFFFFD09B), scheme.primary)
            else listOf(Color(0xFFFF8A3D), scheme.primary),
            outline = scheme.primary,
            foreground = scheme.onPrimary,
            secondary = scheme.onPrimary.copy(alpha = 0.82f),
        )
        HokmButtonTone.EMERALD -> ButtonPalette(
            face = if (dark) listOf(Color(0xFFA5EEE3), scheme.secondary)
            else listOf(Color(0xFF43BDB2), scheme.secondary),
            outline = scheme.secondary,
            foreground = scheme.onSecondary,
            secondary = scheme.onSecondary.copy(alpha = 0.82f),
        )
        HokmButtonTone.GLASS -> ButtonPalette(
            face = listOf(scheme.surface.copy(alpha = 0.94f), scheme.surfaceVariant.copy(alpha = 0.86f)),
            outline = scheme.outline.copy(alpha = 0.78f),
            foreground = scheme.onSurface,
            secondary = scheme.onSurfaceVariant,
        )
        HokmButtonTone.CORAL -> ButtonPalette(
            face = listOf(scheme.error.copy(alpha = 0.88f), scheme.error),
            outline = scheme.error,
            foreground = scheme.onError,
            secondary = scheme.onError.copy(alpha = 0.82f),
        )
    }
}

/** Hokm artwork with the same warm scrim and orange/teal/purple glows as the app shell. */
@Composable
fun HokmBackdrop(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    Box(modifier = modifier) {
        Image(
            painter = painterResource(R.drawable.hokm_backdrop),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background.copy(alpha = if (dark) 0.52f else 0.16f),
                            colors.background.copy(alpha = if (dark) 0.38f else 0.14f),
                            colors.background.copy(alpha = if (dark) 0.66f else 0.36f),
                        ),
                    ),
                ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * 0.43f
            val glows = listOf(
                Triple(Offset(size.width * 0.02f, size.height * 0.20f), colors.primary, 0.11f),
                Triple(Offset(size.width * 0.98f, size.height * 0.60f), colors.secondary, 0.10f),
                Triple(Offset(size.width * 0.72f, size.height * 1.02f), colors.tertiary, 0.09f),
            )
            glows.forEach { (center, color, alpha) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    center = center,
                    radius = radius,
                )
            }
        }
    }
}

/** Lightly tinted glass panel, shared by the setup flow, room lobby and table overlays. */
@Composable
fun HokmGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(shape)
            .background(scheme.surface.copy(alpha = 0.86f))
            .border(1.dp, scheme.outline.copy(alpha = 0.68f), shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Rounded tonal action with a short press response and the app's button geometry. */
@Composable
fun Hokm3DButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: String? = null,
    trailing: String? = null,
    height: Dp = 54.dp,
    tone: HokmButtonTone = HokmButtonTone.GOLD,
    enabled: Boolean = true,
    textColor: Color? = null,
    leadingColor: Color? = null,
    onSound: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val palette = buttonPalette(tone)
    val shape = RoundedCornerShape(19.dp)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val pressDepth by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(125, easing = FastOutSlowInEasing),
        label = "hokm-button-press",
    )
    val foreground = textColor ?: palette.foreground
    val mutedForeground = if (textColor != null) textColor.copy(alpha = 0.76f) else palette.secondary
    val horizontalPadding = when {
        height < 48.dp -> 6.dp
        height < 70.dp -> 9.dp
        else -> 13.dp
    }

    Row(
        modifier = modifier
            .height(height)
            .offset(y = (pressDepth * 1.5f).dp)
            .alpha(if (enabled) 1f else 0.52f)
            .shadow(if (enabled) 4.dp else 0.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(palette.face))
            .border(1.dp, palette.outline.copy(alpha = 0.72f), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = text,
            ) {
                onSound?.invoke()
                onClick()
            }
            .padding(horizontal = horizontalPadding, vertical = 5.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(
                text = leading,
                color = leadingColor ?: foreground,
                fontSize = if (height < 48.dp) 18.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            Text(
                text = text,
                color = foreground,
                fontSize = if (height < 48.dp) 13.sp else 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = mutedForeground,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Text(
                text = trailing,
                color = foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Small decorative chip for status or settings summaries. */
@Composable
fun HokmPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    color: Color = HokmPalette.gold,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.34f), RoundedCornerShape(50))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Text(icon, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Quiet inset surface used for secondary information, not a second competing panel style. */
@Composable
fun HokmInsetPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.58f))
            .border(1.dp, scheme.outline.copy(alpha = 0.38f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        content = content,
    )
}
