package ir.dorehami.games.backgammon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.uikit.toFa

/**
 * Settings and the board/checker shop of the backgammon module — the same shape as chess and Hokm:
 * preferences on one page, collectible looks on another, both reachable from the main menu.
 */

/** Shared preferences file of the module (created by the entry screen). */
internal const val BACKGAMMON_PREFS = "backgammon-settings"

@Composable
internal fun BackgammonSettingsPage(
    name: String,
    onName: (String) -> Unit,
    targetPoints: Int,
    onTargetPoints: (Int) -> Unit,
    boardId: String,
    pieceId: String,
    wins: Int,
    onOpenShop: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "تنظیمات تخته نرد", subtitle = "نام، امتیاز هدف و ظاهر میز", onBack = onBack)
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("نام نمایشی", color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 14) onName(it) },
                placeholder = { Text("مثلاً آرش") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = BackgammonPalette.ink,
                    unfocusedTextColor = BackgammonPalette.ink,
                    focusedContainerColor = BackgammonPalette.cardAlt.copy(alpha = 0.7f),
                    unfocusedContainerColor = BackgammonPalette.cardAlt.copy(alpha = 0.55f),
                    focusedBorderColor = BackgammonPalette.accent,
                    unfocusedBorderColor = BackgammonPalette.line,
                    focusedLabelColor = BackgammonPalette.accent,
                    unfocusedLabelColor = BackgammonPalette.inkSoft,
                    cursorColor = BackgammonPalette.accent,
                ),
            )
        }
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("امتیاز پایان مسابقه", color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ir.dorehami.games.backgammon.BackgammonModule.ALLOWED_TARGETS.forEach { target ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (target == targetPoints) BackgammonPalette.accent else BackgammonPalette.cardAlt)
                            .border(
                                1.dp,
                                if (target == targetPoints) BackgammonPalette.accent else BackgammonPalette.line.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(role = Role.Button) { onTargetPoints(target) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            target.toFa(),
                            color = if (target == targetPoints) Color.White else BackgammonPalette.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
        BackgammonCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BoardThumbnail(BackgammonSkins.board(boardId), width = 84.dp)
                Column(Modifier.weight(1f)) {
                    Text(BackgammonSkins.board(boardId).titleFa, color = BackgammonPalette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(BackgammonSkins.pieces(pieceId).titleFa, color = BackgammonPalette.inkSoft, fontSize = 11.sp)
                    Text(
                        "بردهای تو: ${wins.toFa()}",
                        color = BackgammonPalette.inkSoft,
                        fontSize = 11.sp,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            TableButton(
                text = "فروشگاه تخته و مهره",
                primary = true,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenShop,
            )
        }
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text(
                "بازی همیشه به‌صورت افقی اجرا می‌شود تا تخته کامل در صفحه جا شود؛ با خروج از میز، چرخش گوشی آزاد می‌شود.",
                color = BackgammonPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
    }
}

@Composable
internal fun BackgammonShopPage(
    boardId: String,
    pieceId: String,
    wins: Int,
    onPickBoard: (String) -> Unit,
    onPickPieces: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "فروشگاه تخته نرد", subtitle = "با بردهای خودت طرح باز کن", onBack = onBack)
        BackgammonCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgammonIconView(BackgammonIcon.Trophy, color = BackgammonPalette.accent, size = 18.dp)
                Text(
                    "بردهای تو: ${wins.toFa()} · هیچ طرحی خریدنی با پول نیست",
                    color = BackgammonPalette.ink,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Text("تخته‌ها", color = BackgammonPalette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        BackgammonSkins.boards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { skin ->
                    BackgammonShopTile(
                        title = skin.titleFa,
                        subtitle = if (BackgammonSkins.unlocked(skin.winsRequired, wins)) skin.subtitleFa else "${(skin.winsRequired - wins).toFa()} برد دیگر",
                        selected = skin.id == boardId,
                        unlocked = BackgammonSkins.unlocked(skin, wins),
                        accent = skin.accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickBoard(skin.id) },
                        preview = { BoardThumbnail(skin) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("مهره‌ها", color = BackgammonPalette.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        BackgammonSkins.pieces.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { skin ->
                    BackgammonShopTile(
                        title = skin.titleFa,
                        subtitle = if (BackgammonSkins.unlocked(skin.winsRequired, wins)) skin.subtitleFa else "${(skin.winsRequired - wins).toFa()} برد دیگر",
                        selected = skin.id == pieceId,
                        unlocked = BackgammonSkins.unlocked(skin, wins),
                        accent = BackgammonPalette.accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onPickPieces(skin.id) },
                        preview = { CheckerPipsPreview(skin) },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}


@Composable
private fun BackgammonShopTile(
    title: String,
    subtitle: String,
    selected: Boolean,
    unlocked: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .heightIn(min = 132.dp)
            .clip(shape)
            .background(BackgammonPalette.card.copy(alpha = 0.94f))
            .border(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 0.95f else 0.45f), shape)
            .clickable(enabled = unlocked, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            preview()
            if (!unlocked) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    BackgammonIconView(BackgammonIcon.Lock, color = Color.White, size = 20.dp)
                }
            }
        }
        Text(title, color = BackgammonPalette.ink, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            if (selected) "انتخاب‌شده" else subtitle,
            color = if (selected) BackgammonPalette.accent else BackgammonPalette.inkSoft,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/** Small board preview with the design's own colours. */
@Composable
private fun BoardThumbnail(skin: BackgammonBoardSkin, width: androidx.compose.ui.unit.Dp = 130.dp) {
    Canvas(
        Modifier
            .size(width = width, height = 74.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(skin.woodLight, skin.woodDark)),
            cornerRadius = CornerRadius(w * 0.04f),
        )
        val bar = w * 0.06f
        val cell = (w - bar) / 12f
        for (i in 0 until 12) {
            val colour = if (i % 2 == 0) skin.pointLight else skin.pointDark
            val x = if (i < 6) i * cell else bar + i * cell
            drawRect(color = colour.copy(alpha = 0.92f), topLeft = Offset(x, 0f), size = Size(cell * 0.9f, h * 0.32f))
            drawRect(color = colour.copy(alpha = 0.92f), topLeft = Offset(x, h * 0.68f), size = Size(cell * 0.9f, h * 0.32f))
        }
        drawRect(color = skin.woodDark, topLeft = Offset((w - bar) / 2f, 0f), size = Size(bar, h))
        drawRect(
            color = skin.accent.copy(alpha = 0.5f),
            style = Stroke(width = w * 0.01f),
        )
    }
}

/** Two checkers of the design's colours. */
@Composable
private fun CheckerPipsPreview(skin: BackgammonPieceSkin) {
    Canvas(
        Modifier
            .size(width = 130.dp, height = 74.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(BackgammonPalette.cardAlt.copy(alpha = 0.6f)),
    ) {
        val r = size.minDimension * 0.22f
        val cy = size.height / 2f
        listOf(
            Triple(size.width * 0.32f, skin.whiteFace, skin.whiteRim),
            Triple(size.width * 0.68f, skin.blackFace, skin.blackRim),
        ).forEach { (cx, face, rim) ->
            drawCircle(Color.Black.copy(alpha = 0.3f), r, Offset(cx, cy + r * 0.16f))
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(face, rim),
                    center = Offset(cx - r * 0.3f, cy - r * 0.3f),
                    radius = r * 1.6f,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
            drawCircle(rim, r, Offset(cx, cy), style = Stroke(r * 0.16f))
        }
    }
}
