package ir.dorehami.games.backgammon.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Warm walnut board colours — the same "authentic table, calm and premium" direction as chess. */
object BackgammonPalette {
    val night = Color(0xFF14100C)
    val walnut = Color(0xFF3A2718)
    val walnutLight = Color(0xFF573B24)
    val pointLight = Color(0xFFD9B98A)
    val pointDark = Color(0xFF6E3F22)
    val bar = Color(0xFF241708)
    val ivory = Color(0xFFF4E7CE)
    val ebony = Color(0xFF1F1B17)
    val gold = Color(0xFFE0B65A)
    val goldDim = Color(0xFF9C7B33)
    val mint = Color(0xFF7FD1B9)
    val coral = Color(0xFFE2705E)
    val text = Color(0xFFF6ECDA)
    val muted = Color(0xFFC7B49A)
}

@Composable
fun BackgammonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = BackgammonPalette.gold,
            onPrimary = BackgammonPalette.night,
            secondary = BackgammonPalette.mint,
            onSecondary = BackgammonPalette.night,
            tertiary = BackgammonPalette.coral,
            background = BackgammonPalette.night,
            onBackground = BackgammonPalette.text,
            surface = BackgammonPalette.walnut,
            onSurface = BackgammonPalette.text,
            surfaceVariant = BackgammonPalette.walnutLight,
            onSurfaceVariant = BackgammonPalette.muted,
            outline = BackgammonPalette.goldDim,
            error = BackgammonPalette.coral,
        ),
        content = content,
    )
}

/** Backdrop drawn procedurally so the module ships no extra artwork and stays lightweight. */
@Composable
fun BackgammonBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(BackgammonPalette.night)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF1B140D), BackgammonPalette.night),
                ),
            )
            drawCircle(
                color = BackgammonPalette.gold.copy(alpha = 0.07f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.9f, size.height * 0.05f),
            )
            drawCircle(
                color = BackgammonPalette.mint.copy(alpha = 0.05f),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.05f, size.height * 0.95f),
            )
        }
    }
}

/** Glass-ish card used by every backgammon menu and HUD row. */
@Composable
fun BackgammonCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(BackgammonPalette.walnut.copy(alpha = 0.92f))
            .border(1.dp, BackgammonPalette.goldDim.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        content = content,
    )
}

/** Big menu row: the offline/online split reads the same as the chess menu. */
@Composable
fun BackgammonMenuAction(
    icon: String,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BackgammonPalette.walnutLight.copy(alpha = if (enabled) 0.95f else 0.5f))
            .border(1.dp, BackgammonPalette.goldDim.copy(alpha = if (enabled) 0.55f else 0.25f), RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(BackgammonPalette.gold.copy(alpha = 0.16f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(icon, color = BackgammonPalette.gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = BackgammonPalette.muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
        Text("‹", color = BackgammonPalette.gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Header with the app identity and a back button; used by every screen of this module. */
@Composable
fun BackgammonHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = BackgammonPalette.text, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (subtitle != null) {
                Text(subtitle, color = BackgammonPalette.muted, fontSize = 11.sp)
            }
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(BackgammonPalette.walnutLight)
                .border(1.dp, BackgammonPalette.goldDim.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text("بازگشت", color = BackgammonPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Shared page scaffold of this module (backdrop + safe padding + scrolling column). */
@Composable
fun BackgammonPage(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BackgammonBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

/** Center-aligned short note (status line under the board). */
@Composable
fun BackgammonNote(text: String, color: Color = BackgammonPalette.muted, bold: Boolean = false) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
