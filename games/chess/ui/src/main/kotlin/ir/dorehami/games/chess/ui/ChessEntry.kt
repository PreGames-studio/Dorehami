package ir.dorehami.games.chess.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.dorehami.uikit.rememberSoundPlayer
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Entry point of the chess feature: a short setup screen, then the 3D board.
 *
 * Online and Bluetooth rooms plug in here once `core/session` lands — the table itself is
 * unchanged, because it is driven only by a player's private view.
 */
@Composable
fun ChessEntry(onExit: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("chess-ui-settings", 0) }
    var animationsEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("animationsEnabled", true))
    }
    var boardStyle by rememberSaveable {
        mutableStateOf(
            preferences.getString("boardStyle", null)
                ?.let { name -> ChessBoardStyle.values().firstOrNull { it.name == name } }
                ?: ChessBoardStyle.WOOD,
        )
    }
    var pieceStyle by rememberSaveable {
        mutableStateOf(
            preferences.getString("pieceStyle", null)
                ?.let { name -> ChessPieceStyle.values().firstOrNull { it.name == name } }
                ?: ChessPieceStyle.CLASSIC,
        )
    }

    ChessTheme(motionEnabled = animationsEnabled) {
        ChessEntryContent(
            onExit = onExit,
            boardStyle = boardStyle,
            onBoardStyle = { value ->
                boardStyle = value
                preferences.edit().putString("boardStyle", value.name).apply()
            },
            pieceStyle = pieceStyle,
            onPieceStyle = { value ->
                pieceStyle = value
                preferences.edit().putString("pieceStyle", value.name).apply()
            },
            animationsEnabled = animationsEnabled,
            onAnimationsEnabled = { value ->
                animationsEnabled = value
                preferences.edit().putBoolean("animationsEnabled", value).apply()
            },
        )
    }
}

@Composable
private fun ChessEntryContent(
    onExit: () -> Unit,
    boardStyle: ChessBoardStyle,
    onBoardStyle: (ChessBoardStyle) -> Unit,
    pieceStyle: ChessPieceStyle,
    onPieceStyle: (ChessPieceStyle) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsEnabled: (Boolean) -> Unit,
) {
    var started by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(2) }
    var color by rememberSaveable { mutableStateOf(ColorChoice.WHITE) }
    var passAndPlay by rememberSaveable { mutableStateOf(false) }
    var autoRotate by rememberSaveable { mutableStateOf(true) }
    var soundOn by rememberSaveable { mutableStateOf(true) }
    var matchKey by rememberSaveable { mutableStateOf(0) }

    if (!started) {
        ChessSetupScreen(
            name = name,
            onName = { name = it },
            level = level,
            onLevel = { level = it },
            color = color,
            onColor = { color = it },
            passAndPlay = passAndPlay,
            onPassAndPlay = { passAndPlay = it },
            autoRotate = autoRotate,
            onAutoRotate = { autoRotate = it },
            soundOn = soundOn,
            onSound = { soundOn = it },
            boardStyle = boardStyle,
            onBoardStyle = onBoardStyle,
            pieceStyle = pieceStyle,
            onPieceStyle = onPieceStyle,
            animationsEnabled = animationsEnabled,
            onAnimationsEnabled = onAnimationsEnabled,
            onStart = { started = true },
            onBack = onExit,
        )
    } else {
        ChessMatchHost(
            key = matchKey,
            playerName = name,
            level = level,
            color = color,
            mode = if (passAndPlay) ChessMode.PASS_AND_PLAY else ChessMode.VS_BOT,
            autoRotate = autoRotate,
            soundOn = soundOn,
            boardStyle = boardStyle,
            pieceStyle = pieceStyle,
            onPlayAgain = { matchKey++ },
            onExit = { started = false },
        )
    }
}

@Composable
private fun ChessMatchHost(
    key: Int,
    playerName: String,
    level: Int,
    color: ColorChoice,
    mode: ChessMode,
    autoRotate: Boolean,
    soundOn: Boolean,
    boardStyle: ChessBoardStyle,
    pieceStyle: ChessPieceStyle,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val sounds = rememberSoundPlayer(ChessSounds.map)
    LaunchedEffect(soundOn) { sounds.enabled = soundOn }

    // One coroutine scope per match: cancelled when the match composable leaves the composition.
    val scope = remember(key) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(key) {
        ChessLocalMatch(
            scope = scope,
            mode = mode,
            playerName = playerName,
            colorChoice = color,
            botLevel = level,
            autoRotate = autoRotate,
            // Seed picked by the UI (never by the rules): the wall clock is fine here, it is recorded.
            seed = System.currentTimeMillis() xor (key.toLong() shl 20),
            onSound = { sounds.play(it) },
        )
    }
    DisposableEffect(key) {
        match.start()
        onDispose { scope.cancel() }
    }

    val state by match.state
    ChessScreen(
        state = state,
        onSquareTap = match::onSquareTap,
        onPromotion = match::choosePromotion,
        onFlip = match::flip,
        onUndo = match::undo,
        onResign = match::resign,
        onOfferDraw = match::offerDraw,
        onAnswerDraw = match::answerDraw,
        onClearError = match::clearError,
        onClearNote = match::clearNote,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
        boardStyle = boardStyle,
        pieceStyle = pieceStyle,
    )
}

