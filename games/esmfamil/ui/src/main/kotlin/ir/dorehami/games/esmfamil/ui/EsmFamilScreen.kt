package ir.dorehami.games.esmfamil.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.esmfamil.EsmFamilSheet
import ir.dorehami.uikit.toFa

/**
 * The sheet table. One screen handles the whole match: the letter card, the handwritten sheet,
 * the opened sheets with their points, and the final table.
 *
 * Reading order is RTL like the rest of the app; every number goes through `toFa()`.
 */
@Composable
fun EsmFamilScreen(
    state: EsmFamilUiState,
    onType: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCloseRound: () -> Unit,
    onNext: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    EsmFamilPage {
        EsmFamilHeader(
            title = "اسم فامیل",
            subtitle = state.modeLabel,
            onBack = onExit,
        )

        LetterCard(state = state)

        when (state.phase) {
            EsmFamilPhase.WRITING -> WritingBlock(
                state = state,
                onType = onType,
                onSubmit = onSubmit,
                onCloseRound = onCloseRound,
            )
            EsmFamilPhase.REVEAL -> RevealBlock(state = state, onNext = onNext)
            EsmFamilPhase.FINISHED -> FinishedBlock(state = state, onPlayAgain = onPlayAgain, onExit = onExit)
        }

        if (state.error != null) {
            EsmFamilNote(state.error, color = EsmFamilPalette.danger, bold = true)
        }
    }
}

