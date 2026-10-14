package ir.dorehami.games.hokm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas

import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawScopeRotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HokmRules
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.delay
import kotlin.math.abs

/** Table palette: the locked GX-01 "Emerald Royale" direction (see HokmArtDirection). */
private object Table {
    val feltLight = Color(HokmArtDirection.FELT_SPOTLIGHT)
    val feltDark = Color(HokmArtDirection.FELT)
    val rimLight = Color(HokmArtDirection.BRASS_LIGHT)
    val rimDark = Color(0xFF6E5212)
    val brass = Color(HokmArtDirection.BRASS_LIGHT)
    val turquoise = Color(HokmArtDirection.TURQUOISE)
    val chip = Color(0x99000000)
    val turnGlow = Color(0xFFFFE082)
    val ours = Color(0xFF64B5F6)
    val theirs = Color(0xFFEF9A9A)
}

/**
 * The smallest touch target on the table (games/hokm/AGENTS.md 4.4: «هدف لمسی حداقل 48dp»).
 * Applied as a *minimum* hit-area floor (HK-013): cards already larger keep their size and the
 * fan's geometry is untouched, so the visual layout cannot regress.
 */
private val MIN_TOUCH_DP = 48.dp

/**
 * The Hokm table. Pure presentation: reads [HokmUiState], reports taps through callbacks.
 * Geometry is laid out LTR (seats are physical positions); all text is still RTL Persian.
 */
