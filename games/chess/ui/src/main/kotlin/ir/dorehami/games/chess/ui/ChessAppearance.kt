package ir.dorehami.games.chess.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.chess.ui.gl.ChessBoardPalette

/** Five frame/table materials; board-square colors remain white and black for all of them. */
enum class ChessBoardStyle(val title: String) {
    METAL("فلزی"),
    WOOD("چوبی"),
    LUXURY("لاکچری"),
    TRADITIONAL("سنتی"),
    PAPER("کاغذی"),
}

/** The same two-sided set of pieces in four different material finishes. */
enum class ChessPieceStyle(val title: String) {
    GLOSSY_METAL("فلزی براق"),
    MATTE_METAL("فلزی مات"),
    WOOD("چوبی"),
    CLASSIC("کلاسیک"),
}

@Composable
internal fun ChessBoardStylePicker(
    selected: ChessBoardStyle,
    onSelected: (ChessBoardStyle) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("طرح تخته", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "خانه‌ها در همهٔ طرح‌ها سفید و مشکی می‌مانند.",
            fontSize = chessSp(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChessBoardStyle.values().forEach { style ->
                val palette = remember(style, dark) {
                    ChessBoardPalette.forAppearance(dark, style, ChessPieceStyle.CLASSIC)
                }
                val isSelected = style == selected
                val shape = RoundedCornerShape(17.dp)
                Column(
                    modifier = Modifier
                        .width(116.dp)
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
                            shape = shape,
                        )
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) { onSelected(style) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    BoardStyleSwatch(style, palette)
                    Text(
                        text = style.title,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontSize = chessSp(12),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ChessPieceStylePicker(
    selected: ChessPieceStyle,
    onSelected: (ChessPieceStyle) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("جنس مهره‌ها", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChessPieceStyle.values().forEach { style ->
                val palette = remember(style, dark) {
                    ChessBoardPalette.forAppearance(dark, ChessBoardStyle.WOOD, style)
                }
                val isSelected = style == selected
                val shape = RoundedCornerShape(17.dp)
                Column(
                    modifier = Modifier
                        .width(116.dp)
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.52f),
                            shape = shape,
                        )
                        .semantics { this.selected = isSelected }
                        .clickable(role = Role.RadioButton) { onSelected(style) }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    PieceStyleSwatch(palette)
                    Text(
                        text = style.title,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        fontSize = chessSp(12),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Text(
            "هر جنس با مهره‌های سفید و مشکی نمایش داده می‌شود.",
            fontSize = chessSp(11),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoardStyleSwatch(style: ChessBoardStyle, palette: ChessBoardPalette) {
    Canvas(Modifier.fillMaxWidth().height(58.dp)) {
        val radius = 9.dp.toPx()
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(palette.frameStart), Color(palette.frameEnd))),
            cornerRadius = CornerRadius(radius, radius),
        )
        val margin = size.height * 0.16f
        val cell = (size.height - margin * 2f) / 4f
        val boardWidth = cell * 4f
        val left = (size.width - boardWidth) / 2f
        val top = margin
        for (rank in 0 until 4) for (file in 0 until 4) {
            drawRect(
                color = Color(if ((file + rank) % 2 == 0) palette.darkSquare else palette.lightSquare),
                topLeft = Offset(left + file * cell, top + rank * cell),
                size = Size(cell, cell),
            )
        }
        drawRoundRect(
            color = Color(palette.trim),
            topLeft = Offset(left - cell * 0.1f, top - cell * 0.1f),
            size = Size(boardWidth + cell * 0.2f, boardWidth + cell * 0.2f),
            cornerRadius = CornerRadius(cell * 0.16f, cell * 0.16f),
            style = Stroke(width = 1.4.dp.toPx()),
        )
        when (style) {
            ChessBoardStyle.METAL -> {
                val sheen = Color.White.copy(alpha = 0.38f)
                for (i in 0..2) {
                    val y = size.height * (0.18f + i * 0.11f)
                    drawLine(sheen, Offset(2.dp.toPx(), y), Offset(left - 2.dp.toPx(), y), 0.7.dp.toPx())
                }
            }
            ChessBoardStyle.LUXURY -> {
                val r = 1.4.dp.toPx()
                listOf(Offset(5.dp.toPx(), 6.dp.toPx()), Offset(size.width - 5.dp.toPx(), 6.dp.toPx()),
                    Offset(5.dp.toPx(), size.height - 6.dp.toPx()), Offset(size.width - 5.dp.toPx(), size.height - 6.dp.toPx()))
                    .forEach { drawCircle(Color(palette.trim), r, it) }
            }
            ChessBoardStyle.TRADITIONAL -> {
                val motif = Color(palette.trim).copy(alpha = 0.75f)
                val d = 2.1.dp.toPx()
                listOf(Offset(left * 0.5f, size.height * 0.5f), Offset(size.width - left * 0.5f, size.height * 0.5f))
                    .forEach { center ->
                        drawLine(motif, Offset(center.x - d, center.y), Offset(center.x, center.y - d), 1.dp.toPx())
                        drawLine(motif, Offset(center.x, center.y - d), Offset(center.x + d, center.y), 1.dp.toPx())
                        drawLine(motif, Offset(center.x + d, center.y), Offset(center.x, center.y + d), 1.dp.toPx())
                        drawLine(motif, Offset(center.x, center.y + d), Offset(center.x - d, center.y), 1.dp.toPx())
                    }
            }
            ChessBoardStyle.WOOD, ChessBoardStyle.PAPER -> Unit
        }
    }
}

@Composable
private fun PieceStyleSwatch(palette: ChessBoardPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Brush.horizontalGradient(listOf(Color(palette.darkPiece), Color(palette.lightPiece)))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color(palette.darkPiece).copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChessText.glyph(6), fontSize = 29.sp, color = Color(palette.lightPiece))
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color(palette.lightPiece)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ChessText.glyph(-6), fontSize = 29.sp, color = Color(palette.darkPiece))
        }
    }
}
