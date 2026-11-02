package ir.dorehami.games.backgammon.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import ir.dorehami.games.backgammon.BackgammonPosition
import ir.dorehami.games.backgammon.BackgammonRules
import kotlin.math.min

/**
 * Geometry of one 2D board, shared by drawing and hit-testing so a tap can never disagree with what
 * the player sees.
 *
 * Layout (standard table seen from white's side): points 1..12 on the bottom row, 13..24 on the top
 * row, the bar in the middle. White homes on the bottom-right (points 1..6), black on the top-right
 * (points 19..24).
 */
internal class BackgammonGeometry(val width: Float, val height: Float) {

    val bar: Float = width * 0.052f
    val half: Float = (width - bar) / 2f
    val cell: Float = half / 6f
    val depth: Float = height * 0.44f

    fun isTop(index: Int): Boolean = index >= 12

    fun leftOf(index: Int): Float = when {
        // Near half (white's home board, points 1..6): point 1 sits in the corner, 6 by the bar.
        index in 0..5 -> half + bar + (5 - index) * cell
        index in 6..11 -> (11 - index) * cell
        index in 12..17 -> (index - 12) * cell
        // Far half (black's home board, points 19..24): point 24 sits above point 1, 19 by the bar.
        else -> half + bar + (index - 18) * cell
    }

    /** Index under a touch, or [BackgammonRules.FROM_BAR] for the bar; `null` outside the board. */
    fun hit(x: Float, y: Float): Int? {
        if (x < 0f || y < 0f || x > width || y > height) return null
        if (x >= half && x <= half + bar) return BackgammonRules.FROM_BAR
        val top = y < height / 2f
        val rightHalf = x > half + bar
        val rawColumn = if (rightHalf) (x - this.half - bar) / cell else x / cell
        val column = rawColumn.toInt().coerceIn(0, 5)
        return when {
            !top && rightHalf -> 5 - column
            !top -> 11 - column
            top && !rightHalf -> 12 + column
            else -> 18 + column
        }
    }
}

/** A checker that slides from one point to another: the drawn board already shows the result. */
internal data class CheckerMove(val from: Int, val to: Int, val white: Boolean, val stamp: Long)

/**
 * The backgammon board: 24 points, the bar and both checker colours.
 *
 * Rendering is one canvas with the design taken from [LocalBackgammonLook]: wood gradients, inlaid
 * frame, glossy points, bevelled checkers and a soft brass shadow under each stack. The two
 * animations (the selected checker lifting, the hints breathing) are read in the draw phase only, so
 * a moving board never triggers recomposition or a re-layout.
 */