@Composable
fun HokmScreen(
    state: HokmUiState,
    onPlayCard: (Card) -> Unit,
    onChooseHokm: (Suit) -> Unit,
    onToggleDiscard: (Card) -> Unit,
    onConfirmDiscard: () -> Unit,
    onKeepCard: () -> Unit,
    onPassCard: () -> Unit,
    onSticker: (Sticker) -> Unit,
    onClearError: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    playAgainAvailable: Boolean = true,
    hapticsEnabled: Boolean = true,
    /** Timer sounds are routed through the same player as the table ("timer_tick" / "timer_warn"). */
    onUiSound: (String) -> Unit = {},
) {
    var confirmExit by remember { mutableStateOf(false) }
    var stickerTray by remember { mutableStateOf(false) }
    var showLastTrick by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    BackHandler {
        when {
            stickerTray -> stickerTray = false
            showLastTrick -> showLastTrick = false
            showSheet -> showSheet = false
            else -> confirmExit = true
        }
    }

    val rawHaptics = LocalHapticFeedback.current
    val haptics = remember(rawHaptics, hapticsEnabled) { if (hapticsEnabled) rawHaptics else null }
    // A nudge when it becomes my turn, a thump on kot.
    LaunchedEffect(state.isMyTurn) { if (state.isMyTurn) haptics?.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(state.fx.lastOrNull()?.stamp) {
        if (state.fx.lastOrNull()?.kind == FxKind.KOT) haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            delay(1800)
            onClearError()
        }
    }

    val reducedMotion = rememberHokmReducedMotion()
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Ltr,
        LocalHokmReducedMotion provides reducedMotion,
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Table.rimDark),
        ) {
            val w = maxWidth
            val h = maxHeight
            val compact = w < 380.dp
            val handCardW = if (compact) 58.dp else 68.dp
            val tableCardW = if (compact) 60.dp else 72.dp

            TableBackground()

            // ---- top: score bar, feed, and the seat(s) facing me
            val topSeat = when (state.playerCount) {
                4 -> HokmSeatLayout.seatAtOffset(state.mySeat, 2, state.playerCount)
                2 -> HokmSeatLayout.seatAtOffset(state.mySeat, 1, state.playerCount)
                else -> null
            }
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreBar(
                    state = state,
                    onExit = { confirmExit = true },
                    onSheet = {
                        onUiSound("tap")
                        haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showSheet = true
                    },
                    onTap = {
                        onUiSound("tap")
                        haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                )
                Box(Modifier.heightIn(min = 26.dp), contentAlignment = Alignment.Center) {
                    val line = state.feed.lastOrNull()
                    if (line != null && state.banner == null && state.introShown == null) FeedChip(line)
                    // HK-013: the status line is a polite live region — TalkBack announces the turn
                    // and any error without the player having to explore the table to find it.
                    Box(
                        Modifier
                            .size(1.dp)
                            .alpha(0.04f)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = HokmA11y.turnLabel(state) + " — " + HokmA11y.tableLabel(state)
                            },
                    )
                }
                if (topSeat != null) {
                    SeatWithBubble(state, topSeat)
                    Spacer(Modifier.height(6.dp))
                    OpponentHand(count = state.handCounts.getOrElse(topSeat) { 0 }, rotation = 180f, horizontal = true)
                } else {
                    // Three players: relative offset 2 is upper-left, offset 1 upper-right.
                    val leftSeat = HokmSeatLayout.seatAtOffset(state.mySeat, 2, state.playerCount)
                    val rightSeat = HokmSeatLayout.seatAtOffset(state.mySeat, 1, state.playerCount)
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SeatWithBubble(state, leftSeat)
                            Spacer(Modifier.height(6.dp))
                            OpponentHand(count = state.handCounts.getOrElse(leftSeat) { 0 }, rotation = 150f, horizontal = true)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SeatWithBubble(state, rightSeat)
                            Spacer(Modifier.height(6.dp))
                            OpponentHand(count = state.handCounts.getOrElse(rightSeat) { 0 }, rotation = 210f, horizontal = true)
                        }
                    }
                }
            }

            // ---- side opponents (four players only)
            if (state.playerCount == 4) {
                val rightSeat = HokmSeatLayout.seatAtOffset(state.mySeat, 1, state.playerCount)
                val leftSeat = HokmSeatLayout.seatAtOffset(state.mySeat, 3, state.playerCount)
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SeatWithBubble(state, rightSeat)
                    Spacer(Modifier.height(6.dp))
                    OpponentHand(count = state.handCounts.getOrElse(rightSeat) { 0 }, rotation = 270f, horizontal = false)
                }
                Column(Modifier.align(Alignment.CenterStart).padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    SeatWithBubble(state, leftSeat)
                    Spacer(Modifier.height(6.dp))
                    OpponentHand(count = state.handCounts.getOrElse(leftSeat) { 0 }, rotation = 90f, horizontal = false)
                }
            }

            // ---- two players: the stock sits beside the table centre while drawing
            if (state.playerCount == 2 && state.handPhase == HandPhase.DRAWING && state.stockCount > 0) {
                StockPile(state.stockCount, Modifier.align(Alignment.CenterStart).padding(start = 18.dp))
            }

            // ---- trick area
            TrickArea(
                state = state,
                cardWidth = tableCardW,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(w * 0.62f, h * 0.36f)
                    .offset(y = (-12).dp),
            )

            // ---- my hand
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StickerButton(onClick = { stickerTray = !stickerTray }, onTap = { onUiSound("tap") })
                    SeatWithBubble(state, state.mySeat)
                }
                Spacer(Modifier.height(4.dp))
                HandFan(
                    cards = state.myHand,
                    legal = if (state.iDiscard) state.myHand.toSet() else state.legalCards,
                    enabled = (state.isMyTurn && !state.iDraw && !state.iChooseHokm) || state.iDiscard,
                    selected = state.selectedForDiscard,
                    cardWidth = handCardW,
                    onTap = { card ->
                        haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (state.iDiscard) onToggleDiscard(card) else onPlayCard(card)
                    },
                )
            }

            // ---- overlays
            if (state.iChooseHokm) {
                HokmChooser(
                    hand = state.myHand,
                    onChoose = onChooseHokm,
                    onTap = { onUiSound("tap") },
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            if (state.iDiscard) {
                DiscardPanel(
                    need = state.discardCount,
                    selected = state.selectedForDiscard.size,
                    onConfirm = onConfirmDiscard,
                    onTap = { onUiSound("tap") },
                    modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp),
                )
            }
            if (state.iDraw) {
                DrawPanel(
                    revealed = state.revealed!!,
                    stockCount = state.stockCount,
                    onKeep = onKeepCard,
                    onPass = onPassCard,
                    onTap = { onUiSound("tap") },
                    modifier = Modifier.align(Alignment.Center).offset(y = (-20).dp),
                )
            }
            if (stickerTray) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.36f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { stickerTray = false },
                    contentAlignment = Alignment.Center,
                ) {
                    StickerTray(
                        onPick = { stickerTray = false; onSticker(it) },
                        onDismiss = { stickerTray = false },
                        onTap = { onUiSound("tap") },
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { },
                    )
                }
            }
            state.introShown?.let { shown ->
                HakemDrawOverlay(state, shown, Modifier.align(Alignment.Center))
            }
            if (state.dealing) DealingOverlay(state, Modifier.align(Alignment.Center))

            FxOverlay(state, tableCardWidth = tableCardW, modifier = Modifier.fillMaxSize())

            BannerOverlay(state.banner, Modifier.align(Alignment.Center).offset(y = (-40).dp))

            // ---- turn timer (rooms only) + last-trick peek, stacked above my hand on the left
            Column(
                Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.turnDeadlineMs > 0L && state.turnSeconds > 0 && !state.finished) {
                    TurnTimer(state, haptics = haptics, onSound = onUiSound)
                }
                if (state.lastTrick.isNotEmpty() && state.handPhase == HandPhase.PLAYING) {
                    RoundIconButton(text = "↶", onClick = { showLastTrick = true }, onTap = { onUiSound("tap") })
                }
            }

            if (showLastTrick) {
                LastTrickOverlay(
                    state,
                    tableCardW,
                    onDismiss = { showLastTrick = false },
                    onTap = { onUiSound("tap") },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (showSheet) {
                ScoreSheetOverlay(
                    state,
                    onDismiss = { showSheet = false },
                    onTap = { onUiSound("tap") },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            state.error?.let { ErrorChip(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp)) }

            if (state.finished) {
                GameOverDialog(
                    state = state,
                    onPlayAgain = onPlayAgain,
                    onExit = onExit,
                    playAgainAvailable = playAgainAvailable,
                    onTap = { onUiSound("tap") },
                )
            }
            if (confirmExit && !state.finished) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AlertDialog(
                        onDismissRequest = { confirmExit = false },
                        title = { Text("خروج از بازی؟", textAlign = TextAlign.Right) },
                        text = { Text("اگر بیرون بروی این بازی از دست می‌رود.", textAlign = TextAlign.Right) },
                        containerColor = HokmPalette.deepGreen,
                        titleContentColor = HokmPalette.goldLight,
                        textContentColor = HokmPalette.text,
                        confirmButton = {
                            Hokm3DButton(
                                text = "خروج",
                                tone = HokmButtonTone.CORAL,
                                height = 42.dp,
                                modifier = Modifier.widthIn(min = 86.dp),
                                onSound = { onUiSound("tap") },
                                onClick = onExit,
                            )
                        },
                        dismissButton = {
                            Hokm3DButton(
                                text = "ادامه",
                                tone = HokmButtonTone.GLASS,
                                height = 42.dp,
                                modifier = Modifier.widthIn(min = 100.dp),
                                onSound = { onUiSound("tap") },
                                onClick = { confirmExit = false },
                            )
                        },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------- felt

/**
 * The table itself, drawn with vectors (GX-01 "Emerald Royale", HK-015 sample): brass rim with
 * girih-lite diamond ticks and turquoise inlays, emerald felt with one focused spotlight in the
 * centre (from direction B) that never darkens the card area, and the brass medallion rings.
 * Vector instead of the old photograph: crisp at every size and the spotlight stays centred.
 */
@Composable
private fun TableBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val rim = 14.dp.toPx()
        // brass rim
        drawRect(Brush.verticalGradient(listOf(Table.rimLight, Table.rimDark)))
        // girih-lite: tiny diamond ticks along the rim band
        val tick = 5.dp.toPx()
        val tickStep = 22.dp.toPx()
        var tx = tickStep / 2f
        while (tx < size.width) {
            listOf(rim / 2f, size.height - rim / 2f).forEach { ty ->
                val p = androidx.compose.ui.graphics.Path().apply {
                    moveTo(tx, ty - tick / 2f)
                    lineTo(tx + tick / 2f, ty)
                    lineTo(tx, ty + tick / 2f)
                    lineTo(tx - tick / 2f, ty)
                    close()
                }
                drawPath(p, Color.Black.copy(alpha = 0.22f), style = Stroke(1.2f))
            }
            tx += tickStep
        }
        // emerald felt with the focused centre spotlight
        val inner = androidx.compose.ui.geometry.Rect(rim, rim, size.width - rim, size.height - rim)
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Table.feltLight, Table.feltDark, Color(HokmArtDirection.FELT_EDGE)),
                center = Offset(size.width / 2f, size.height * 0.45f),
                radius = size.height * 0.72f,
            ),
            topLeft = inner.topLeft,
            size = inner.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
        )
        // brass inlay line + turquoise inlay stones on the inner rim
        drawRoundRect(
            color = Table.brass.copy(alpha = 0.55f),
            topLeft = Offset(inner.left + 6.dp.toPx(), inner.top + 6.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(inner.width - 12.dp.toPx(), inner.height - 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.11f),
            style = Stroke(1.5.dp.toPx()),
        )
        val stoneR = 3.dp.toPx()
        val stoneY = listOf(inner.top + rim * 0.7f, inner.bottom - rim * 0.7f)
        var sx = inner.left + 34.dp.toPx()
        while (sx < inner.right - 20.dp.toPx()) {
            stoneY.forEach { sy -> drawCircle(Table.turquoise.copy(alpha = 0.85f), stoneR, Offset(sx, sy)) }
            sx += 68.dp.toPx()
        }
        // felt grain: very faint diagonal hatch
        val step = 7.dp.toPx()
        var x = -size.height
        while (x < size.width) {
            drawLine(Color.White.copy(alpha = 0.025f), Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1f)
            x += step
        }
        // central medallion
        val c = Offset(size.width / 2f, size.height * 0.45f)
        drawCircle(Table.brass.copy(alpha = 0.12f), size.width * 0.22f, c, style = Stroke(2.dp.toPx()))
        drawCircle(Table.brass.copy(alpha = 0.08f), size.width * 0.16f, c, style = Stroke(1.dp.toPx()))
    }
}

