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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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

/** Table palette: tournament-green baize, walnut rim, brass accents. */
private object Table {
    val feltLight = Color(0xFF2F8A4E)
    val feltDark = Color(0xFF0F3D22)
    val rimLight = Color(0xFF8B5A2B)
    val rimDark = Color(0xFF3E2410)
    val brass = Color(0xFFD9B55A)
    val chip = Color(0x99000000)
    val turnGlow = Color(0xFFFFE082)
    val ours = Color(0xFF64B5F6)
    val theirs = Color(0xFFEF9A9A)
}

/**
 * The Hokm table. Pure presentation: reads [HokmUiState], reports taps through callbacks.
 * Geometry is laid out LTR (seats are physical positions); all text is still RTL Persian.
 */
@Composable
fun HokmScreen(
    state: HokmUiState,
    onPlayCard: (Card) -> Unit,
    onChooseHokm: (Suit) -> Unit,
    onClearError: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    var confirmExit by remember { mutableStateOf(false) }
    BackHandler { confirmExit = true }

    val haptics = LocalHapticFeedback.current

    LaunchedEffect(state.error) {
        if (state.error != null) {
            delay(1800)
            onClearError()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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

            FeltBackground()

            // ---- top: score bar, feed, partner
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScoreBar(state = state, onExit = { confirmExit = true })
                Box(Modifier.height(26.dp), contentAlignment = Alignment.Center) {
                    val line = state.feed.lastOrNull()
                    if (line != null && state.banner == null && state.introShown == null) FeedChip(line)
                }
                SeatBadge(state, seat = 2)
                Spacer(Modifier.height(6.dp))
                OpponentHand(count = state.handCounts.getOrElse(2) { 0 }, rotation = 180f, horizontal = true)
            }

            // ---- side opponents
            Column(Modifier.align(Alignment.CenterEnd).padding(end = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SeatBadge(state, seat = 1)
                Spacer(Modifier.height(6.dp))
                OpponentHand(count = state.handCounts.getOrElse(1) { 0 }, rotation = 270f, horizontal = false)
            }
            Column(Modifier.align(Alignment.CenterStart).padding(start = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SeatBadge(state, seat = 3)
                Spacer(Modifier.height(6.dp))
                OpponentHand(count = state.handCounts.getOrElse(3) { 0 }, rotation = 90f, horizontal = false)
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
                SeatBadge(state, seat = state.mySeat, modifier = Modifier.padding(bottom = 4.dp))
                HandFan(
                    cards = state.myHand,
                    legal = state.legalCards,
                    enabled = state.isMyTurn,
                    cardWidth = handCardW,
                    onTap = { card ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayCard(card)
                    },
                )
            }

            // ---- overlays
            if (state.iChooseHokm) {
                HokmChooser(
                    hand = state.myHand,
                    onChoose = onChooseHokm,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            state.introShown?.let { shown ->
                HakemDrawOverlay(state, shown, Modifier.align(Alignment.Center))
            }
            if (state.dealing) DealingOverlay(Modifier.align(Alignment.Center))

            BannerOverlay(state.banner, Modifier.align(Alignment.Center).offset(y = (-40).dp))

            state.error?.let { ErrorChip(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 180.dp)) }

            if (state.finished) {
                GameOverDialog(state, onPlayAgain = onPlayAgain, onExit = onExit)
            }
            if (confirmExit && !state.finished) {
                AlertDialog(
                    onDismissRequest = { confirmExit = false },
                    title = { Text("خروج از بازی؟", textAlign = TextAlign.Right) },
                    text = { Text("اگر بیرون بروی این بازی از دست می‌رود.", textAlign = TextAlign.Right) },
                    confirmButton = { TextButton(onClick = onExit) { Text("خروج") } },
                    dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("ادامهٔ بازی") } },
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------- felt

@Composable
private fun FeltBackground() {
    Canvas(Modifier.fillMaxSize()) {
        val rim = 14.dp.toPx()
        // walnut rim
        drawRect(Brush.verticalGradient(listOf(Table.rimLight, Table.rimDark)))
        // baize
        val inner = androidx.compose.ui.geometry.Rect(rim, rim, size.width - rim, size.height - rim)
        drawRoundRect(
            brush = Brush.radialGradient(
                colors = listOf(Table.feltLight, Table.feltDark),
                center = Offset(size.width / 2f, size.height * 0.45f),
                radius = size.height * 0.7f,
            ),
            topLeft = inner.topLeft,
            size = inner.size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.12f),
        )
        // brass inlay line
        drawRoundRect(
            color = Table.brass.copy(alpha = 0.55f),
            topLeft = Offset(inner.left + 6.dp.toPx(), inner.top + 6.dp.toPx()),
            size = androidx.compose.ui.geometry.Size(inner.width - 12.dp.toPx(), inner.height - 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.11f),
            style = Stroke(1.5.dp.toPx()),
        )
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
    val team = HokmRules.teamOf(seat)
    val teamColor = if (team == state.myTeam) Table.ours else Table.theirs

    val pulse = rememberInfiniteTransition(label = "turn")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier
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
                    if (team == state.myTeam) "تیم ما" else "حریف",
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
            if (state.handPhase == HandPhase.CHOOSING_HOKM && isHakem && info.isBot && state.introShown == null && !state.dealing) {
                Text("در حال انتخاب حکم…", color = Table.turnGlow, fontSize = 11.sp)
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
        fun slot(seat: Int): Pair<Dp, Dp> = when (seat) {
            0 -> 0.dp to ch * 0.22f
            1 -> cw * 0.26f to 0.dp
            2 -> 0.dp to -ch * 0.22f
            else -> -cw * 0.26f to 0.dp
        }

        fun origin(seat: Int): Pair<Dp, Dp> = when (seat) {
            0 -> 0.dp to ch * 1.4f
            1 -> cw * 0.9f to 0.dp
            2 -> 0.dp to -ch * 1.4f
            else -> -cw * 0.9f to 0.dp
        }

        val collectTo = state.collectingTo
        val collect by animateFloatAsState(
            targetValue = if (collectTo != null) 1f else 0f,
            animationSpec = tween(480, easing = FastOutSlowInEasing),
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
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(300, easing = FastOutSlowInEasing)) }
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
) {
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
            val raise by animateFloatAsState(if (playable) 1f else 0f, tween(180), label = "raise")
            PlayingCardView(
                card = card,
                width = cardWidth,
                elevation = if (playable) 10.dp else 4.dp,
                dimmed = enabled && !playable,
                modifier = Modifier
                    .offset(x = startX + step * i, y = 20.dp + lift - (12.dp * raise))
                    .rotate(angle)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTap(card) },
                    ),
            )
            }
        }
    }
}

