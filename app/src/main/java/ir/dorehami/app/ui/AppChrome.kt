package ir.dorehami.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** Shared illustrated backdrop for the app shell; game modules keep their own backdrops. */
@Composable
internal fun DorehamiBackdrop(imageRes: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    val transition = rememberInfiniteTransition(label = "dorehamiBackdrop")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dorehamiBackdropPhase",
    )
    val drift = sin(phase * 2f * PI.toFloat())

    Box(modifier = modifier) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val zoom = 1.025f + 0.012f * drift
                    scaleX = zoom
                    scaleY = zoom
                    rotationZ = 0.18f * drift
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background.copy(alpha = if (dark) 0.48f else 0.14f),
                            colors.background.copy(alpha = if (dark) 0.36f else 0.12f),
                            colors.background.copy(alpha = if (dark) 0.62f else 0.32f),
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
            glows.forEachIndexed { index, (origin, color, alpha) ->
                val sway = sin(phase * 2f * PI.toFloat() + index) * size.minDimension * 0.018f
                val center = Offset(origin.x + sway, origin.y - sway * 0.35f)
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

/** Lightly tinted glass panel so the bespoke artwork remains visible underneath. */
@Composable
internal fun AppGlassPanel(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface.copy(alpha = if (dark) 0.84f else 0.76f))
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = if (dark) 0.68f else 0.74f),
                shape = shape,
            ),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DorehamiTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            TextButton(onClick = onBack) {
                Text(
                    text = "بازگشت ←",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