// --------------------------------------------------------------------------------------- seats

@Composable
private fun SeatBadge(state: HokmUiState, seat: Int, modifier: Modifier = Modifier) {
    val info = state.seats.getOrNull(seat) ?: return
    val isTurn = !state.finished && state.introShown == null && (
        (state.handPhase == HandPhase.PLAYING && state.turn == seat && state.collectingTo == null) ||
            (state.handPhase == HandPhase.CHOOSING_HOKM && state.hakem == seat)
        )
    val isHakem = state.hakem == seat
    val team = state.teamOf(seat)
    val teamColor = if (team == state.myTeam) Table.ours else Table.theirs

    val reducedMotion = LocalHokmReducedMotion.current
    val pulse = rememberInfiniteTransition(label = "turn")
    val animatedGlow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    // HK-013.a: in reduced-motion mode the turn border is steady, not pulsing.
    val glow = if (HokmMotion.infiniteMotionEnabled(reducedMotion)) animatedGlow else 0.85f

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier
                // HK-013: a seat is one focusable, self-describing line for TalkBack.
                .semantics(mergeDescendants = true) {
                    contentDescription = HokmA11y.seatLabel(
                        seat = seat,
                        name = info.name,
                        cardCount = state.handCounts.getOrElse(seat) { 0 },
                        tricksWon = state.tricksWon.getOrElse(if (state.isTeamGame) state.teamOf(seat) else seat) { 0 },
                        isTurn = isTurn,
                        isHakem = isHakem,
                        isMe = seat == state.mySeat,
                    )
                }
                .clip(RoundedCornerShape(22.dp))
                .background(Table.chip)
                .border(
                    width = if (isTurn) 2.dp else 1.dp,
                    color = if (isTurn) Table.turnGlow.copy(alpha = glow) else teamColor.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(22.dp),
                )
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(teamColor, teamColor.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(info.name.take(1), color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(info.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(
                    when {
                        state.isTeamGame -> if (team == state.myTeam) "تیم ما" else "حریف"
                        seat == state.mySeat -> "شما"
                        else -> "${state.scores.getOrElse(team) { 0 }.toFa()} امتیاز"
                    },
                    color = teamColor,
                    fontSize = 11.sp,
                )
            }
            if (isHakem) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Table.brass)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text("👑 حاکم", color = Color(0xFF3E2410), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (info.isBot && state.introShown == null && !state.dealing) {
                val hint = when {
                    state.handPhase == HandPhase.CHOOSING_HOKM && isHakem -> "در حال انتخاب حکم…"
                    state.handPhase == HandPhase.DISCARDING && state.turn == seat -> "دارد کارت می‌سوزاند…"
                    state.handPhase == HandPhase.DRAWING && state.turn == seat -> "دارد کارت برمی‌دارد…"
                    else -> null
                }
                if (hint != null) Text(hint, color = Table.turnGlow, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun OpponentHand(count: Int, rotation: Float, horizontal: Boolean, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val cardW = 30.dp
    val step = 9.dp
    val n = count.coerceAtMost(13)
    val cardH = cardW / CARD_ASPECT
    val span = step * (n - 1)
    val boxW = if (horizontal) cardW + span else cardH
    val boxH = if (horizontal) cardH else cardH + span
    Box(modifier.size(boxW, boxH), contentAlignment = Alignment.Center) {
        for (i in 0 until n) {
            val off = (i - (n - 1) / 2f) * step.value
            CardBackView(
                width = cardW,
                elevation = 2.dp,
                modifier = Modifier
                    .offset(x = if (horizontal) off.dp else 0.dp, y = if (horizontal) 0.dp else off.dp)
                    .rotate(rotation + (i - (n - 1) / 2f) * 1.5f),
            )
        }
    }
}

// --------------------------------------------------------------------------------------- trick

@Composable
private fun TrickArea(state: HokmUiState, cardWidth: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cw = maxWidth
        val ch = maxHeight
        fun slot(seat: Int): Pair<Dp, Dp> {
            val direction = HokmSeatLayout.direction(seat, state.mySeat, state.playerCount)
            return cw * 0.26f * direction.x to ch * 0.22f * direction.y
        }

        fun origin(seat: Int): Pair<Dp, Dp> {
            val direction = HokmSeatLayout.direction(seat, state.mySeat, state.playerCount)
            return cw * 0.9f * direction.x to ch * 1.4f * direction.y
        }

        val collectTo = state.collectingTo
        val reducedMotion = LocalHokmReducedMotion.current
        val collect by animateFloatAsState(
            targetValue = if (collectTo != null) 1f else 0f,
            animationSpec = tween(HokmMotion.durationMs(480, reducedMotion), easing = FastOutSlowInEasing),
            label = "collect",
        )

        state.tableCards.forEach { play ->
            key(play.card.code, state.handNumber, state.trickNumber) {
                TableCard(
                    play = play,
                    cardWidth = cardWidth,
                    slot = slot(play.seat),
                    origin = origin(play.seat),
                    collectTarget = collectTo?.let { origin(it) },
                    collect = collect,
                )
            }
        }
    }
}

@Composable
private fun TableCard(
    play: Play,
    cardWidth: Dp,
    slot: Pair<Dp, Dp>,
    origin: Pair<Dp, Dp>,
    collectTarget: Pair<Dp, Dp>?,
    collect: Float,
) {
    val progress = remember { Animatable(0f) }
    val reducedMotion = LocalHokmReducedMotion.current
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(HokmMotion.durationMs(300, reducedMotion), easing = FastOutSlowInEasing))
    }
    val p = progress.value
    val baseX = origin.first + (slot.first - origin.first) * p
    val baseY = origin.second + (slot.second - origin.second) * p
    val x = if (collectTarget != null) baseX + (collectTarget.first - baseX) * collect else baseX
    val y = if (collectTarget != null) baseY + (collectTarget.second - baseY) * collect else baseY
    // A believable, slightly careless throw: a little tilt that depends on the card, not on chance.
    val tilt = (play.card.rank.ordinal % 5 - 2) * 3f + (play.seat * 7 % 11 - 5)
    PlayingCardView(
        card = play.card,
        width = cardWidth,
        elevation = 8.dp,
        modifier = Modifier
            .offset(x = x, y = y)
            .rotate(tilt)
            .scale(0.85f + 0.15f * p)
            .alpha(1f - collect * 0.9f),
    )
}

// --------------------------------------------------------------------------------------- hand

@Composable
private fun HandFan(
    cards: List<Card>,
    legal: Set<Card>,
    enabled: Boolean,
    cardWidth: Dp,
    onTap: (Card) -> Unit,
    selected: Set<Card> = emptySet(),
) {
    val reducedMotion = LocalHokmReducedMotion.current
    BoxWithConstraints(Modifier.fillMaxWidth().height(cardWidth / CARD_ASPECT + 26.dp)) {
        val n = cards.size
        if (n == 0) return@BoxWithConstraints
        val available = maxWidth - 24.dp
        val maxStep = cardWidth * 0.55f
        val step = if (n == 1) 0.dp else minOf(maxStep, (available - cardWidth) / (n - 1))
        val total = cardWidth + step * (n - 1)
        val startX = (maxWidth - total) / 2
        val mid = (n - 1) / 2f
        cards.forEachIndexed { i, card ->
            key(card.code) {
            val rel = i - mid
            val angle = rel * (if (n > 8) 2.2f else 3.5f)
            val lift = (abs(rel) * abs(rel) * 1.1f).dp
            val playable = enabled && card in legal
            val isSelected = card in selected
            val raise by animateFloatAsState(
                if (isSelected) 2.2f else if (playable) 1f else 0f,
                tween(HokmMotion.durationMs(180, reducedMotion)),
                label = "raise",
            )
            PlayingCardView(
                card = card,
                width = cardWidth,
                elevation = if (playable) 10.dp else 4.dp,
                dimmed = enabled && !playable,
                modifier = Modifier
                    .offset(x = startX + step * i, y = 20.dp + lift - (12.dp * raise))
                    .rotate(angle)
                    // HK-013: TalkBack must be able to play — the card is named, its legality is
                    // spoken, and the touch target never falls below the accessible minimum.
                    .sizeIn(minWidth = MIN_TOUCH_DP, minHeight = MIN_TOUCH_DP)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "بازی کردن ${HokmA11y.cardLabel(card)}",
                        onClick = { onTap(card) },
                    )
                    .semantics {
                        contentDescription = HokmA11y.handCardLabel(card, legal = playable, selected = isSelected)
                    },
            )
            }
        }
    }
}

// --------------------------------------------------------------------------------------- score bar

@Composable
private fun ScoreBar(
    state: HokmUiState,
    onExit: () -> Unit,
    onSheet: () -> Unit = {},
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xDD12372D), Color(0xE608211A))))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .clickable(onClick = onSheet)
                // HK-013: the score bar speaks the score line, and its tap is named.
                .semantics(mergeDescendants = true) {
                    contentDescription = HokmA11y.scoreLabel(state.scores, state.myTeam, state.targetPoints)
                    onClick(label = "باز کردن کارنامهٔ دست‌ها") { onSheet(); true }
                }
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Hokm3DButton(
                text = "خروج",
                leading = "‹",
                tone = HokmButtonTone.GLASS,
                height = 39.dp,
                modifier = Modifier.widthIn(min = 74.dp),
                onSound = onTap,
                onClick = onExit,
            )
            Spacer(Modifier.width(5.dp))
            if (state.isTeamGame) {
                val my = state.myTeam
                TeamScore("ما", state.scores.getOrElse(my) { 0 }, state.tricksWon.getOrElse(my) { 0 }, Table.ours)
                Spacer(Modifier.weight(1f))
                HokmBadge(state)
                Spacer(Modifier.weight(1f))
                TeamScore("حریف", state.scores.getOrElse(1 - my) { 0 }, state.tricksWon.getOrElse(1 - my) { 0 }, Table.theirs)
            } else {
                // Everybody alone: one compact column per player, me first.
                val order = listOf(state.mySeat) + (0 until state.playerCount).filter { it != state.mySeat }
                order.forEach { seat ->
                    TeamScore(
                        label = if (seat == state.mySeat) "شما" else state.seats.getOrNull(seat)?.name?.take(6) ?: "",
                        score = state.scores.getOrElse(seat) { 0 },
                        tricks = state.tricksWon.getOrElse(seat) { 0 },
                        color = if (seat == state.mySeat) Table.ours else Table.theirs,
                    )
                    Spacer(Modifier.weight(1f))
                }
                HokmBadge(state)
            }
        }
    }
}

