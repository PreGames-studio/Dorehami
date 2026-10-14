package ir.dorehami.games.hokm.ui

import android.animation.ValueAnimator
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text

/**
 * HK-013.a — whether the player has asked the *system* for reduced motion. Provided once at the
 * Hokm entry/table roots via [rememberHokmReducedMotion]; deep components only read it, so the
 * player's setting reaches every animation without threading a flag through every call site.
 * The decision itself is pure ([HokmMotion]); this file only reads the system signals.
 */
val LocalHokmReducedMotion = staticCompositionLocalOf { false }

/**
 * Reads the system reduced-motion signals once for this composition root:
 * `Settings.Global.ANIMATOR_DURATION_SCALE` and `ValueAnimator.areAnimatorsEnabled()`.
 * Read defensively — a provider that refuses the setting must never crash the table; it just
 * leaves normal motion on.
 */
@Composable
fun rememberHokmReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        HokmMotion.isReducedMotion(scale, ValueAnimator.areAnimatorsEnabled())
    }
}

/** A shared, high-contrast palette for the Hokm lobby, room and table. */
object HokmPalette {
    val night = Color(0xFF061913)
    val deepGreen = Color(0xFF09271F)
    val felt = Color(0xFF0D3B30)
    val emerald = Color(0xFF14936A)
    val mint = Color(0xFF75DAB4)
    val gold = Color(0xFFE8C779)
    val goldLight = Color(0xFFFFE9A8)
    val goldDark = Color(0xFF936825)
    val ivory = Color(0xFFF8F4E9)
    val text = Color(0xFFF8F5EA)
    val muted = Color(0xFFB7CCC1)
    val coral = Color(0xFFE47B69)
    val panel = Color(0xE6102D25)
    val panelLight = Color(0xFF19483A)
}

enum class HokmButtonTone { GOLD, EMERALD, GLASS, CORAL }

private data class ButtonPalette(
    val face: List<Color>,
    val base: Color,
    val outline: Color,
    val foreground: Color,
    val secondary: Color,
)

private fun buttonPalette(tone: HokmButtonTone): ButtonPalette = when (tone) {
    HokmButtonTone.GOLD -> ButtonPalette(
        face = listOf(Color(0xFFFFE9A8), Color(0xFFE4BC61), Color(0xFFC89135)),
        base = Color(0xFF79501F),
        outline = Color(0xFFFFF0C1),
        foreground = Color(0xFF18372B),
        secondary = Color(0xFF345042),
    )
    HokmButtonTone.EMERALD -> ButtonPalette(
        face = listOf(Color(0xFF39C697), Color(0xFF15966B), Color(0xFF08704F)),
        base = Color(0xFF06432F),
        outline = Color(0xFF8AE4BD),
        foreground = Color.White,
        secondary = Color(0xFFE0F5EA),
    )
    HokmButtonTone.GLASS -> ButtonPalette(
        face = listOf(Color(0xFF286052), Color(0xFF174638), Color(0xFF10372D)),
        base = Color(0xFF08251D),
        outline = Color(0x667DDFC0),
        foreground = HokmPalette.text,
        secondary = HokmPalette.muted,
    )
    HokmButtonTone.CORAL -> ButtonPalette(
        face = listOf(Color(0xFFF49A7E), Color(0xFFDA6B59), Color(0xFFB94A47)),
        base = Color(0xFF773332),
        outline = Color(0xFFFFC0A4),
        foreground = Color.White,
        secondary = Color(0xFFFFE8DF),
    )
}

/**
 * Subtle, animated felt backdrop. It is deliberately drawn in Compose so it stays crisp and adds
 * no network, font or image dependency to the game.
 */
@Composable
fun HokmBackdrop(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF0B3529), HokmPalette.night, Color(0xFF07150F)),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 46.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.018f),
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                x += step
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3327C48B), Color.Transparent),
                    center = Offset(size.width * 0.94f, size.height * 0.08f),
                    radius = size.maxDimension * 0.74f,
                ),
                radius = size.maxDimension * 0.74f,
                center = Offset(size.width * 0.94f, size.height * 0.08f),
            )
            drawCircle(
                color = HokmPalette.gold.copy(alpha = 0.055f),
                radius = size.width * 0.26f,
                center = Offset(size.width * 0.08f, size.height * 0.78f),
                style = Stroke(width = 1.dp.toPx()),
            )
            drawCircle(
                color = HokmPalette.gold.copy(alpha = 0.035f),
                radius = size.width * 0.19f,
                center = Offset(size.width * 0.08f, size.height * 0.78f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Raised glass panel used for menus and lobby cards. */
@Composable
fun HokmGlassPanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(12.dp, RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xEE174438), Color(0xF20B251E))))
            .border(1.dp, Color(0x667CDBB4), RoundedCornerShape(22.dp))
            .padding(contentPadding),
        content = content,
    )
}

/**
 * A tactile button with a visible lower lip, reflective gradient and a small press-in animation.
 * Keep this component for every game action so the whole Hokm flow has one visual language.
 */
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
    val shape = RoundedCornerShape(17.dp)
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val reducedMotion = LocalHokmReducedMotion.current
    val pressDepth by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = tween(HokmMotion.durationMs(125, reducedMotion), easing = FastOutSlowInEasing),
        label = "hokm-button-press",
    )
    val foreground = textColor ?: palette.foreground
    val mutedForeground = if (textColor != null) textColor.copy(alpha = 0.76f) else palette.secondary
    val horizontalPadding = when {
        height < 48.dp -> 6.dp
        height < 70.dp -> 9.dp
        else -> 13.dp
    }

    // HK-013.a: `height` is a *minimum*, not a cage — at fontScale 200% the label may need more
    // room than the design height, and the button must grow instead of clipping its text.
    Box(
        modifier = modifier
            .heightIn(min = height + 5.dp)
            .alpha(if (enabled) 1f else 0.58f),
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(min = height)
                .clip(shape)
                .background(palette.base),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .heightIn(min = height)
                .offset(y = (pressDepth * 4f).dp)
                .shadow((8f * (1f - pressDepth)).dp, shape)
                .clip(shape)
                .background(Brush.verticalGradient(palette.face))
                .border(1.dp, palette.outline, shape)
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

/** Wrapper that keeps a background beneath a group of cards without making every panel opaque. */
@Composable
fun HokmInsetPanel(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x66112620))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .padding(14.dp),
        content = content,
    )
}
