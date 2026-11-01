package ir.dorehami.games.backgammon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.uikit.toFa

/**
 * The table: score header, board, dice and the two controls a backgammon turn needs (roll, submit).
 * Every rule decision lives in `:games:backgammon:core`; this composable only renders and forwards.
 */
@Composable
fun BackgammonScreen(
    state: BackgammonUiState,
    modeLabel: String,
    onPoint: (Int) -> Unit,
    onDie: (Int) -> Unit,
    onRoll: () -> Unit,
    onUndo: () -> Unit,
    onSubmit: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)

    Box(Modifier.fillMaxSize()) {
        BackgammonBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BackgammonHeader(title = "تخته نرد", subtitle = modeLabel, onBack = onExit)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScoreBoard(
                    name = state.whiteName,
                    score = state.scoreWhite,
                    borneOff = state.board.offWhite,
                    active = state.whiteToMove && !state.finished,
                    modifier = Modifier.weight(1f),
                )
                ScoreBoard(
                    name = state.blackName,
                    score = state.scoreBlack,
                    borneOff = state.board.offBlack,
                    active = !state.whiteToMove && !state.finished,
                    modifier = Modifier.weight(1f),
                )
            }

            BackgammonBoard(
                board = state.board,
                selectedPoint = state.selectedPoint,
                hints = state.hints,
                enabled = state.canMove,
                onPoint = onPoint,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.dice.isEmpty()) {
                    Text("تاس", color = BackgammonPalette.muted, fontSize = 12.sp)
                } else {
                    state.dice.forEach { die ->
                        DieChip(
                            die = die,
                            selectable = state.canMove,
                            chosen = state.selectedDie == die,
                            onClick = { onDie(die) },
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canRoll) {
                    TableButton(
                        text = "ریختن تاس",
                        primary = true,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onRoll,
                    )
                }
                if (state.pending.isNotEmpty()) {
                    TableButton(
                        text = "لغو آخرین حرکت",
                        primary = false,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onUndo,
                    )
                    TableButton(
                        text = "ثبت نوبت",
                        primary = true,
                        enabled = true,
                        modifier = Modifier.weight(1f),
                        onClick = onSubmit,
                    )
                }
            }

            when {
                state.finished -> BackgammonNote("مسابقه تمام شد", BackgammonPalette.gold, bold = true)
                state.thinking -> BackgammonNote("ربات دارد فکر می‌کند…", BackgammonPalette.mint)
                state.canRoll -> BackgammonNote("نوبت ${state.turnName} است؛ اول تاس بریز.", BackgammonPalette.text, bold = true)
                state.canMove && state.pending.isEmpty() -> BackgammonNote(
                    "یک مهره را بزن (تاس مناسب خودکار انتخاب می‌شود) یا اول یک تاس را لمس کن.",
                    BackgammonPalette.text,
                )
                state.canMove -> BackgammonNote(
                    "ادامه بده: ${state.pending.size.toFa()} حرکت انجام شد.",
                    BackgammonPalette.muted,
                )
                else -> BackgammonNote("منتظر نوبت حریف…", BackgammonPalette.muted)
            }

            state.error?.let { message ->
                BackgammonNote(message, BackgammonPalette.coral, bold = true)
            }

            Spacer(Modifier.height(4.dp))
            BackgammonNote(
                "مسابقه تا ${state.target.toFa()} امتیاز · دست ${state.gameNumber.toFa()}",
                BackgammonPalette.muted,
            )
            Text(
                text = "برای بردن یک دست باید همهٔ ۱۵ مهره را از خانه بیرون ببری؛ گمون ۲ و بک‌گمون ۳ امتیاز دارد.",
                color = BackgammonPalette.muted.copy(alpha = 0.75f),
                fontSize = 10.sp,
                lineHeight = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }

        if (state.finished && state.outcomeTitle != null) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = BackgammonPalette.walnut,
                titleContentColor = BackgammonPalette.gold,
                textContentColor = BackgammonPalette.text,
                title = { Text(state.outcomeTitle, fontWeight = FontWeight.Bold) },
                text = { Text(state.outcomeDetail ?: "") },
                confirmButton = {
                    TableButton(
                        text = "بازی دوباره",
                        primary = true,
                        enabled = true,
                        modifier = Modifier.size(width = 116.dp, height = 42.dp),
                        onClick = onPlayAgain,
                    )
                },
                dismissButton = {
                    TableButton(
                        text = "بازگشت",
                        primary = false,
                        enabled = true,
                        modifier = Modifier.size(width = 96.dp, height = 42.dp),
                        onClick = onExit,
                    )
                },
            )
        }
    }
}

@Composable
private fun ScoreBoard(
    name: String,
    score: Int,
    borneOff: Int,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(BackgammonPalette.walnut.copy(alpha = 0.9f))
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) BackgammonPalette.gold else BackgammonPalette.goldDim.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(name, color = BackgammonPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            "امتیاز ${score.toFa()} · بیرون ${borneOff.toFa()} از ${15.toFa()}",
            color = BackgammonPalette.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun DieChip(
    die: Int,
    selectable: Boolean,
    chosen: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (chosen) BackgammonPalette.gold else BackgammonPalette.ivory)
            .border(
                width = if (chosen) 2.dp else 1.dp,
                color = if (chosen) BackgammonPalette.gold else BackgammonPalette.goldDim,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = selectable, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = die.toFa(),
            color = BackgammonPalette.night,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
fun TableButton(
    text: String,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    !enabled -> BackgammonPalette.walnutLight.copy(alpha = 0.5f)
                    primary -> BackgammonPalette.gold
                    else -> BackgammonPalette.walnutLight
                },
            )
            .border(
                1.dp,
                if (primary && enabled) BackgammonPalette.gold else BackgammonPalette.goldDim.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> BackgammonPalette.muted
                primary -> BackgammonPalette.night
                else -> BackgammonPalette.text
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