@Composable
fun BackgammonBoard(
    board: BackgammonPosition,
    selectedPoint: Int,
    hints: Set<Int>,
    enabled: Boolean,
    onPoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
    moving: CheckerMove? = null,
    animateEntrance: Boolean = true,
) {
    val look = LocalBackgammonLook.current
    val lift = remember { Animatable(0f) }
    LaunchedEffect(selectedPoint) {
        if (selectedPoint >= 0) {
            lift.snapTo(0f)
            lift.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
        } else {
            lift.snapTo(0f)
        }
    }
    val pulse = rememberInfiniteTransition(label = "hint-pulse")
    val pulseValue by pulse.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "hint-pulse-value",
    )
    val slide = remember(moving?.stamp) { Animatable(if (moving == null) 1f else 0f) }
    LaunchedEffect(moving?.stamp) {
        if (moving != null) {
            slide.snapTo(0f)
            slide.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        } else {
            slide.snapTo(1f)
        }
    }
    val entrance = remember { Animatable(if (animateEntrance) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animateEntrance) entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }

    Canvas(
        modifier
            .fillMaxSize()
            .aspectRatio(1.42f)
            .pointerInput(board, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val geometry = BackgammonGeometry(size.width.toFloat(), size.height.toFloat())
                    geometry.hit(offset.x, offset.y)?.let(onPoint)
                }
            },
    ) {
        val geometry = BackgammonGeometry(size.width, size.height)
        drawBoardFace(geometry, look.board)

        // A soft entrance: the board settles in from 96% instead of popping in.
        val e = entrance.value
        rotate(0f) {
            for (index in 0 until BackgammonRules.POINT_COUNT) {
                val count = board.points[index]
                if (count == 0) continue
                drawStack(
                    geometry = geometry,
                    index = index,
                    count = kotlin.math.abs(count),
                    white = count > 0,
                    selected = index == selectedPoint,
                    lift = lift.value,
                    pieces = look.pieces,
                    scale = 0.96f + 0.04f * e,
                )
            }
        }

        // Bar checkers wait at the middle; white stacks from the bottom, black from the top.
        drawBarStack(geometry, board.barWhite, white = true, selected = selectedPoint == BackgammonRules.FROM_BAR, lift = lift.value, pieces = look.pieces)
        drawBarStack(geometry, board.barBlack, white = false, selected = false, lift = 0f, pieces = look.pieces)

        // The checker currently sliding: drawn on top so the eye follows it.
        val move = moving
        if (move != null && slide.value < 1f) {
            val from = checkerCentre(geometry, move.from, count = kotlin.math.abs(board.points.getOrElse(move.from) { 0 }) + 1, white = move.white)
            val to = checkerCentre(geometry, move.to, count = kotlin.math.abs(board.points.getOrElse(move.to) { 0 }), white = move.white)
            val t = slide.value
            val arc = kotlin.math.sin(t * Math.PI).toFloat() * geometry.cell * 0.35f
            val x = from.first + (to.first - from.first) * t
            val y = from.second + (to.second - from.second) * t - arc
            drawChecker(
                centreX = x,
                centreY = y,
                radius = checkerRadius(geometry, 1),
                white = move.white,
                highlighted = false,
                lift = 0.35f,
                pieces = look.pieces,
            )
        }

        hints.forEach { hint ->
            val alpha = 0.55f + 0.45f * pulseValue
            if (hint == BackgammonRules.FROM_BAR) {
                drawCircle(
                    color = look.board.accent.copy(alpha = alpha),
                    radius = geometry.bar * 0.34f,
                    center = Offset(geometry.half + geometry.bar / 2f, size.height * 0.5f),
                    style = Stroke(width = geometry.bar * 0.10f),
                )
            } else {
                val cx = geometry.leftOf(hint) + geometry.cell / 2f
                val cy = if (geometry.isTop(hint)) size.height * 0.10f else size.height * 0.90f
                drawCircle(
                    color = look.board.accent.copy(alpha = alpha),
                    radius = geometry.cell * 0.13f,
                    center = Offset(cx, cy),
                    style = Stroke(width = geometry.cell * 0.05f),
                )
                drawCircle(
                    color = look.board.accent.copy(alpha = alpha * 0.22f),
                    radius = geometry.cell * 0.11f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

/** Centre of the slot a checker of [count] at [index] occupies (used by the slide animation). */
private fun checkerCentre(geometry: BackgammonGeometry, index: Int, count: Int, white: Boolean): Pair<Float, Float> {
    if (index == BackgammonRules.FROM_BAR) {
        return geometry.half + geometry.bar / 2f to geometry.height / 2f
    }
    if (index !in 0 until BackgammonRules.POINT_COUNT) {
        // Borne off: slide out toward the player's own tray.
        val y = if (white) geometry.height * 1.04f else -geometry.height * 0.04f
        return geometry.width / 2f to y
    }
    val cx = geometry.leftOf(index) + geometry.cell / 2f
    val slot = (count - 1).coerceAtLeast(0)
    val radius = checkerRadius(geometry, count)
    val gap = radius * 0.22f
    val cy = if (geometry.isTop(index)) {
        radius + 6f + slot * (radius * 2f + gap)
    } else {
        geometry.height - radius - 6f - slot * (radius * 2f + gap)
    }
    return cx to cy
}

private fun checkerRadius(geometry: BackgammonGeometry, count: Int): Float =
    min(geometry.cell * 0.36f, (geometry.depth - 10f) / (count * 2.05f)).coerceAtLeast(5f)

private fun DrawScope.drawBoardFace(geometry: BackgammonGeometry, skin: BackgammonBoardSkin) {
    val w = size.width
    val h = size.height
    val corner = CornerRadius(w * 0.035f)

    // Wood: a warm vertical gradient with a lighter inlay frame, like a real folding board.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(skin.woodLight, skin.woodDark, skin.woodLight)),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = skin.accent.copy(alpha = 0.55f),
        cornerRadius = corner,
        style = Stroke(width = w * 0.007f),
    )
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.20f),
        topLeft = Offset(w * 0.012f, h * 0.012f),
        size = Size(w * 0.976f, h * 0.976f),
        cornerRadius = corner,
        style = Stroke(width = w * 0.004f),
    )

    // Points: glossy triangles with a highlight down the middle and a dark seam between neighbours.
    for (index in 0 until BackgammonRules.POINT_COUNT) {
        val left = geometry.leftOf(index)
        val top = geometry.isTop(index)
        val base = if (index % 2 == 0) skin.pointLight else skin.pointDark
        val tipY = if (top) geometry.depth else h - geometry.depth
        val baseY = if (top) geometry.insideTop() else h - geometry.insideTop()
        val path = Path().apply {
            if (top) {
                moveTo(left, baseY)
                lineTo(left + geometry.cell, baseY)
                lineTo(left + geometry.cell / 2f, tipY)
            } else {
                moveTo(left, baseY)
                lineTo(left + geometry.cell, baseY)
                lineTo(left + geometry.cell / 2f, tipY)
            }
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(
                    base.copy(alpha = 0.98f),
                    base.copy(alpha = 0.82f),
                ),
            ),
        )
        // Central highlight: the lacquered look of a wooden point.
        drawPath(
            path = Path().apply {
                if (top) {
                    moveTo(left + geometry.cell * 0.36f, baseY)
                    lineTo(left + geometry.cell * 0.64f, baseY)
                    lineTo(left + geometry.cell / 2f, tipY)
                } else {
                    moveTo(left + geometry.cell * 0.36f, baseY)
                    lineTo(left + geometry.cell * 0.64f, baseY)
                    lineTo(left + geometry.cell / 2f, tipY)
                }
                close()
            },
            color = Color.White.copy(alpha = 0.10f),
        )
        drawLine(
            color = Color.Black.copy(alpha = 0.18f),
            start = Offset(left, baseY),
            end = Offset(left + geometry.cell / 2f, tipY),
            strokeWidth = w * 0.002f,
        )
    }

    // The bar: a brass-inlaid strip with a subtle inner shadow.
    drawRect(
        brush = Brush.verticalGradient(listOf(skin.woodLight, skin.woodDark, skin.woodLight)),
        topLeft = Offset(geometry.half, 0f),
        size = Size(geometry.bar, h),
    )
    drawLine(
        color = skin.accent.copy(alpha = 0.65f),
        start = Offset(geometry.half, 0f),
        end = Offset(geometry.half, h),
        strokeWidth = w * 0.0035f,
    )
    drawLine(
        color = skin.accent.copy(alpha = 0.65f),
        start = Offset(geometry.half + geometry.bar, 0f),
        end = Offset(geometry.half + geometry.bar, h),
        strokeWidth = w * 0.0035f,
    )

    // Brass corner studs — the small detail that makes the board read as furniture.
    val stud = w * 0.012f
    listOf(
        Offset(w * 0.022f, h * 0.03f),
        Offset(w * 0.978f, h * 0.03f),
        Offset(w * 0.022f, h * 0.97f),
        Offset(w * 0.978f, h * 0.97f),
    ).forEach { centre ->
        drawCircle(skin.accent.copy(alpha = 0.7f), radius = stud, center = centre)
        drawCircle(Color.Black.copy(alpha = 0.25f), radius = stud * 0.45f, center = centre)
    }
}