/** The drawn letter, the round counter and the little burst that fires when sheets open. */
@Composable
private fun LetterCard(state: EsmFamilUiState) {
    val pop by animateFloatAsState(
        targetValue = if (state.phase == EsmFamilPhase.WRITING) 1f else 1.06f,
        animationSpec = tween(260),
        label = "esmFamilLetterPop",
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(EsmFamilPalette.card.copy(alpha = 0.86f))
                .border(1.dp, EsmFamilPalette.line.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                "دور ${state.round.toFa()} از ${state.rounds.toFa()}",
                color = EsmFamilPalette.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (state.phase) {
                    EsmFamilPhase.WRITING -> "همه با این حرف برگه را پر می‌کنند"
                    EsmFamilPhase.REVEAL -> "برگه‌ها رو شد؛ امتیازها را ببین"
                    EsmFamilPhase.FINISHED -> "دست تمام شد؛ جدول پایانی پایین است"
                },
                color = EsmFamilPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        Box(
            Modifier
                .size(84.dp)
                .scale(pop)
                .clip(RoundedCornerShape(24.dp))
                .background(EsmFamilPalette.letterCard)
                .border(2.dp, EsmFamilPalette.accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = EsmFamilUiModel.letterChip(state.letter),
                color = EsmFamilPalette.pen,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/** The writing phase: either my row (room) or the hand that currently holds the phone (local). */
@Composable
private fun WritingBlock(
    state: EsmFamilUiState,
    onType: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onCloseRound: () -> Unit,
) {
    val alreadyIn = state.hasWritten
    EsmFamilCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (state.seats.size > 1) "برگهٔ ${state.writerName}" else "برگهٔ تو",
                color = EsmFamilPalette.ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                EsmFamilIconView(EsmFamilIcon.Pen, color = EsmFamilPalette.accent, size = 15.dp)
                Text(
                    "${state.submittedIds.size.toFa()} از ${state.seats.size.toFa()} برگه",
                    color = EsmFamilPalette.inkSoft,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        state.categories.forEach { category ->
            SheetRow(
                categoryId = category,
                letter = state.letter,
                value = if (alreadyIn) state.mine[category].orEmpty() else state.draft[category].orEmpty(),
                enabled = !alreadyIn,
                onValueChange = { onType(category, it) },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (alreadyIn) {
            EsmFamilNote("برگه‌ات ثبت شد. منتظر بقیه…", color = EsmFamilPalette.teal, bold = true)
        } else {
            EsmFamilButton(
                text = "ثبت برگه",
                primary = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSubmit,
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EsmFamilButton(
            text = "پایان دور",
            primary = false,
            enabled = !alreadyIn || state.isHost,
            modifier = Modifier.weight(1f),
            onClick = onCloseRound,
        )
    }
    EsmFamilNote(
        "هر جواب یکتا ${EsmFamilSheet.POINTS_UNIQUE.toFa()} امتیاز دارد و جواب تکراری ${EsmFamilSheet.POINTS_SHARED.toFa()}؛ " +
            "جوابی که با حرف شروع نشود صفر می‌گیرد.",
        color = EsmFamilPalette.inkSoft,
    )
}

/** One ruled line of the sheet: the column name on the right, the pen line on the left. */
@Composable
private fun SheetRow(
    categoryId: String,
    letter: String,
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = EsmFamilSheet.titleOf(categoryId),
            color = EsmFamilPalette.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.size(width = 66.dp, height = 24.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = letter.ifEmpty { "—" },
                    color = EsmFamilPalette.rule,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            modifier = Modifier.weight(1f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = EsmFamilPalette.pen,
                unfocusedTextColor = EsmFamilPalette.pen,
                disabledTextColor = EsmFamilPalette.inkSoft,
                focusedContainerColor = EsmFamilPalette.cardAlt.copy(alpha = 0.7f),
                unfocusedContainerColor = EsmFamilPalette.cardAlt.copy(alpha = 0.5f),
                disabledContainerColor = EsmFamilPalette.cardAlt.copy(alpha = 0.35f),
                focusedBorderColor = EsmFamilPalette.accent,
                unfocusedBorderColor = EsmFamilPalette.rule,
                disabledBorderColor = EsmFamilPalette.rule.copy(alpha = 0.6f),
                cursorColor = EsmFamilPalette.accent,
            ),
        )
    }
}

/** The opened sheets: every player's row with its points, best row first. */
@Composable
private fun RevealBlock(state: EsmFamilUiState, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        // A quiet burst: deterministic rays behind the cards, so the reveal feels like an event
        // without a random particle system and without a frame budget surprise.
        Canvas(Modifier.fillMaxWidth().height(6.dp)) {
            val w = size.width
            repeat(24) { index ->
                val t = index / 24f
                val x = w * t
                drawCircle(
                    color = if (index % 3 == 0) EsmFamilPalette.accent else EsmFamilPalette.teal,
                    radius = size.height * 0.16f,
                    center = Offset(x, size.height / 2f),
                    alpha = 0.5f,
                )
            }
        }
    }
    state.rows
        .sortedByDescending { row -> row.points.values.sum() }
        .forEach { row -> RevealRowCard(row = row, categories = state.categories) }
    EsmFamilButton(
        text = if (state.round >= state.rounds) "دیدن جدول پایانی" else "دور بعد",
        primary = true,
        enabled = state.isHost,
        modifier = Modifier.fillMaxWidth(),
        onClick = onNext,
    )
    if (!state.isHost) {
        EsmFamilNote("میزبان دور بعد را شروع می‌کند.", color = EsmFamilPalette.inkSoft)
    }
}

@Composable
private fun RevealRowCard(row: EsmFamilRow, categories: List<String>) {
    val roundTotal = row.points.values.sum()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(EsmFamilPalette.card.copy(alpha = 0.88f))
            .border(
                if (row.isMe) 2.dp else 1.dp,
                if (row.isMe) EsmFamilPalette.accent.copy(alpha = 0.6f) else EsmFamilPalette.line.copy(alpha = 0.6f),
                RoundedCornerShape(20.dp),
            )
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                color = EsmFamilPalette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f),
            )
            Text(
                "این دور ${roundTotal.toFa()} · کل ${row.total.toFa()}",
                color = EsmFamilPalette.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        categories.forEach { category ->
            val answer = row.answers[category].orEmpty()
            val points = row.points[category] ?: 0
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    EsmFamilSheet.titleOf(category),
                    color = EsmFamilPalette.inkSoft,
                    fontSize = 11.sp,
                    modifier = Modifier.size(width = 58.dp, height = 18.dp),
                )
                Text(
                    answer.ifBlank { "—" },
                    color = if (points > 0) EsmFamilPalette.pen else EsmFamilPalette.inkSoft,
                    fontSize = 13.sp,
                    fontWeight = if (points > 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                PointsBadge(points)
            }
        }
    }
}

/** 10 / 5 / 0 as a small lozenge — the whole scoring rule, visible at a glance. */
@Composable
private fun PointsBadge(points: Int) {
    val (colour, label) = when (EsmFamilUiModel.badgeOf(points)) {
        EsmFamilBadge.Full -> EsmFamilPalette.pointsFull to points.toFa()
        EsmFamilBadge.Shared -> EsmFamilPalette.pointsHalf to points.toFa()
        EsmFamilBadge.None -> EsmFamilPalette.pointsNone to "۰"
    }
    Box(
        Modifier
            .size(width = 34.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(colour.copy(alpha = 0.14f))
            .border(1.dp, colour.copy(alpha = 0.55f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colour, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

/** The final table: ranks, points and the winner. */
@Composable
private fun FinishedBlock(state: EsmFamilUiState, onPlayAgain: () -> Unit, onExit: () -> Unit) {
    val winners = state.standings.filter { it.rank == 1 }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(EsmFamilPalette.card.copy(alpha = 0.95f), EsmFamilPalette.cardAlt.copy(alpha = 0.9f)),
                ),
            )
            .border(1.dp, EsmFamilPalette.line.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EsmFamilIconView(EsmFamilIcon.Trophy, color = EsmFamilPalette.accent, size = 22.dp)
            Text(
                if (winners.size == 1) "${winners.first().name} برد" else "مساوی شد",
                color = EsmFamilPalette.ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.height(8.dp))
        state.standings.forEach { standing ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${standing.rank.toFa()}.",
                    color = if (standing.rank == 1) EsmFamilPalette.accent else EsmFamilPalette.inkSoft,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.size(width = 30.dp, height = 20.dp),
                )
                Text(
                    standing.name,
                    color = EsmFamilPalette.ink,
                    fontSize = 14.sp,
                    fontWeight = if (standing.rank == 1) FontWeight.Black else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${standing.score.toFa()} امتیاز",
                    color = EsmFamilPalette.inkSoft,
                    fontSize = 12.sp,
                )
            }
        }
    }
    EsmFamilButton(
        text = "بازی دوباره",
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onPlayAgain,
    )
    EsmFamilButton(
        text = "بازگشت به منوی اسم فامیل",
        primary = false,
        modifier = Modifier.fillMaxWidth(),
        onClick = onExit,
    )
}

/** Small round badge used by the lobby and the room header. */
@Composable
fun EsmFamilPill(text: String, colour: Color = EsmFamilPalette.teal) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colour.copy(alpha = 0.14f))
            .border(1.dp, colour.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, color = colour, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
