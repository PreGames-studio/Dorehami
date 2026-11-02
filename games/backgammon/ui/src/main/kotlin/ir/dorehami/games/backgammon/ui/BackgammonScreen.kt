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
import androidx.compose.foundation.layout.width
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

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

    // Watch the board and animate the checker that just moved. The engine only publishes whole
    // positions, so the target comes from a diff (see BackgammonMoveTrace).
    var lastBoard by remember { mutableStateOf(state.board) }
    var moving by remember { mutableStateOf<CheckerMove?>(null) }
    var stamp by remember { mutableStateOf(0L) }
    LaunchedEffect(state.board) {
        if (state.board != lastBoard) {
            val trace = diffCheckerMove(lastBoard, state.board)
            if (trace != null) {
                stamp += 1
                moving = CheckerMove(from = trace.from, to = trace.to, white = trace.white, stamp = stamp)
            }
            lastBoard = state.board
        }
    }

    Box(Modifier.fillMaxSize()) {
        BackgammonBackdrop(Modifier.fillMaxSize())
        // Landscape is the real table: the board takes the start side and the HUD the end side.
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        BackgammonBoard(
                            board = state.board,
                            selectedPoint = state.selectedPoint,
                            hints = state.hints,
                            enabled = state.canMove,
                            onPoint = onPoint,
                            moving = moving,
                            modifier = Modifier.fillMaxHeight(),
                        )
                    }
                    Column(
                        Modifier
                            .width(230.dp)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BackgammonHeader(title = "تخته نرد", subtitle = modeLabel, onBack = onExit)
                        ScoreRow(state)
                        DiceRow(state, onDie)
                        ActionRows(state, onRoll, onUndo, onSubmit)
                        StatusNotes(state)
                    }
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BackgammonHeader(title = "تخته نرد", subtitle = modeLabel, onBack = onExit)
                    ScoreRow(state)
                    BackgammonBoard(
                        board = state.board,
                        selectedPoint = state.selectedPoint,
                        hints = state.hints,
                        enabled = state.canMove,
                        onPoint = onPoint,
                        moving = moving,
                    )
                    DiceRow(state, onDie)
                    ActionRows(state, onRoll, onUndo, onSubmit)
                    StatusNotes(state)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        if (state.finished && state.outcomeTitle != null) {
            AlertDialog(
                onDismissRequest = {},
                containerColor = BackgammonPalette.card,
                titleContentColor = BackgammonPalette.accent,
                textContentColor = BackgammonPalette.ink,
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
private fun ScoreRow(state: BackgammonUiState) {
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
}

@Composable
private fun DiceRow(state: BackgammonUiState, onDie: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.dice.isEmpty()) {
            BackgammonIconView(BackgammonIcon.Dice, color = BackgammonPalette.inkSoft, size = 16.dp)
            Text("تاس", color = BackgammonPalette.inkSoft, fontSize = 12.sp)
        } else {
            state.dice.forEachIndexed { index, die ->
                BackgammonDie(
                    die = die,
                    selectable = state.canMove,
                    chosen = state.selectedDie == die,
                    // The first die of a fresh roll tumbles in; the rest sit still.
                    animate = state.pending.isEmpty(),
                    delayMs = index * 90,
                    onClick = { onDie(die) },
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.dice.isNotEmpty()) {
            Text(
                if (state.selectedDie != 0) "تاس ${state.selectedDie.toFa()} انتخاب شد" else "یک تاس را بزن",
                color = BackgammonPalette.inkSoft,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ActionRows(
    state: BackgammonUiState,
    onRoll: () -> Unit,
    onUndo: () -> Unit,
    onSubmit: () -> Unit,
) {
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
}

@Composable
private fun StatusNotes(state: BackgammonUiState) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when {
            state.finished -> BackgammonNote("مسابقه تمام شد", BackgammonPalette.accent, bold = true)
            state.thinking -> BackgammonNote("ربات دارد فکر می‌کند…", BackgammonPalette.teal)
            state.canRoll -> BackgammonNote("نوبت ${state.turnName} است؛ اول تاس بریز.", BackgammonPalette.ink, bold = true)
            state.canMove && state.pending.isEmpty() -> BackgammonNote(
                "یک مهره را بزن (تاس مناسب خودکار انتخاب می‌شود) یا اول یک تاس را لمس کن.",
                BackgammonPalette.ink,
            )
            state.canMove -> BackgammonNote(
                "ادامه بده: ${state.pending.size.toFa()} حرکت انجام شد.",
                BackgammonPalette.inkSoft,
            )
            else -> BackgammonNote("منتظر نوبت حریف…", BackgammonPalette.inkSoft)
        }
        state.error?.let { message -> BackgammonNote(message, BackgammonPalette.danger, bold = true) }
        BackgammonNote(
            "مسابقه تا ${state.target.toFa()} امتیاز · دست ${state.gameNumber.toFa()}",
            BackgammonPalette.inkSoft,
        )
        Text(
            text = "برای بردن یک دست باید همهٔ ۱۵ مهره را از خانه بیرون ببری؛ گمون ۲ و بک‌گمون ۳ امتیاز دارد.",
            color = BackgammonPalette.inkSoft.copy(alpha = 0.75f),
            fontSize = 10.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
            .background(BackgammonPalette.card.copy(alpha = 0.9f))
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) BackgammonPalette.accent else BackgammonPalette.line.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Text(name, color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            "امتیاز ${score.toFa()} · بیرون ${borneOff.toFa()} از ${15.toFa()}",
            color = BackgammonPalette.inkSoft,
            fontSize = 11.sp,
        )
    }
}

/**
 * A real die face: rounded ivory body, bevelled edge and pips drawn as circles (the old version put
 * a Persian digit in a square). A fresh roll tumbles in with a short bounce and a settle spin.
 */
@Composable
private fun BackgammonDie(
    die: Int,
    selectable: Boolean,
    chosen: Boolean,
    animate: Boolean,
    delayMs: Int = 0,
    onClick: () -> Unit,
) {
    val look = LocalBackgammonLook.current.pieces
    val roll = remember(die) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(die, animate) {
        if (animate) {
            roll.snapTo(0f)
            roll.animateTo(1f, tween(420, delayMillis = delayMs, easing = FastOutSlowInEasing))
        } else {
            roll.snapTo(1f)
        }
    }
    val accent = BackgammonPalette.accent
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer {
                val p = roll.value
                val overshoot = 1f + 0.28f * (1f - p)
                scaleX = overshoot
                scaleY = overshoot
                rotationZ = (1f - p) * 220f
                alpha = 0.3f + 0.7f * p
            }
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (chosen) accent else BackgammonPalette.ivory,
                        if (chosen) accent.copy(alpha = 0.82f) else Color(0xFFE7DCC6),
                    ),
                ),
            )
            .border(
                width = if (chosen) 2.dp else 1.dp,
                color = if (chosen) accent else BackgammonPalette.line.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = selectable, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize().padding(9.dp)) {
            val r = size.minDimension
            val pip = r * 0.10f
            val ink = if (chosen) Color.White else BackgammonPalette.ink
            fun dot(x: Float, y: Float) {
                drawCircle(Color.Black.copy(alpha = 0.10f), radius = pip, center = Offset(x + pip * 0.18f, y + pip * 0.22f))
                drawCircle(ink, radius = pip, center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.22f), radius = pip * 0.35f, center = Offset(x - pip * 0.3f, y - pip * 0.3f))
            }
            val a = r * 0.22f
            val b = r * 0.5f
            val c = r * 0.78f
            when (die) {
                1 -> dot(b, b)
                2 -> {
                    dot(a, a); dot(c, c)
                }
                3 -> {
                    dot(a, a); dot(b, b); dot(c, c)
                }
                4 -> {
                    dot(a, a); dot(c, a); dot(a, c); dot(c, c)
                }
                5 -> {
                    dot(a, a); dot(c, a); dot(b, b); dot(a, c); dot(c, c)
                }
                else -> {
                    dot(a, a); dot(c, a); dot(a, b); dot(c, b); dot(a, c); dot(c, c)
                }
            }
            // Bevel shadow along the bottom edge so the die sits on the table.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.12f),
                cornerRadius = CornerRadius(r * 0.12f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.05f),
            )
        }
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
                    !enabled -> BackgammonPalette.cardAlt.copy(alpha = 0.5f)
                    primary -> BackgammonPalette.accent
                    else -> BackgammonPalette.cardAlt
                },
            )
            .border(
                1.dp,
                if (primary && enabled) BackgammonPalette.accent else BackgammonPalette.line.copy(alpha = 0.6f),
                RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> BackgammonPalette.inkSoft
                primary -> Color.White
                else -> BackgammonPalette.ink
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
