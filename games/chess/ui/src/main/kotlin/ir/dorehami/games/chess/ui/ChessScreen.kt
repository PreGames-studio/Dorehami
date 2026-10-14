package ir.dorehami.games.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import ir.dorehami.games.chess.ui.gl.Chess3DView
import ir.dorehami.games.chess.ui.gl.ChessBoardPalette
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.delay

/**
 * The chess table: a 3D board (OpenGL ES 2) between two player cards, with a move list and the
 * action bar under it.
 *
 * Pure presentation — it reads [ChessUiState] and reports taps. Rules, bot and the engine live in
 * [ChessLocalMatch]; only the player's own view is ever passed in, so nothing hidden leaks here.
 */
@Composable
fun ChessScreen(
    state: ChessUiState,
    onSquareTap: (Int) -> Unit,
    onPromotion: (Char?) -> Unit,
    onFlip: () -> Unit,
    onUndo: () -> Unit,
    onResign: () -> Unit,
    onOfferDraw: () -> Unit,
    onAnswerDraw: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onClearNote: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    boardStyle: ChessBoardStyle = ChessBoardStyle.WOOD,
    pieceStyle: ChessPieceStyle = ChessPieceStyle.CLASSIC,
) {
    var confirmExit by remember { mutableStateOf(false) }
    var confirmUndo by remember { mutableStateOf(false) }
    var showMoves by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            delay(2200)
            onClearError()
        }
    }
    LaunchedEffect(state.note) {
        if (state.note != null) {
            delay(2600)
            onClearNote()
        }
    }

    // One view for the whole composition: its GL context survives recomposition and rotation-free
    // recreation is not needed (the match is rebuilt from the seed anyway).
    val context = LocalContext.current
    val board = remember { Chess3DView(context) }
    val darkTheme = isSystemInDarkTheme()
    LaunchedEffect(board, darkTheme, boardStyle, pieceStyle) {
        board.setPalette(ChessBoardPalette.forAppearance(darkTheme, boardStyle, pieceStyle))
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(board, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> board.onPause()
                Lifecycle.Event.ON_RESUME -> board.onResume()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            board.onPause()
        }
    }
    LaunchedEffect(state) {
        board.onSquareTap = { sq -> onSquareTap(sq) }
        board.setScene(state.scene())
        board.setViewSide(state.viewerSide == Side.WHITE, animate = true)
    }

    Box(Modifier.fillMaxSize()) {
        ChessBackdrop(imageRes = R.drawable.chess_game_backdrop, modifier = Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                mode = state.mode,
                thinking = state.botThinking,
                onExit = { confirmExit = true },
                onMoves = { showMoves = true },
            )

            PlayerRow(state.top, state)

            Box(Modifier.weight(1f)) {
                AndroidView(factory = { board }, modifier = Modifier.fillMaxSize())
            }

            // Keep Compose controls outside the translucent SurfaceView bounds so they remain
            // above the GL surface on devices that require its overlay composition mode.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton("↔ چرخش صفحه", onFlip, Modifier.weight(1f))
                GhostButton("نمای پیش‌فرض", { board.resetView() }, Modifier.weight(1f))
            }

            PlayerRow(state.bottom, state)

            ActionBar(
                state = state,
                onUndo = { if (state.canUndo) confirmUndo = true },
                onResign = onResign,
                onOfferDraw = onOfferDraw,
            )
        }

        val toast = state.error ?: state.note
        AnimatedVisibility(visible = toast != null, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = toast ?: "",
                    color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    fontSize = chessSp(14),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }

    val promo = state.promotion
    if (promo != null) {
        PromotionDialog(pieces = promo.pieces, side = state.view?.you?.color ?: state.viewerSide, onPick = onPromotion)
    }
    if (state.drawQuestionFor != 0 && state.outcome == null) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("پیشنهاد مساوی") },
            text = { Text("${ChessText.colorName(-state.drawQuestionFor)} مساوی پیشنهاد داده. قبول می‌کنی؟") },
            confirmButton = { TextButton(onClick = { onAnswerDraw(true) }) { Text("قبول") } },
            dismissButton = { TextButton(onClick = { onAnswerDraw(false) }) { Text("رد کن") } },
        )
    }
    if (confirmUndo) {
        AlertDialog(
            onDismissRequest = { confirmUndo = false },
            title = { Text("آخرین حرکت برگردد؟") },
            text = { Text("حرکت آخر (و جواب ربات) از بازی حذف می‌شود؛ بازی از اولِ همین وضعیت ادامه پیدا می‌کند.") },
            confirmButton = {
                TextButton(onClick = { confirmUndo = false; onUndo() }) { Text("برگردان") }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = false }) { Text("بی‌خیال") } },
        )
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("از بازی بیرون می‌ری؟") },
            text = { Text("اگر بازی تمام نشده باشد، نتیجه‌ای ثبت نمی‌شود.") },
            confirmButton = { TextButton(onClick = onExit) { Text("خروج") } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("ادامهٔ بازی") } },
        )
    }
    if (showMoves) {
        MovesDialog(san = state.view?.san ?: emptyList(), onDismiss = { showMoves = false })
    }
    state.outcome?.let { o ->
        OutcomeDialog(o, onPlayAgain = onPlayAgain, onExit = onExit)
    }
}

// ------------------------------------------------------------------ chrome