@Composable
private fun HokmBadge(state: HokmUiState) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            // GX-01: the trump lives in a brass medallion with a turquoise stone once chosen.
            val hokm = state.hokm
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Table.rimLight, Table.rimDark)))
                    .border(1.dp, Table.brass, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (hokm != null) {
                    SuitIcon(hokm, 19.dp, onDark = true)
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Table.turquoise),
                    )
                } else {
                    Text("؟", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            Text("حکم:", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            if (hokm != null) {
                Text(hokm.titleFa, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } else {
                Text("—", color = Color.White, fontSize = 13.sp)
            }
        }
        Text(
            "دست ${state.handNumber.toFa()} • تا ${state.targetPoints.toFa()} امتیاز",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun TeamScore(label: String, score: Int, tricks: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(score.toFa(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(HokmRules.TRICKS_TO_WIN_HAND) { i ->
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (i < tricks) color else Color.White.copy(alpha = 0.18f)),
                )
            }
        }
    }
}

@Composable
fun SuitIcon(suit: Suit, size: Dp, onDark: Boolean = false, modifier: Modifier = Modifier) {
    val color = if (onDark && suit.inkColor == CardColors.black) Color.White else suit.inkColor
    Canvas(modifier.size(size)) {
        drawSuit(suit, Offset(this.size.width / 2f, this.size.height / 2f), this.size.minDimension, color)
    }
}

// --------------------------------------------------------------------------------------- overlays

@Composable
private fun HokmChooser(
    hand: List<Card>,
    onChoose: (Suit) -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HokmGlassPanel(
            modifier.padding(14.dp).fillMaxWidth(0.94f),
            contentPadding = 15.dp,
        ) {
            Text("حاکم هستی؛ حکم را انتخاب کن", color = HokmPalette.text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(3.dp))
            Text("پنج کارت اولت را ببین و خالِ حکم را بگو", color = HokmPalette.muted, fontSize = 11.sp)
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                hand.forEach { PlayingCardView(it, width = 48.dp, elevation = 5.dp) }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Suit.entries.forEach { suit ->
                    val count = hand.count { it.suit == suit }
                    val suitColor = if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) Color(0xFFF27B78) else HokmPalette.ivory
                    Hokm3DButton(
                        text = suit.titleFa,
                        subtitle = "${count.toFa()} برگ",
                        leading = suit.symbol,
                        leadingColor = suitColor,
                        tone = HokmButtonTone.GLASS,
                        height = 66.dp,
                        modifier = Modifier.weight(1f),
                        onSound = onTap,
                        onClick = { onChoose(suit) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HakemDrawOverlay(state: HokmUiState, shown: Int, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier
                .padding(16.dp)
                .fillMaxWidth(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xE6102A1A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Table.brass.copy(alpha = 0.6f)),
        ) {
            Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("تعیین حاکم", color = Table.brass, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("کارت‌ها یکی‌یکی رو می‌شوند؛ اولین آس حاکم را مشخص می‌کند", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                val visible = state.hakemDraw.take(shown)
                visible.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        row.forEach { play ->
                            val isAce = play.card.rank == ir.dorehami.games.hokm.Rank.ACE
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PlayingCardView(
                                    play.card,
                                    width = 44.dp,
                                    elevation = if (isAce) 12.dp else 3.dp,
                                    modifier = if (isAce) Modifier.border(2.dp, Table.turnGlow, RoundedCornerShape(4.dp)) else Modifier,
                                )
                                Text(state.seats[play.seat].name, color = Color.White, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DealingOverlay(state: HokmUiState, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "deal")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "p")
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        // deck
        repeat(4) { i -> CardBackView(width = 56.dp, elevation = 2.dp, modifier = Modifier.offset(y = (-i).dp)) }
        // Fly one card toward each real seat in turn, keeping the local player at the bottom.
        // HK-013.a: reduced motion shows the deck and the caption, without the flying card.
        if (HokmMotion.infiniteMotionEnabled(LocalHokmReducedMotion.current)) {
            val step = phase * state.playerCount
            val relativeSeat = step.toInt() % state.playerCount
            val seat = HokmSeatLayout.seatAtOffset(state.mySeat, relativeSeat, state.playerCount)
            val direction = HokmSeatLayout.direction(seat, state.mySeat, state.playerCount)
            val f = step % 1f
            val dx = 90f * direction.x * f
            val dy = 90f * direction.y * f
            CardBackView(width = 56.dp, elevation = 6.dp, modifier = Modifier.offset(dx.dp, dy.dp).alpha(1f - f * 0.8f))
        }
        Text(
            "در حال پخش کارت‌ها…",
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).clip(RoundedCornerShape(10.dp)).background(Table.chip).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun BannerOverlay(banner: Banner?, modifier: Modifier = Modifier) {
    var last by remember { mutableStateOf(banner) }
    if (banner != null) last = banner
    AnimatedVisibility(
        visible = banner != null,
        enter = fadeIn() + scaleIn(initialScale = 0.7f),
        exit = fadeOut() + scaleOut(targetScale = 0.9f),
        modifier = modifier,
    ) {
        val b = last ?: return@AnimatedVisibility
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xF2102A1A), Color(0xF21B3F2A))))
                    .border(1.5.dp, Table.brass, RoundedCornerShape(20.dp))
                    .padding(horizontal = 26.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(b.title, color = Table.brass, fontWeight = FontWeight.Black, fontSize = if (b.big) 24.sp else 18.sp, textAlign = TextAlign.Center)
                b.subtitle?.let { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, textAlign = TextAlign.Center) }
            }
        }
    }
}

@Composable
private fun FeedChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.9f),
        fontSize = 12.sp,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Table.chip)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
private fun ErrorChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xE6B3202B))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun GameOverDialog(
    state: HokmUiState,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
    playAgainAvailable: Boolean,
    onTap: () -> Unit,
) {
    val won = state.winnerTeam == state.myTeam
    val my = state.scores.getOrElse(state.myTeam) { 0 }
    val them = if (state.isTeamGame) state.scores.getOrElse(1 - state.myTeam) { 0 } else state.scores.filterIndexed { i, _ -> i != state.myTeam }.maxOrNull() ?: 0
    val winnerName = state.winnerTeam?.let { state.seats.getOrNull(it)?.name } ?: ""
    val gameOverMessage = when {
        !playAgainAvailable -> "این میز تمام شد؛ برای دور بعدی به منو برگرد."
        won -> "دست مریزاد! یک بازی دیگر؟"
        else -> "بازی بعدی مال ماست. دوباره؟"
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = HokmPalette.deepGreen,
            titleContentColor = HokmPalette.goldLight,
            textContentColor = HokmPalette.text,
            title = {
                Text(
                    if (won) (if (state.isTeamGame) "🏆 بردیم!" else "🏆 بردی!") else (if (state.isTeamGame) "این بار باختیم" else "این بار $winnerName برد"),
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("${my.toFa()} — ${them.toFa()}", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text(
                        if (state.isTeamGame) "تیم ما — تیم حریف" else if (won) "شما — بهترین حریف" else "شما — $winnerName",
                        color = HokmPalette.muted,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(gameOverMessage, textAlign = TextAlign.Center)
                }
            },
            confirmButton = {
                Hokm3DButton(
                    text = if (playAgainAvailable) "بازی دوباره" else "بازگشت",
                    leading = if (playAgainAvailable) "↻" else "‹",
                    tone = if (playAgainAvailable) HokmButtonTone.EMERALD else HokmButtonTone.GLASS,
                    height = 46.dp,
                    modifier = Modifier.widthIn(min = if (playAgainAvailable) 126.dp else 102.dp),
                    onSound = onTap,
                    onClick = if (playAgainAvailable) onPlayAgain else onExit,
                )
            },
            dismissButton = {
                if (playAgainAvailable) {
                    Hokm3DButton(
                        text = "بازگشت",
                        tone = HokmButtonTone.GLASS,
                        height = 46.dp,
                        modifier = Modifier.width(102.dp),
                        onSound = onTap,
                        onClick = onExit,
                    )
                }
            },
        )
    }
}


// --------------------------------------------------------------------------------------- two-player panels

@Composable
private fun StockPile(count: Int, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            repeat(minOf(5, 1 + count / 9)) { i -> CardBackView(width = 44.dp, elevation = 2.dp, modifier = Modifier.offset(x = (i * 1.5f).dp, y = (-i * 1.5f).dp)) }
        }
        Spacer(Modifier.height(4.dp))
        Text("${count.toFa()} برگ", color = Color.White, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Table.chip).padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

@Composable
private fun DiscardPanel(
    need: Int,
    selected: Int,
    onConfirm: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HokmGlassPanel(modifier.fillMaxWidth(0.9f), contentPadding = 18.dp) {
            Text("${need.toFa()} کارت بسوزان", color = HokmPalette.text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(3.dp))
            Text("کارت‌های کم‌ارزش را از دستت انتخاب کن", color = HokmPalette.muted, fontSize = 11.sp)
            Spacer(Modifier.height(11.dp))
            Hokm3DButton(
                text = if (selected == need) "بسوزان" else "${selected.toFa()} از ${need.toFa()} انتخاب شده",
                leading = "🔥",
                tone = HokmButtonTone.CORAL,
                enabled = selected == need,
                modifier = Modifier.fillMaxWidth(),
                onSound = onTap,
                onClick = onConfirm,
            )
        }
    }
}

