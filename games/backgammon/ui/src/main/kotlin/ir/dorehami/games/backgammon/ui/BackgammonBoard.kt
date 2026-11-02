package ir.dorehami.games.backgammon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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

/**
 * The backgammon board: 24 points, the bar and both checker colours. Purely visual — every tap is
 * reported to the caller, which knows whether it is legal.
 */
@Composable
fun BackgammonBoard(
    board: BackgammonPosition,
    selectedPoint: Int,
    hints: Set<Int>,
    enabled: Boolean,
    onPoint: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier
            .fillMaxWidth()
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
        drawBoard(geometry)

        for (index in 0 until BackgammonRules.POINT_COUNT) {
            val count = board.points[index]
            if (count == 0) continue
            drawStack(
                geometry = geometry,
                index = index,
                count = kotlin.math.abs(count),
                white = count > 0,
                selected = index == selectedPoint,
            )
        }

        // Bar checkers wait at the middle; white stacks from the bottom, black from the top.
        drawBarStack(geometry, board.barWhite, white = true, selected = selectedPoint == BackgammonRules.FROM_BAR)
        drawBarStack(geometry, board.barBlack, white = false, selected = false)

        hints.forEach { hint ->
            if (hint == BackgammonRules.FROM_BAR) {
                drawCircle(
                    color = BackgammonPalette.gold.copy(alpha = 0.85f),
                    radius = geometry.bar * 0.32f,
                    center = Offset(geometry.half + geometry.bar / 2f, size.height * 0.5f),
                )
            } else {
                val cx = geometry.leftOf(hint) + geometry.cell / 2f
                val cy = if (geometry.isTop(hint)) size.height * 0.09f else size.height * 0.91f
                drawCircle(
                    color = BackgammonPalette.gold.copy(alpha = 0.9f),
                    radius = geometry.cell * 0.11f,
                    center = Offset(cx, cy),
                )
            }
        }
    }
}

private fun DrawScope.drawBoard(geometry: BackgammonGeometry) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = BackgammonPalette.walnut,
        cornerRadius = CornerRadius(w * 0.03f),
    )
    drawRoundRect(
        color = BackgammonPalette.goldDim.copy(alpha = 0.75f),
        cornerRadius = CornerRadius(w * 0.03f),
        style = Stroke(width = 2.5f),
    )

    for (index in 0 until BackgammonRules.POINT_COUNT) {
        val left = geometry.leftOf(index)
        val top = geometry.isTop(index)
        val path = Path().apply {
            if (top) {
                moveTo(left, 0f)
                lineTo(left + geometry.cell, 0f)
                lineTo(left + geometry.cell / 2f, geometry.depth)
            } else {
                moveTo(left, h)
                lineTo(left + geometry.cell, h)
                lineTo(left + geometry.cell / 2f, h - geometry.depth)
            }
            close()
        }
        val light = index % 2 == 0
        drawPath(path = path, color = if (light) BackgammonPalette.pointLight else BackgammonPalette.pointDark)
    }

    drawRect(
        color = BackgammonPalette.bar,
        topLeft = Offset(geometry.half, 0f),
        size = Size(geometry.bar, h),
    )
    drawLine(
        color = BackgammonPalette.goldDim.copy(alpha = 0.55f),
        start = Offset(geometry.half, 0f),
        end = Offset(geometry.half, h),
        strokeWidth = 1.5f,
    )
    drawLine(
        color = BackgammonPalette.goldDim.copy(alpha = 0.55f),
        start = Offset(geometry.half + geometry.bar, 0f),
        end = Offset(geometry.half + geometry.bar, h),
        strokeWidth = 1.5f,
    )
}

private fun DrawScope.drawStack(
    geometry: BackgammonGeometry,
    index: Int,
    count: Int,
    white: Boolean,
    selected: Boolean,
) {
    val centreX = geometry.leftOf(index) + geometry.cell / 2f
    val radius = min(geometry.cell * 0.36f, (geometry.depth - 8f) / (count * 2.05f)).coerceAtLeast(5f)
    val gap = radius * 0.22f
    for (slot in 0 until count) {
        val centreY = if (geometry.isTop(index)) {
            radius + 4f + slot * (radius * 2f + gap)
        } else {
            size.height - radius - 4f - slot * (radius * 2f + gap)
        }
        drawChecker(centreX, centreY, radius, white, selected && slot == count - 1)
    }
}

private fun DrawScope.drawBarStack(
    geometry: BackgammonGeometry,
    count: Int,
    white: Boolean,
    selected: Boolean,
) {
    if (count <= 0) return
    val centreX = geometry.half + geometry.bar / 2f
    val radius = min(geometry.bar * 0.38f, (size.height * 0.4f) / (count * 2.05f)).coerceAtLeast(4f)
    val gap = radius * 0.22f
    for (slot in 0 until count) {
        val centreY = if (white) {
            size.height / 2f + radius + slot * (radius * 2f + gap)
        } else {
            size.height / 2f - radius - slot * (radius * 2f + gap)
        }
        drawChecker(centreX, centreY, radius, white, selected && slot == count - 1)
    }
}

private fun DrawScope.drawChecker(
    centreX: Float,
    centreY: Float,
    radius: Float,
    white: Boolean,
    highlighted: Boolean,
) {
    val face = if (white) BackgammonPalette.ivory else BackgammonPalette.ebony
    val rim = if (white) Color(0xFFC8B291) else Color(0xFF4A423A)
    drawCircle(color = Color.Black.copy(alpha = 0.35f), radius = radius, center = Offset(centreX, centreY + radius * 0.14f))
    drawCircle(color = face, radius = radius, center = Offset(centreX, centreY))
    drawCircle(color = rim, radius = radius, center = Offset(centreX, centreY), style = Stroke(width = radius * 0.16f))
    drawCircle(
        color = if (white) Color(0xFFB9A17B).copy(alpha = 0.6f) else Color(0xFF6B6157).copy(alpha = 0.6f),
        radius = radius * 0.5f,
        center = Offset(centreX, centreY),
        style = Stroke(width = radius * 0.1f),
    )
    if (highlighted) {
        drawCircle(
            color = BackgammonPalette.gold,
            radius = radius * 1.04f,
            center = Offset(centreX, centreY),
            style = Stroke(width = radius * 0.2f),
        )
    }
}