@Composable
private fun TopBar(mode: ChessMode, thinking: Boolean, onExit: () -> Unit, onMoves: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onExit) { Text("بازگشت", color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(14)) }
        Text("شطرنج", color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(18), fontWeight = FontWeight.Black)
        Spacer(Modifier.weight(1f))
        if (thinking) {
            val pulse = rememberInfiniteTransition(label = "think")
            val a by pulse.animateFloat(
                0.35f,
                1f,
                infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
                label = "alpha",
            )
            Text("ربات فکر می‌کند…", color = MaterialTheme.colorScheme.primary, fontSize = chessSp(12), modifier = Modifier.alpha(a))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            if (mode == ChessMode.PASS_AND_PLAY) "دو نفره روی یک گوشی" else "تک‌نفره",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(10),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        TextButton(onClick = onMoves) { Text("حرکت‌ها", color = MaterialTheme.colorScheme.primary, fontSize = chessSp(13)) }
    }
}

@Composable
private fun PlayerRow(card: ChessPlayerCard, state: ChessUiState) {
    val v = state.view
    val captured = if (card.side == Side.WHITE) v?.capturedByWhite ?: "" else v?.capturedByBlack ?: ""
    val advantage = if (v == null) 0 else ChessText.materialBalance(v.capturedByWhite, v.capturedByBlack) * card.side
    val myTurn = v != null && !v.finished && v.sideToMove == card.side
    val isMe = v?.you?.color == card.side
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                (if (myTurn) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .copy(alpha = 0.84f),
            )
            .border(1.dp, if (myTurn) MaterialTheme.colorScheme.secondary.copy(alpha = 0.68f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (card.side == Side.WHITE) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                ChessText.glyph(6 * card.side),
                fontSize = chessSp(17),
                color = if (card.side == Side.WHITE) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(card.name, color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(13), fontWeight = FontWeight.Bold)
                if (isMe) Text(" · شما", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(11))
                if (myTurn) Text(" ●", color = MaterialTheme.colorScheme.secondary, fontSize = chessSp(11))
            }
            val tray = ChessText.sortedTray(captured)
            val line = buildString {
                for (c in tray) append(ChessText.glyph(c))
                if (advantage > 0) append(" +${advantage.toFa()}")
            }
            Text(
                line.ifEmpty { if (v == null || v.ply == 0) "مهره‌ای نگرفته" else "—" },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = chessSp(13),
            )
        }
        Text("حرکت ${(((v?.ply ?: 0) + 1) / 2).toFa()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(10))
    }
}

@Composable
private fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ChessTonalButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        minHeight = 40,
        tone = ChessButtonTone.Neutral,
    )
}

@Composable
private fun ActionBar(state: ChessUiState, onUndo: () -> Unit, onResign: () -> Unit, onOfferDraw: () -> Unit) {
    val v = state.view
    val myTurn = v?.you?.myTurn == true && !state.botThinking
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ActionButton("↩ برگرداندن", state.canUndo, onUndo, Modifier.weight(1f), tone = ChessButtonTone.Primary)
            ActionButton("½ مساوی", state.canOfferDraw, onOfferDraw, Modifier.weight(1f), tone = ChessButtonTone.Secondary)
            ActionButton("🏳 تسلیم", state.canResign, onResign, Modifier.weight(1f), tone = ChessButtonTone.Danger)
        }
        val status = when {
            v == null -> "…"
            v.finished -> "بازی تمام شد"
            state.mode == ChessMode.PASS_AND_PLAY && !myTurn -> "نوبت ${ChessText.colorName(v.sideToMove)} — گوشی را بده به او"
            myTurn && v.inCheck -> "نوبت توئه و شاهت در کیشه!"
            myTurn -> "نوبت توئه؛ روی مهره‌ات بزن"
            state.botThinking -> "ربات دارد حساب می‌کند…"
            else -> "نوبت ${ChessText.colorName(v.sideToMove)}"
        }
        Text(
            status,
            color = if (myTurn) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(12),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ChessButtonTone,
) {
    ChessTonalButton(
        text = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        tone = tone,
        minHeight = 48,
    )
}

// ------------------------------------------------------------------ dialogs

@Composable
private fun PromotionDialog(pieces: List<Char>, side: Int, onPick: (Char?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(null) },
        title = { Text("وزیر یا چیز دیگر؟") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pieces.forEach { letter ->
                    val type = when (letter) {
                        'q' -> 5
                        'r' -> 4
                        'b' -> 3
                        else -> 2
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (type == 5) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                            .clickable(role = androidx.compose.ui.semantics.Role.Button) { onPick(letter) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(ChessText.glyph(type * side), fontSize = chessSp(30), color = if (type == 5) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        Text(ChessText.promotionName(letter), fontSize = chessSp(11), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = { TextButton(onClick = { onPick(null) }) { Text("انصراف") } },
    )
}

@Composable
private fun MovesDialog(san: List<String>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حرکت‌ها") },
        text = {
            // Move numbers are physical LTR order even in a Persian layout.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (san.isEmpty()) Text("هنوز حرکتی انجام نشده", fontSize = chessSp(13))
                    var i = 0
                    while (i < san.size) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${(i / 2 + 1).toFa()}.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(13), modifier = Modifier.width(30.dp))
                            Text(san[i], color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(13), modifier = Modifier.weight(1f))
                            Text(san.getOrNull(i + 1) ?: "", color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(13), modifier = Modifier.weight(1f))
                        }
                        i += 2
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun OutcomeDialog(outcome: ChessOutcome, onPlayAgain: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text(outcome.title, fontWeight = FontWeight.Black, fontSize = chessSp(20)) },
        text = {
            val who = if (outcome.winnerSide != 0) " — ${ChessText.colorName(outcome.winnerSide)}" else ""
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    painter = painterResource(R.drawable.chess_finish_art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(116.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Text(outcome.detail + who, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(14))
            }
        },
        confirmButton = { ChessGradientButton(text = "دوباره", onClick = onPlayAgain) },
        dismissButton = { TextButton(onClick = onExit) { Text("خروج") } },
    )
}