/** Distance from the outer edge to where the points start (the wooden frame). */
private fun BackgammonGeometry.insideTop(): Float = height * 0.03f

private fun DrawScope.drawStack(
    geometry: BackgammonGeometry,
    index: Int,
    count: Int,
    white: Boolean,
    selected: Boolean,
    lift: Float,
    pieces: BackgammonPieceSkin,
    scale: Float,
) {
    val centreX = geometry.leftOf(index) + geometry.cell / 2f
    val radius = checkerRadius(geometry, count) * scale
    val gap = radius * 0.22f
    for (slot in 0 until count) {
        val isTop = slot == count - 1
        val selectedLift = if (selected && isTop) lift else 0f
        val centreY = if (geometry.isTop(index)) {
            radius + 6f + slot * (radius * 2f + gap) - selectedLift * radius * 0.45f
        } else {
            geometry.height - radius - 6f - slot * (radius * 2f + gap) + selectedLift * -radius * 0.45f
        }
        drawChecker(
            centreX = centreX,
            centreY = centreY,
            radius = radius,
            white = white,
            highlighted = selected && isTop,
            lift = selectedLift,
            pieces = pieces,
        )
    }
}

private fun DrawScope.drawBarStack(
    geometry: BackgammonGeometry,
    count: Int,
    white: Boolean,
    selected: Boolean,
    lift: Float,
    pieces: BackgammonPieceSkin,
) {
    if (count <= 0) return
    val centreX = geometry.half + geometry.bar / 2f
    val radius = min(geometry.bar * 0.40f, (size.height * 0.42f) / (count * 2.05f)).coerceAtLeast(4f)
    val gap = radius * 0.22f
    for (slot in 0 until count) {
        val isTop = slot == count - 1
        val selectedLift = if (selected && isTop) lift else 0f
        val centreY = if (white) {
            size.height / 2f + radius + slot * (radius * 2f + gap) + selectedLift * radius * 0.3f
        } else {
            size.height / 2f - radius - slot * (radius * 2f + gap) - selectedLift * radius * 0.3f
        }
        drawChecker(centreX, centreY, radius, white, selected && isTop, selectedLift, pieces)
    }
}