// --------------------------------------------------------------------------------------- score bar

@Composable
private fun ScoreBar(state: HokmUiState, onExit: () -> Unit, modifier: Modifier = Modifier) {
    val my = state.myTeam
    val them = 1 - my
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Table.chip)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onExit, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                Text("خروج", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
            }
            Spacer(Modifier.width(4.dp))
            TeamScore("ما", state.scores.getOrElse(my) { 0 }, state.tricksWon.getOrElse(my) { 0 }, Table.ours)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("حکم:", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    val hokm = state.hokm
                    if (hokm != null) {
                        SuitIcon(hokm, 18.dp, onDark = true)
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
            Spacer(Modifier.weight(1f))
            TeamScore("حریف", state.scores.getOrElse(them) { 0 }, state.tricksWon.getOrElse(them) { 0 }, Table.theirs)
        }
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
private fun HokmChooser(hand: List<Card>, onChoose: (Suit) -> Unit, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier
                .padding(16.dp)
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xF2102A1A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Table.brass.copy(alpha = 0.6f)),
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("شما حاکم هستید — حکم را انتخاب کن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Spacer(Modifier.height(4.dp))
                Text("پنج کارت اولت را ببین و خال حکم را بگو", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
                    hand.forEach { PlayingCardView(it, width = 50.dp, elevation = 4.dp) }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Suit.entries.forEach { suit ->
                        val count = hand.count { it.suit == suit }
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .clickable { onChoose(suit) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SuitIcon(suit, 34.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(suit.titleFa, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${count.toFa()} برگ", color = Color.DarkGray, fontSize = 10.sp)
                        }
                    }
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
private fun DealingOverlay(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "deal")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing)), label = "p")
    Box(modifier.size(220.dp), contentAlignment = Alignment.Center) {
        // deck
        repeat(4) { i -> CardBackView(width = 56.dp, elevation = 2.dp, modifier = Modifier.offset(y = (-i).dp)) }
        // a card flying out to each seat in turn
        val dir = ((phase * 4).toInt() % 4)
        val f = (phase * 4) % 1f
        val dx = when (dir) { 1 -> 90f; 3 -> -90f; else -> 0f } * f
        val dy = when (dir) { 0 -> 90f; 2 -> -90f; else -> 0f } * f
        CardBackView(width = 56.dp, elevation = 6.dp, modifier = Modifier.offset(dx.dp, dy.dp).alpha(1f - f * 0.8f))
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
private fun GameOverDialog(state: HokmUiState, onPlayAgain: () -> Unit, onExit: () -> Unit) {
    val won = state.winnerTeam == state.myTeam
    val my = state.scores.getOrElse(state.myTeam) { 0 }
    val them = state.scores.getOrElse(1 - state.myTeam) { 0 }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    if (won) "🏆 بردیم!" else "این بار باختیم",
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("${my.toFa()} — ${them.toFa()}", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("تیم ما — تیم حریف", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (won) "دست مریزاد! یک بازی دیگر؟" else "بازی بعدی مال ماست. دوباره؟",
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = { Button(onClick = onPlayAgain) { Text("بازی دوباره") } },
            dismissButton = { TextButton(onClick = onExit) { Text("بازگشت") } },
        )
    }
}
