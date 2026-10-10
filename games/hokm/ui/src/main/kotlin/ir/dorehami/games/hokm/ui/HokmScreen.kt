package ir.dorehami.games.hokm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.HandEndMode
import ir.dorehami.games.hokm.HokmOptions
import ir.dorehami.games.hokm.HokmLazimMode
import ir.dorehami.games.hokm.Suit
import kotlin.math.abs

private val Gold = Color(0xFFE8C46A)
private val GoldDark = Color(0xFF8A6A1F)
private val TableScrim = Color(0x990C2412)

/** دادهٔ لازم برای شروع یک دستِ حکم (از صفحهٔ تنظیمات می‌آید). */
data class HokmLaunchConfig(
    val seed: Long,
    val options: HokmOptions = HokmOptions.default(),
    val soundEnabled: Boolean = true,
)

/**
 * صفحهٔ میزِ حکم.
 *
 * این صفحه فقط وضعیت را نشان می‌دهد و تصمیمِ کاربر را به [HokmViewModel] می‌فرستد؛ هیچ قاعده‌ای
 * اینجا پیاده نشده است (قواعد در `games/hokm/core` هستند).
 */
@Composable
fun HokmGameScreen(
    state: HokmUiState,
    onPlayCard: (String) -> Unit,
    onDeclareTrump: (Suit) -> Unit,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onToastShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = state.toast
    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(1_800)
            onToastShown()
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        TableBackdrop()

        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(state = state, onExit = onExit)
            TableArea(
                state = state,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            YourHand(
                state = state,
                onPlayCard = onPlayCard,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
        }

        // بنرِ کوت / پایانِ دست
        AnimatedVisibility(
            visible = state.banner != null,
            enter = scaleIn(tween(320)) + fadeIn(tween(320)),
            exit = scaleOut(tween(320)) + fadeOut(tween(320)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            state.banner?.let { BannerCard(it) }
        }

        // انتخابِ حکم
        if (state.youMustDeclareTrump) {
            TrumpPicker(state = state, onPick = onDeclareTrump)
        }

        // پایانِ بازی
        if (state.result != null) {
            ResultOverlay(result = state.result, state = state, onRestart = onRestart, onExit = onExit)
        }

        // پیامِ خطا
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp),
        ) {
            if (message != null) {
                Surface(
                    color = Color(0xCC40170F),
                    shape = RoundedCornerShape(14.dp),
                    contentColor = Color.White,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// پس‌زمینه و اجزای میز
// ---------------------------------------------------------------------------

@Composable
private fun TableBackdrop() {
    Image(
        painter = painterResource(id = R.drawable.hokm_table_felt),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color.Transparent, TableScrim),
                    radius = 900f,
                ),
            ),
    )
}

@Composable
private fun TopBar(state: HokmUiState, onExit: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onExit) {
            Text("خروج", color = Gold)
        }

        ScoreBoard(state = state)

        TrumpChip(state = state)
    }
}

@Composable
private fun ScoreBoard(state: HokmUiState) {
    Surface(
        color = Color(0xCC0F2A18),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TeamScore(
                label = if (state.yourTeam == 0) "ما" else "آن‌ها",
                score = state.scores.getOrNull(0) ?: 0,
                target = state.targetPoints,
                highlight = state.yourTeam == 0,
            )
            Text("•", color = Gold)
            TeamScore(
                label = if (state.yourTeam == 1) "ما" else "آن‌ها",
                score = state.scores.getOrNull(1) ?: 0,
                target = state.targetPoints,
                highlight = state.yourTeam == 1,
            )
        }
    }
}

@Composable
private fun TeamScore(label: String, score: Int, target: Int, highlight: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (highlight) Gold else Color(0xCCEFEFEF),
        )
        Text(
            text = "${persianDigits(score)}/${persianDigits(target)}",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) Gold else Color.White,
        )
    }
}