@Composable
private fun ChessSetupScreen(
    name: String,
    onName: (String) -> Unit,
    level: Int,
    onLevel: (Int) -> Unit,
    color: ColorChoice,
    onColor: (ColorChoice) -> Unit,
    passAndPlay: Boolean,
    onPassAndPlay: (Boolean) -> Unit,
    autoRotate: Boolean,
    onAutoRotate: (Boolean) -> Unit,
    soundOn: Boolean,
    onSound: (Boolean) -> Unit,
    boardStyle: ChessBoardStyle,
    onBoardStyle: (ChessBoardStyle) -> Unit,
    pieceStyle: ChessPieceStyle,
    onPieceStyle: (ChessPieceStyle) -> Unit,
    animationsEnabled: Boolean,
    onAnimationsEnabled: (Boolean) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showRules by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        ChessBackdrop(imageRes = R.drawable.chess_backdrop, modifier = Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("بازگشت", color = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showRules = !showRules }) {
                    Text(if (showRules) "بستن قوانین" else "قوانین بازی", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text("شطرنج", color = MaterialTheme.colorScheme.onBackground, fontSize = chessSp(40), fontWeight = FontWeight.Black)
            Text("میز سه‌بعدی، حریفِ قابل‌اعتماد", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(13))
            Spacer(Modifier.height(14.dp))

            // A tiny preview of the pieces, drawn with glyphs so the setup screen needs no GL.
            val previewTints = listOf(
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.secondaryContainer,
                MaterialTheme.colorScheme.tertiaryContainer,
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 2, 3, 4, 5, 6).forEachIndexed { index, t ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(previewTints[index])
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ChessText.glyph(t), fontSize = chessSp(24), color = MaterialTheme.colorScheme.onSurface) }
                }
            }
            Spacer(Modifier.height(20.dp))

            if (showRules) {
                RulesCard()
                Spacer(Modifier.height(16.dp))
            }

            SettingsCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 14) onName(it) },
                    label = { Text("اسم شما") },
                    placeholder = { Text("مثلاً تاله") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = chessOutlinedFieldColors(),
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("دو نفره روی همین گوشی", modifier = Modifier.weight(1f), fontSize = chessSp(14), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = passAndPlay, onCheckedChange = onPassAndPlay, colors = chessSwitchColors())
                }
                Text(
                    if (passAndPlay) "نوبت هر کس که باشد گوشی را برمی‌دارد." else "با ربات بازی می‌کنی؛ هر چه سطح بالاتر، عمیق‌تر فکر می‌کند.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = chessSp(12),
                )
                if (!passAndPlay) {
                    Spacer(Modifier.height(14.dp))
                    Text("سطح حریف", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        (1..4).forEach { l ->
                            Choice(
                                text = ChessText.levelName(l),
                                selected = l == level,
                                onClick = { onLevel(l) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("رنگ شما", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Choice("سفید", color == ColorChoice.WHITE, { onColor(ColorChoice.WHITE) }, Modifier.weight(1f))
                        Choice("سیاه", color == ColorChoice.BLACK, { onColor(ColorChoice.BLACK) }, Modifier.weight(1f))
                        Choice("تصادفی", color == ColorChoice.RANDOM, { onColor(ColorChoice.RANDOM) }, Modifier.weight(1f))
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("چرخش خودکار صفحه هر نوبت", modifier = Modifier.weight(1f), fontSize = chessSp(14), color = MaterialTheme.colorScheme.onSurface)
                        Switch(checked = autoRotate, onCheckedChange = onAutoRotate, colors = chessSwitchColors())
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("افکت‌های صوتی", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                    Switch(checked = soundOn, onCheckedChange = onSound, colors = chessSwitchColors())
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تصویر و ذرات متحرک", color = MaterialTheme.colorScheme.onSurface)
                        Text("حرکت آرام پس‌زمینه و برق ذرات", fontSize = chessSp(11), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = animationsEnabled, onCheckedChange = onAnimationsEnabled, colors = chessSwitchColors())
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingsCard {
                ChessBoardStylePicker(selected = boardStyle, onSelected = onBoardStyle)
                Spacer(Modifier.height(16.dp))
                ChessPieceStylePicker(selected = pieceStyle, onSelected = onPieceStyle)
            }

            Spacer(Modifier.height(18.dp))
            ChessGradientButton(
                text = if (passAndPlay) "شروع بازی دو نفره ♟️" else "شروع بازی با ربات ♟️",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "بازی بلوتوثی و آنلاین با همین میز به‌زودی",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = chessSp(12),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = chessSp(12),
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.75f))
            .border(1.dp, if (selected) scheme.primary else scheme.outline, RoundedCornerShape(18.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun RulesCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("قوانین شطرنج", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = chessSp(16))
        val rules = listOf(
            "پیاده یک خانه جلو می‌رود (اول بازی دو خانه)، کج می‌زند و وقتی به آخرین خط برسد، وزیر یا هر مهرهٔ دیگری می‌شود.",
            "اسب «L» حرکت می‌کند و از روی مهره‌ها رد می‌شود؛ فیل مورب، رخ راست، وزیر هر دو.",
            "شاه یک خانه به هر طرف؛ یک بار در بازی می‌تواند «قلعه» برود (با شرط خالی و غیرکیش بودن مسیر).",
            "اگر پیاده حریف دو خانه جلو رفته باشد، می‌توانی یک بار او را «گرفتن در عبور» (an passant) بزنی.",
            "کیش: شاه زیر ضربه است. باید کیش را برداری؛ اگر هیچ راهی نباشد و مات باشی، می‌بازی.",
            "پات: نوبت توست و حرکت قانونی نداری ولی کیش هم نیستی — مساوی.",
            "مساوی سه‌راه (تکرار وضعیت)، قانون ۵۰ حرکت و نبودِ مهرهٔ کافی برای مات هم بازی را تمام می‌کند.",
            "می‌توانی پیشنهاد مساوی بدهی یا تسلیم شوی؛ رباتِ قوی پیشنهاد را فقط وقتی می‌پذیرد که عقب باشد.",
        )
        rules.forEachIndexed { i, r ->
            Text("${(i + 1).toFa()}. $r", color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(13), lineHeight = chessSp(20))
        }
    }
}
