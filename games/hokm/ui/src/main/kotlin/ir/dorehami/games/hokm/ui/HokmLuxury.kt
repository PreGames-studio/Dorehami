package ir.dorehami.games.hokm.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/** Motion level for the whole Hokm subtree. Defaults to the full effects if no provider is used. */
val LocalHokmMotion = staticCompositionLocalOf { HokmMotionLevel.FULL }

/** Supplies [level] to every luxury effect below it (menu, lobby and table share one setting). */
@Composable
fun HokmMotionProvider(level: HokmMotionLevel, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHokmMotion provides level, content = content)
}

/**
 * A quiet gold ring of micro-particles that fits any rectangular target.
 *
 * Used for the lobby Ready button and for ready players' avatar frames. With reduced motion the
 * dots stop rotating; with motion off only a static, readable outline remains. [burstToken]
 * triggers one short outward burst (increment it on tap instead of creating a new animation per
 * recomposition).
 */
@Composable
fun GoldParticleRing(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFFFC66D),
    dotCount: Int = 18,
    burstToken: Int = 0,
) {
    val level = LocalHokmMotion.current
    val scale = HokmCinematics.motionScale(level)
    val dots = HokmCinematics.particleCount(dotCount, level)
    val spin = rememberInfiniteTransition(label = "gold-ring")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "gold-ring-rotation",
    )
    val burst = remember { Animatable(1f) }
    LaunchedEffect(burstToken) {
        if (burstToken > 0 && scale > 0f) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(520, easing = LinearEasing))
        }
    }
    Canvas(modifier) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val pad = 2.dp.toPx()
        val rx = (size.width / 2f - pad).coerceAtLeast(1f)
        val ry = (size.height / 2f - pad).coerceAtLeast(1f)
        drawOval(
            color = color.copy(alpha = 0.26f + 0.14f * scale),
            topLeft = Offset(centre.x - rx, centre.y - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 2.dp.toPx()),
        )
        if (dots == 0 || scale <= 0f) return@Canvas
        val turn = if (level == HokmMotionLevel.FULL) rotation else 0f
        val dotRadius = (1.7f + 1.1f * scale).dp.toPx()
        for (i in 0 until dots) {
            val (dx, dy) = HokmCinematics.ringDot(i, dots, rx * 0.98f, ry * 0.98f, turn)
            val wave = 0.5f + 0.5f * sin((turn + i * 37f) * PI.toFloat() / 180f)
            drawCircle(
                color = color.copy(alpha = (0.32f + 0.5f * wave) * scale + 0.18f),
                radius = dotRadius,
                center = centre + Offset(dx, dy),
            )
        }
        if (burstToken > 0 && burst.value < 1f) {
            val p = burst.value
            for (i in 0 until dots) {
                val (dx, dy) = HokmCinematics.ringDot(i, dots, rx * (1f + p), ry * (1f + p), 0f)
                drawCircle(
                    color = color.copy(alpha = (1f - p) * scale),
                    radius = (3f * (1f - p) + 0.5f).dp.toPx(),
                    center = centre + Offset(dx, dy),
                )
            }
        }
    }
}

/**
 * Cinematic but static overhead light: a warm pool in the middle and a vignette at the rim.
 * It is a single Canvas with two gradients — no blur, no bloom pass, no extra frame cost.
 */
@Composable
fun SpotlightVignette(modifier: Modifier = Modifier, intensity: Float = 1f) {
    Canvas(modifier) {
        val centre = Offset(size.width / 2f, size.height * 0.44f)
        val radius = maxOf(size.width, size.height) * 0.72f
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color(0xFFFFE0B2).copy(alpha = 0.11f * intensity), Color.Transparent),
                center = centre,
                radius = radius,
            ),
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f * intensity)),
                center = centre,
                radius = radius,
            ),
        )
    }
}

/**
 * A moving gold-foil highlight for the flipped "Tak" (ace) card. Clip the parent to the card
 * shape; the sweep itself is transparent at both ends so the rank and suit stay readable.
 */
@Composable
fun GoldFoilGloss(modifier: Modifier = Modifier, progress: Float) {
    val strength = HokmCinematics.foilSweep(progress)
    Canvas(modifier) {
        val w = size.width
        val x = -w * 0.4f + w * 1.8f * progress.coerceIn(0f, 1f)
        val left = x - w * 0.35f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFFFE9A8).copy(alpha = 0.52f * strength),
                    Color.Transparent,
                ),
                startX = left,
                endX = left + w * 0.7f,
            ),
            topLeft = Offset(left, 0f),
            size = Size(w * 0.7f, size.height),
        )
    }
}

/** Compact digital trick counter shown next to each seat. Persian digits, TalkBack label. */
@Composable
fun TrickCounterChip(count: Int, modifier: Modifier = Modifier) {
    val label = HokmCinematics.faDigits(count)
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color(0xE6102A2B))
            .border(1.dp, Color(0xFFE7B85C).copy(alpha = 0.8f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics { contentDescription = "دست‌های برده: $label" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFFFFD98A), fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

/** Standard gravity (m/s²); keeps the sensor normalization free of device-specific constants. */
private const val STANDARD_GRAVITY = 9.80665f

/** A stable, never-changing sheen state for call sites without a sensor (previews, tests, backs). */
internal val NeutralSheenState: androidx.compose.runtime.State<HokmSheen> =
    mutableStateOf(HokmSheen.NEUTRAL)

/**
 * A subtle, tilt-driven specular highlight shared by the whole table.
 *
 * Uses the standard accelerometer — no permission and no new dependency — low-pass filters its
 * values and unregisters the listener on dispose. The returned [androidx.compose.runtime.State]
 * is read inside the card's draw phase, so sensor events redraw the cards without recomposing the
 * table fifty times a second. It stays exactly [HokmSheen.NEUTRAL] when motion is reduced/off,
 * when the sensor is missing, or when the device is in [HokmQualityLevel.LOW].
 */
@Composable
fun rememberTiltSheen(
    quality: HokmQualityLevel = HokmQualityLevel.STANDARD,
): androidx.compose.runtime.State<HokmSheen> {
    val level = LocalHokmMotion.current
    val context = LocalContext.current
    val state = remember { mutableStateOf(HokmSheen.NEUTRAL) }
    val scale = HokmCinematics.motionScale(level) * HokmReflection.sheenScale(quality)
    DisposableEffect(context, level, quality) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (level != HokmMotionLevel.FULL || scale <= 0f || manager == null || sensor == null) {
            state.value = HokmSheen.NEUTRAL
            onDispose { }
        } else {
            var smoothX = 0f
            var smoothY = 0f
            val listener = object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    if (values.size < 2) return
                    val rawX = (values[0] / STANDARD_GRAVITY).coerceIn(-1f, 1f)
                    val rawY = (values[1] / STANDARD_GRAVITY).coerceIn(-1f, 1f)
                    smoothX += (rawX - smoothX) * 0.12f
                    smoothY += (rawY - smoothY) * 0.12f
                    state.value = HokmReflection.sheen(smoothX, smoothY, scale)
                }
            }
            if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
                state.value = HokmSheen.NEUTRAL
            }
            onDispose {
                manager.unregisterListener(listener)
                state.value = HokmSheen.NEUTRAL
            }
        }
    }
    return state
}