@Composable
private fun TrumpChip(state: HokmUiState) {
    Surface(
        color = if (state.trump != null) Color(0xCC2A1B0C) else Color(0x991C1C1C),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "حکم", fontSize = 11.sp, color = Color(0xCCFFFFFF))
            if (state.trump != null) {
                Text(text = state.trumpSymbol, fontSize = 18.sp, color = Gold)
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = Gold,
                )
            }
        }
    }
}

/** میز و همهٔ چیزهای روی آن: صندلی‌ها، دستِ جاری و خلاصهٔ دست. */
@Composable
private fun TableArea(
    state: HokmUiState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        val youSeat = state.youSeat
        val right = (youSeat + 1) % 4
        val top = (youSeat + 2) % 4
        val left = (youSeat + 3) % 4

        // لوزیِ راهنمای وسطِ میز
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 190.dp, height = 130.dp)
                .border(1.dp, Gold.copy(alpha = 0.18f), RoundedCornerShape(70.dp)),
        )

        SeatChip(
            seat = state.seats.firstOrNull { it.seat == top },
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -h * 0.36f),
        )
        SeatChip(
            seat = state.seats.firstOrNull { it.seat == left },
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -w * 0.36f, y = -h * 0.05f),
        )
        SeatChip(
            seat = state.seats.firstOrNull { it.seat == right },
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = w * 0.36f, y = -h * 0.05f),
        )

        TrickCards(
            state = state,
            modifier = Modifier.align(Alignment.Center),
        )

        TrickCounter(
            state = state,
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 78.dp),
        )
    }
}

/** کارت‌های روی زمین (دستِ جاری). */
@Composable
private fun TrickCards(state: HokmUiState, modifier: Modifier = Modifier) {
    var showResolved by remember { mutableStateOf(false) }
    val resolvedKey = state.lastTrick?.number
    LaunchedEffect(resolvedKey) {
        if (resolvedKey != null) {
            showResolved = true
            kotlinx.coroutines.delay(900)
            showResolved = false
        }
    }

    val plays = if (showResolved && state.lastTrick != null) state.lastTrick.plays else state.trick
    val fade by animateFloatAsState(
        targetValue = if (showResolved) 0.0f else 1f,
        animationSpec = tween(700),
        label = "trickFade",
    )

    Box(modifier = modifier.size(width = 150.dp, height = 120.dp)) {
        plays.forEach { play ->
            val rel = (play.seat - state.youSeat + 4) % 4
            val (dx, dy) = when (rel) {
                0 -> 0.dp to 26.dp
                1 -> -52.dp to 0.dp
                2 -> 0.dp to -26.dp
                else -> 52.dp to 0.dp
            }
            PlayingCard(
                card = play.card,
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(56.dp)
                    .offset(x = dx, y = dy)
                    .alpha(if (showResolved) fade else 1f)
                    .graphicsLayer { rotationZ = when (rel) { 1 -> -8f; 3 -> 8f; else -> 0f } },
                elevation = 8.dp,
            )
        }
    }
}

/** شمارشِ دست‌های هر تیم در این دست. */
@Composable
private fun TrickCounter(state: HokmUiState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xAA0F2A18),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "دست ${persianDigits(state.handNumber)}",
                fontSize = 11.sp,
                color = Color(0xCCFFFFFF),
            )
            Text(
                text = "${persianDigits(state.tricks.getOrNull(state.yourTeam) ?: 0)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Text(text = "برابر", fontSize = 10.sp, color = Color(0x99FFFFFF))
            Text(
                text = "${persianDigits(state.tricks.getOrNull(1 - state.yourTeam) ?: 0)}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

/** پلاکِ هر بازیکنِ دورِ میز. */
@Composable
private fun SeatChip(
    seat: UiSeat?,
    state: HokmUiState,
    modifier: Modifier = Modifier,
) {
    if (seat == null) return
    val isTurn = state.turnSeat == seat.seat && state.stage == "playing"
    val isPartner = seat.team == state.yourTeam && seat.seat != state.youSeat
    val ring by animateDpAsState(if (isTurn) 3.dp else 1.dp, label = "ring")

    Column(
        modifier = modifier.widthIn(max = 92.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                if (isPartner) Color(0xFF1E5631) else Color(0xFF3A2A12),
                                Color(0xFF10261A),
                            ),
                        ),
                    )
                    .border(ring, if (isTurn) Gold else Gold.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = seat.name.take(1),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (seat.seat == state.hakemSeat) {
                Text(
                    text = "👑",
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                )
            }
        }

        Text(
            text = seat.name,
            fontSize = 12.sp,
            fontWeight = if (isTurn) FontWeight.Bold else FontWeight.Normal,
            color = if (isTurn) Gold else Color(0xDDEEEEEE),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(seat.cards.coerceAtMost(5)) {
                Box(
                    modifier = Modifier
                        .size(width = 5.dp, height = 8.dp)
                        .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(1.dp)),
                )
            }
            if (seat.cards > 5) {
                Text(text = persianDigits(seat.cards), fontSize = 9.sp, color = Color(0xBBFFFFFF))
            }
        }
        if (isTurn) {
            Text(text = "در حال بازی…", fontSize = 9.sp, color = Gold)
        }
    }
}