@Composable
private fun DrawPanel(
    revealed: Card,
    stockCount: Int,
    onKeep: () -> Unit,
    onPass: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HokmGlassPanel(modifier.fillMaxWidth(0.92f), contentPadding = 16.dp) {
            Text("این کارت را می‌خواهی؟", color = HokmPalette.text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                key(revealed.code) { PlayingCardView(revealed, width = 76.dp, elevation = 12.dp) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CardBackView(width = 44.dp, elevation = 4.dp)
                    Text("${stockCount.toFa()} برگ", color = HokmPalette.muted, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(11.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Hokm3DButton(
                    text = "نگه می‌دارم",
                    subtitle = "همین کارت",
                    leading = "✓",
                    tone = HokmButtonTone.EMERALD,
                    height = 54.dp,
                    modifier = Modifier.weight(1f),
                    onSound = onTap,
                    onClick = onKeep,
                )
                Hokm3DButton(
                    text = "رد می‌کنم",
                    subtitle = "بعدی را کور بردار",
                    leading = "↻",
                    tone = HokmButtonTone.GLASS,
                    height = 54.dp,
                    modifier = Modifier.weight(1f),
                    onSound = onTap,
                    onClick = onPass,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------- stickers

@Composable
private fun SeatWithBubble(state: HokmUiState, seat: Int) {
    val sticker = state.stickers.firstOrNull { it.seat == seat }
    Box(contentAlignment = Alignment.TopCenter) {
        SeatBadge(state, seat = seat, modifier = Modifier.padding(top = if (sticker != null) 30.dp else 0.dp))
        AnimatedVisibility(visible = sticker != null, enter = fadeIn() + scaleIn(initialScale = 0.5f), exit = fadeOut() + scaleOut()) {
            val s = sticker ?: return@AnimatedVisibility
            key(s.stamp) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(s.sticker.emoji, fontSize = 20.sp)
                    Text(s.sticker.captionFa, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StickerButton(onClick: () -> Unit, onTap: () -> Unit) {
    Hokm3DButton(
        text = "☺",
        tone = HokmButtonTone.GLASS,
        height = 42.dp,
        modifier = Modifier.width(45.dp),
        onSound = onTap,
        onClick = onClick,
    )
}

@Composable
private fun StickerTray(
    onPick: (Sticker) -> Unit,
    onDismiss: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        HokmGlassPanel(modifier.fillMaxWidth(0.94f), contentPadding = 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("استیکر بفرست", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("واکنش سریع سر میز", color = HokmPalette.muted, fontSize = 10.sp)
                }
                Hokm3DButton(
                    text = "بستن",
                    tone = HokmButtonTone.GLASS,
                    height = 40.dp,
                    modifier = Modifier.widthIn(min = 76.dp),
                    onSound = onTap,
                    onClick = onDismiss,
                )
            }
            Spacer(Modifier.height(7.dp))
            Stickers.all.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                    row.forEach { sticker ->
                        Hokm3DButton(
                            text = sticker.emoji,
                            subtitle = sticker.captionFa,
                            tone = HokmButtonTone.GLASS,
                            height = 52.dp,
                            modifier = Modifier.weight(1f),
                            onSound = onTap,
                            onClick = { onPick(sticker) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}


// --------------------------------------------------------------------------------------- effects

/** Particle bursts: gold sparks on a cut, a sparkle on each trick, a big burst on kot, confetti on a win. */
@Composable
private fun FxOverlay(state: HokmUiState, tableCardWidth: Dp, modifier: Modifier = Modifier) {
    // HK-013.a: particles/confetti are decoration; reduced motion drops them entirely.
    // The information they carried (a cut, a won trick, a kot) stays on the banner and score.
    if (HokmMotion.particleCount(1, LocalHokmReducedMotion.current) == 0) return
    if (state.fx.isEmpty()) return
    BoxWithConstraints(modifier) {
        val cw = maxWidth
        val ch = maxHeight
        state.fx.forEach { fx ->
            key(fx.stamp) {
                val centre = if (fx.seat < 0) 0.5f to 0.45f else {
                    val direction = HokmSeatLayout.direction(fx.seat, state.mySeat, state.playerCount)
                    (0.5f + 0.26f * direction.x) to (0.45f + 0.18f * direction.y)
                }
                when (fx.kind) {
                    FxKind.CUT -> Burst(
                        cx = cw * centre.first, cy = ch * centre.second,
                        count = 18, radius = tableCardWidth * 1.6f, durationMs = 900,
                        colors = listOf(Color(0xFFFFE082), Color(0xFFFFC107), Color.White),
                        label = "بُرید!",
                    )
                    FxKind.TRICK_WIN -> Burst(
                        cx = cw * centre.first, cy = ch * centre.second,
                        count = 8, radius = tableCardWidth, durationMs = 700,
                        colors = listOf(Color.White.copy(alpha = 0.9f), Table.brass),
                    )
                    FxKind.KOT -> Burst(
                        cx = cw * 0.5f, cy = ch * 0.45f,
                        count = 48, radius = cw * 0.55f, durationMs = 2200,
                        colors = listOf(Color(0xFFFF7043), Color(0xFFFFD54F), Color(0xFFEF5350), Color.White),
                    )
                    FxKind.CONFETTI -> Confetti(cw, ch, durationMs = 3800)
                }
            }
        }
    }
}

@Composable
private fun Burst(
    cx: Dp,
    cy: Dp,
    count: Int,
    radius: Dp,
    durationMs: Int,
    colors: List<Color>,
    label: String? = null,
) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    val seeds = remember { List(count) { i -> Triple(i * (360f / count) + Random.nextFloat() * 12f, 0.6f + Random.nextFloat() * 0.4f, colors[i % colors.size]) } }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val c = Offset(cx.toPx(), cy.toPx())
        val r = radius.toPx()
        seeds.forEach { (deg, speed, color) ->
            val rad = Math.toRadians(deg.toDouble())
            val dist = r * speed * p
            val pos = Offset(c.x + (cos(rad) * dist).toFloat(), c.y + (sin(rad) * dist).toFloat() + p * p * r * 0.25f)
            val size = (6f - 5f * p).coerceAtLeast(0.5f).dp.toPx()
            drawCircle(color.copy(alpha = (1f - p).coerceIn(0f, 1f)), size, pos)
        }
        // central flash
        if (p < 0.35f) drawCircle(Color.White.copy(alpha = (0.35f - p) * 1.5f), r * 0.35f * (0.5f + p), c)
    }
    if (label != null) {
        val scale = 0.6f + 0.6f * t.value
        Box(
            Modifier
                .offset(x = cx - 30.dp, y = cy - 14.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; alpha = (1f - t.value * 1.1f).coerceIn(0f, 1f) },
        ) {
            Text(label, color = Color(0xFFFFE082), fontWeight = FontWeight.Black, fontSize = 20.sp)
        }
    }
}

@Composable
private fun Confetti(w: Dp, h: Dp, durationMs: Int) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val palette = listOf(Color(0xFFEF5350), Color(0xFF42A5F5), Color(0xFFFFD54F), Color(0xFF66BB6A), Color(0xFFAB47BC), Color.White)
    val bits = remember {
        List(90) {
            floatArrayOf(Random.nextFloat(), Random.nextFloat() * -0.6f, 0.5f + Random.nextFloat(), Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 0.2f, it.toFloat())
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val W = w.toPx()
        val H = h.toPx()
        bits.forEach { b ->
            val y = (b[1] + p * b[2] * 1.4f) * H
            if (y < -20f || y > H + 20f) return@forEach
            val x = (b[0] + sin(p * 6f + b[5]) * 0.03f + b[4] * p) * W
            val rot = b[3] + p * 720f * (b[2] - 0.5f)
            val color = palette[b[5].toInt() % palette.size].copy(alpha = (1f - (p - 0.8f) * 5f).coerceIn(0f, 1f))
            drawScopeRotate(rot, Offset(x, y)) {
                drawRect(color, topLeft = Offset(x - 4.dp.toPx(), y - 2.5.dp.toPx()), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 5.dp.toPx()))
            }
        }
    }
}

// --------------------------------------------------------------------------------------- timer

/**
 * Countdown ring for rooms with a move limit. Counts the *host's* deadline (already translated to
 * this device's clock by the session layer). Ticks in the last five seconds, warns at two.
 */
@Composable
private fun TurnTimer(state: HokmUiState, haptics: androidx.compose.ui.hapticfeedback.HapticFeedback?, onSound: (String) -> Unit) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.turnDeadlineMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(100)
        }
    }
    val total = state.turnSeconds * 1000f
    val left = (state.turnDeadlineMs - now).coerceIn(0L, total.toLong())
    val frac = if (total <= 0f) 0f else left / total
    val secs = ((left + 999) / 1000).toInt()
    val mine = state.isMyTurn
    LaunchedEffect(secs, state.turnDeadlineMs, mine) {
        if (!mine) return@LaunchedEffect
        when {
            secs in 1..2 -> { onSound("timer_warn"); haptics?.performHapticFeedback(HapticFeedbackType.LongPress) }
            secs in 3..5 -> onSound("timer_tick")
        }
    }
    val color = when {
        !mine -> Color.White.copy(alpha = 0.55f)
        secs <= 5 -> Color(0xFFEF5350)
        secs <= 10 -> Color(0xFFFFB300)
        else -> Table.turnGlow
    }
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            drawCircle(Color.Black.copy(alpha = 0.45f))
            drawArc(Color.White.copy(alpha = 0.15f), -90f, 360f, useCenter = false, style = stroke)
            drawArc(color, -90f, 360f * frac, useCenter = false, style = stroke)
        }
        Text(secs.toFa(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun RoundIconButton(text: String, onClick: () -> Unit, onTap: () -> Unit) {
    Hokm3DButton(
        text = text,
        tone = HokmButtonTone.GLASS,
        height = 42.dp,
        modifier = Modifier.width(46.dp),
        textColor = Table.brass,
        onSound = onTap,
        onClick = onClick,
    )
}

// --------------------------------------------------------------------------------------- overlays

/** The previous trick, laid out exactly like the table, with the winner highlighted. */
@Composable
private fun LastTrickOverlay(state: HokmUiState, cardWidth: Dp, onDismiss: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("دستِ قبلی", color = Table.brass, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            state.lastTrickWinner?.let { w ->
                val name = if (w == state.mySeat) "شما" else state.seats.getOrNull(w)?.name ?: ""
                Text("برنده: $name", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Spacer(Modifier.height(7.dp))
            Hokm3DButton(
                text = "بستن",
                tone = HokmButtonTone.GLASS,
                height = 40.dp,
                modifier = Modifier.widthIn(min = 82.dp),
                onSound = onTap,
                onClick = onDismiss,
            )
            Spacer(Modifier.height(7.dp))
            Box(Modifier.size(cardWidth * 4.2f, cardWidth * 4.6f), contentAlignment = Alignment.Center) {
                state.lastTrick.forEach { play ->
                    val direction = HokmSeatLayout.direction(play.seat, state.mySeat, state.playerCount)
                    val win = play.seat == state.lastTrickWinner
                    Box(
                        Modifier
                            .offset(x = cardWidth * 1.3f * direction.x, y = cardWidth * 1.45f * direction.y)
                            .then(if (win) Modifier.border(3.dp, Table.turnGlow, RoundedCornerShape(cardWidth * 0.09f)) else Modifier),
                    ) {
                        PlayingCardView(play.card, width = cardWidth, elevation = if (win) 10.dp else 4.dp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("برای بستن ضربه بزنید", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
        }
    }
}

/** The score sheet: one row per finished hand. */
@Composable
private fun ScoreSheetOverlay(state: HokmUiState, onDismiss: () -> Unit, onTap: () -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.88f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF164537), Color(0xFF09251D))))
                    .border(1.dp, Table.brass.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("برگهٔ امتیاز", color = Table.brass, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (state.modeLabel.isNotEmpty()) Text(state.modeLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                    Hokm3DButton(
                        text = "بستن",
                        tone = HokmButtonTone.GLASS,
                        height = 40.dp,
                        modifier = Modifier.widthIn(min = 76.dp),
                        onSound = onTap,
                        onClick = onDismiss,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "هدف: ${state.targetPoints.toFa()} امتیاز",
                    color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (state.handHistory.isEmpty()) {
                    Text("هنوز دستی تمام نشده.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        SheetCell("دست", Modifier.weight(0.7f), header = true)
                        SheetCell("حاکم", Modifier.weight(1.3f), header = true)
                        SheetCell("حکم", Modifier.weight(0.7f), header = true)
                        SheetCell("نتیجه", Modifier.weight(1.5f), header = true)
                        SheetCell("امتیاز", Modifier.weight(0.8f), header = true)
                    }
                    Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                        state.handHistory.forEachIndexed { i, hnd ->
                            val mine = hnd.winnerTeam == state.myTeam
                            val hakemName = if (hnd.hakem == state.mySeat) "شما" else state.seats.getOrNull(hnd.hakem)?.name ?: ""
                            val kind = when (hnd.kind) {
                                ir.dorehami.games.hokm.HandWinKind.KOT -> "کُت"
                                ir.dorehami.games.hokm.HandWinKind.HAKEM_KOT -> "حاکم‌کُت"
                                ir.dorehami.games.hokm.HandWinKind.VOID -> "بی‌نتیجه"
                                else -> hnd.tricks.joinToString(" – ") { it.toFa() }
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (i % 2 == 0) Color.White.copy(alpha = 0.04f) else Color.Transparent)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SheetCell((i + 1).toFa(), Modifier.weight(0.7f))
                                SheetCell(hakemName, Modifier.weight(1.3f))
                                Box(Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
                                    hnd.hokm?.let { SuitIcon(it, 16.dp, onDark = true) }
                                }
                                SheetCell(kind, Modifier.weight(1.5f), color = if (mine) Table.ours else Table.theirs)
                                SheetCell("+" + hnd.points.toFa(), Modifier.weight(0.8f), color = if (mine) Table.ours else Table.theirs)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    if (state.isTeamGame) {
                        Text("ما ${state.scores.getOrElse(state.myTeam) { 0 }.toFa()}", color = Table.ours, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text("حریف ${state.scores.getOrElse(1 - state.myTeam) { 0 }.toFa()}", color = Table.theirs, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    } else {
                        (0 until state.playerCount).forEach { seat ->
                            val n = if (seat == state.mySeat) "شما" else state.seats.getOrNull(seat)?.name?.take(6) ?: ""
                            Text("$n ${state.scores.getOrElse(seat) { 0 }.toFa()}", color = if (seat == state.mySeat) Table.ours else Table.theirs, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetCell(text: String, modifier: Modifier, header: Boolean = false, color: Color = Color.White) {
    Text(
        text,
        modifier = modifier,
        textAlign = TextAlign.Center,
        color = if (header) Table.brass else color,
        fontSize = if (header) 11.sp else 13.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
    )
}