/**
 * One checker: contact shadow, domed face (radial gradient), milled rim, and a highlight arc. The
 * shape is a circle with a slight vertical squash so it reads as a disc lying on the board.
 */
private fun DrawScope.drawChecker(
    centreX: Float,
    centreY: Float,
    radius: Float,
    white: Boolean,
    highlighted: Boolean,
    lift: Float,
    pieces: BackgammonPieceSkin,
) {
    val face = if (white) pieces.whiteFace else pieces.blackFace
    val rim = if (white) pieces.whiteRim else pieces.blackRim
    val shadowShift = radius * (0.18f + 0.12f * lift)

    drawOval(
        color = Color.Black.copy(alpha = 0.38f - 0.12f * lift),
        topLeft = Offset(centreX - radius, centreY - radius * 0.92f + shadowShift),
        size = Size(radius * 2f, radius * 2f * 0.94f),
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(face, rim.copy(alpha = 0.95f)),
            center = Offset(centreX - radius * 0.3f, centreY - radius * 0.35f),
            radius = radius * 1.6f,
        ),
        radius = radius,
        center = Offset(centreX, centreY),
    )
    drawCircle(
        color = rim,
        radius = radius,
        center = Offset(centreX, centreY),
        style = Stroke(width = radius * 0.14f),
    )
    // Milled groove: the thin ring every tournament checker has.
    drawCircle(
        color = Color.Black.copy(alpha = 0.16f),
        radius = radius * 0.62f,
        center = Offset(centreX, centreY),
        style = Stroke(width = radius * 0.07f),
    )
    // Specular highlight.
    drawArc(
        color = Color.White.copy(alpha = if (white) 0.5f else 0.22f),
        startAngle = 195f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(centreX - radius * 0.72f, centreY - radius * 0.78f),
        size = Size(radius * 1.44f, radius * 1.44f),
        style = Stroke(width = radius * 0.10f),
    )
    if (highlighted) {
        drawCircle(
            color = BackgammonPalette.gold,
            radius = radius * 1.05f,
            center = Offset(centreX, centreY),
            style = Stroke(width = radius * 0.16f),
        )
    }
}