// ---------------------------------------------------------------------------
// دستِ شما
// ---------------------------------------------------------------------------

@Composable
private fun YourHand(
    state: HokmUiState,
    onPlayCard: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    LaunchedEffect(state.version, state.isYourTurn) {
        if (state.isYourTurn) {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (state.isYourTurn) "نوبتِ شماست" else (state.seats.firstOrNull { it.seat == state.turnSeat }?.name?.let { "نوبتِ $it" } ?: ""),
            fontSize = 13.sp,
            fontWeight = if (state.isYourTurn) FontWeight.Bold else FontWeight.Normal,
            color = if (state.isYourTurn) Gold else Color(0xCCFFFFFF),
            modifier = Modifier.padding(bottom = 6.dp),
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            val cards = state.hand
            val count = cards.size
            val cardWidth = 68.dp
            val available = maxWidth - 16.dp
            val step = if (count > 1) {
                val ideal = (available - cardWidth) / (count - 1)
                ideal.coerceIn(14.dp, 34.dp)
            } else {
                0.dp
            }
            val totalWidth = cardWidth + step * (count - 1)

            Box(
                modifier = Modifier
                    .width(totalWidth)
                    .height(cardWidth / CARD_ASPECT + 26.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                val middle = (count - 1) / 2f
                cards.forEachIndexed { index, card ->
                    val rel = index - middle
                    val playable = card.code in state.legalCards
                    val lift by animateDpAsState(
                        targetValue = if (playable && state.isYourTurn) 10.dp else 0.dp,
                        label = "lift$index",
                    )
                    PlayingCard(
                        card = card,
                        modifier = Modifier
                            .width(cardWidth)
                            .offset(x = step * index, y = -(abs(rel) * abs(rel) * 1.4f).dp)
                            .graphicsLayer {
                                rotationZ = rel * 2.0f
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1.4f)
                            },
                        dim = state.isYourTurn && !playable,
                        lift = lift,
                        elevation = 8.dp,
                        onClick = { if (state.isYourTurn && playable) onPlayCard(card.code) },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// انتخابِ حکم، بنر و پایان
// ---------------------------------------------------------------------------

@Composable
private fun TrumpPicker(state: HokmUiState, onPick: (Suit) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC04120A))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF12281B),
            border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.6f)),
            modifier = Modifier.padding(24.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "شما حاکم هستید 👑",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Gold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "خالِ حکم را انتخاب کن",
                    fontSize = 13.sp,
                    color = Color(0xCCFFFFFF),
                )
                Spacer(Modifier.height(18.dp))
                Suit.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { suit ->
                            SuitButton(suit = suit, onClick = { onPick(suit) })
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SuitButton(suit: Suit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1D3A28),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gold.copy(alpha = 0.4f)),
        modifier = Modifier.size(width = 104.dp, height = 104.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = suit.symbol, fontSize = 34.sp, color = if (suit.isRed) Color(0xFFEF5350) else Color.White)
            Spacer(Modifier.height(6.dp))
            Text(text = suit.faName, fontSize = 15.sp, color = Color.White)
        }
    }
}

@Composable
private fun BannerCard(banner: UiBanner) {
    val color = when (banner.kind) {
        BannerKind.GOOD -> Color(0xEE1B5E20)
        BannerKind.BAD -> Color(0xEE7A1B1B)
        BannerKind.BIG -> Color(0xEE8A6A1F)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold),
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = banner.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Gold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = banner.subtitle,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ResultOverlay(
    result: UiResult,
    state: HokmUiState,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xD004120A)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color(0xFF12281B),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, Gold),
            modifier = Modifier.padding(28.dp),
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (result.youWon) "بردید 🎉" else "باختید",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.youWon) Gold else Color(0xFFEF9A9A),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "تیمِ ${persianDigits(result.winnerTeam + 1)} بازی را برد",
                    fontSize = 14.sp,
                    color = Color(0xCCFFFFFF),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ما", fontSize = 12.sp, color = Color(0xBBFFFFFF))
                        Text(
                            persianDigits(result.scores.getOrElse(state.yourTeam) { 0 }),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Gold,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("آن‌ها", fontSize = 12.sp, color = Color(0xBBFFFFFF))
                        Text(
                            persianDigits(result.scores.getOrElse(1 - state.yourTeam) { 0 }),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "در ${persianDigits(result.hands)} دست",
                    fontSize = 12.sp,
                    color = Color(0x99FFFFFF),
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) {
                    Text("بازیِ دوباره")
                }
                TextButton(onClick = onExit) {
                    Text("خروج", color = Color(0xCCFFFFFF))
                }
            }
        }
    }
}

/** تنظیماتِ سریعِ حکم (قبل از شروعِ دست). */
@Composable
fun HokmSetupScreen(
    config: HokmLaunchConfig,
    onConfigChange: (HokmLaunchConfig) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = config.options
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0E2416), Color(0xFF06120B))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "حکم",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = Gold,
        )
        Text(
            text = "محبوب‌ترین بازی ورقِ ایران — چهار نفر، دو تیم",
            fontSize = 13.sp,
            color = Color(0xAAFFFFFF),
        )
        Spacer(Modifier.height(28.dp))

        SetupRow("امتیازِ هدف", persianDigits(options.targetPoints)) {
            val next = if (options.targetPoints >= 11) 3 else options.targetPoints + 2
            onConfigChange(config.copy(options = options.copy(targetPoints = next)))
        }
        SetupRow("پایانِ هر دست", options.handEnd.faName) {
            onConfigChange(
                config.copy(
                    options = options.copy(
                        handEnd = if (options.handEnd == HandEndMode.SEVEN) {
                            HandEndMode.THIRTEEN
                        } else {
                            HandEndMode.SEVEN
                        },
                    ),
                ),
            )
        }
        SetupRow("حکمِ لازم", options.hokmLazim.faName) {
            onConfigChange(
                config.copy(
                    options = options.copy(
                        hokmLazim = if (options.hokmLazim == HokmLazimMode.OFF) {
                            HokmLazimMode.OVERTRUMP
                        } else {
                            HokmLazimMode.OFF
                        },
                    ),
                ),
            )
        }
        SetupRow("مهلتِ هر نوبت", if (options.turnSeconds == 0) "بدون محدودیت" else "${persianDigits(options.turnSeconds)} ثانیه") {
            val next = when (options.turnSeconds) {
                0 -> 15
                15 -> 30
                30 -> 60
                else -> 0
            }
            onConfigChange(config.copy(options = options.copy(turnSeconds = next)))
        }
        SetupRow("صدا", if (config.soundEnabled) "روشن" else "خاموش") {
            onConfigChange(config.copy(soundEnabled = !config.soundEnabled))
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("شروع بازی", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = onBack) {
            Text("بازگشت", color = Color(0xCCFFFFFF))
        }
    }
}

@Composable
private fun SetupRow(label: String, value: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color(0x22FFFFFF),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, color = Color(0xDDFFFFFF), fontSize = 14.sp)
            Text(text = value, color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}
