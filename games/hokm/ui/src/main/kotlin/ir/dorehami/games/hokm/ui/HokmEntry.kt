package ir.dorehami.games.hokm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.ui.room.LobbyScreen
import ir.dorehami.games.hokm.ui.room.RoomFlow
import ir.dorehami.games.hokm.ui.room.RoomMode
import ir.dorehami.games.hokm.ui.room.RoomStage
import ir.dorehami.uikit.rememberSoundPlayer
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Default for the self-hosted server (docs/DEPLOY.md). `10.0.2.2` = the dev machine from the emulator. */
const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

/**
 * Entry point of the Hokm feature: a short setup screen, then the table — solo against bots, in a
 * Bluetooth room, or in an online room. The table composable is the same in all three.
 */
@Composable
fun HokmEntry(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { HokmPrefs(context) }
    var started by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(prefs.name) }
    var target by rememberSaveable { mutableStateOf(prefs.targetPoints) }
    var playerCount by rememberSaveable { mutableStateOf(prefs.playerCount) }
    var soundOn by rememberSaveable { mutableStateOf(prefs.soundOn) }
    var hapticsOn by rememberSaveable { mutableStateOf(prefs.hapticsOn) }
    var botLevel by rememberSaveable { mutableStateOf(prefs.botLevel) }
    var matchKey by rememberSaveable { mutableStateOf(0) }
    var roomMode by rememberSaveable { mutableStateOf<String?>(null) }
    var serverUrl by rememberSaveable { mutableStateOf(prefs.serverUrl) }
    var stats by remember { mutableStateOf(prefs.stats) }

    val onFinished: (HokmUiState) -> Unit = { s ->
        val kots = s.handHistory.count {
            (it.kind == ir.dorehami.games.hokm.HandWinKind.KOT || it.kind == ir.dorehami.games.hokm.HandWinKind.HAKEM_KOT) &&
                it.winnerTeam == s.myTeam
        }
        prefs.recordMatch(
            won = s.winnerTeam == s.myTeam,
            hands = s.handHistory.size,
            handsWon = s.handHistory.count { it.winnerTeam == s.myTeam },
            kots = kots,
        )
        stats = prefs.stats
    }

    if (roomMode != null) {
        HokmRoom(
            mode = if (roomMode == "bt") RoomMode.BLUETOOTH else RoomMode.ONLINE,
            playerName = name,
            playerCount = playerCount,
            targetPoints = target,
            soundOn = soundOn,
            hapticsOn = hapticsOn,
            serverUrl = serverUrl,
            onServerUrl = { serverUrl = it; prefs.serverUrl = it },
            onFinished = onFinished,
            onExit = { roomMode = null },
        )
    } else if (!started) {
        HokmSetupScreen(
            name = name,
            onName = { name = it; prefs.name = it },
            target = target,
            onTarget = { target = it; prefs.targetPoints = it },
            playerCount = playerCount,
            onPlayerCount = { playerCount = it; prefs.playerCount = it },
            soundOn = soundOn,
            onSound = { soundOn = it; prefs.soundOn = it },
            hapticsOn = hapticsOn,
            onHaptics = { hapticsOn = it; prefs.hapticsOn = it },
            botLevel = botLevel,
            onBotLevel = { botLevel = it; prefs.botLevel = it },
            stats = stats,
            onResetStats = { prefs.resetStats(); stats = prefs.stats },
            onStart = { started = true },
            onMode = { roomMode = if (it == RoomMode.BLUETOOTH) "bt" else "online" },
            onBack = onExit,
        )
    } else {
        HokmSoloGame(
            key = matchKey,
            playerName = name,
            playerCount = playerCount,
            targetPoints = target,
            botLevel = botLevel,
            soundOn = soundOn,
            hapticsOn = hapticsOn,
            onFinished = onFinished,
            onPlayAgain = { matchKey++ },
            onExit = { started = false },
        )
    }
}

/** Fires [onFinished] exactly once per match, when the table reports the game over. */
@Composable
private fun RecordWhenFinished(state: HokmUiState, onFinished: (HokmUiState) -> Unit) {
    var recorded by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished) {
        if (state.finished && !recorded) {
            recorded = true
            onFinished(state)
        }
    }
}

@Composable
private fun HokmSoloGame(
    key: Int,
    playerName: String,
    playerCount: Int,
    targetPoints: Int,
    botLevel: String,
    soundOn: Boolean,
    hapticsOn: Boolean,
    onFinished: (HokmUiState) -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val sounds = rememberSoundPlayer(HokmSounds.map)
    LaunchedEffect(soundOn) { sounds.enabled = soundOn }

    // One coroutine scope per match: cancelled when the match composable leaves.
    val scope = remember(key) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(key) {
        HokmLocalMatch(
            scope = scope,
            playerName = playerName,
            playerCount = playerCount,
            targetPoints = targetPoints,
            // Seed picked by the UI (never by the rules): wall clock is fine here, it is recorded.
            seed = System.currentTimeMillis() xor (key.toLong() shl 20),
            botLevel = botLevel,
            onSound = { sounds.play(it) },
        )
    }
    DisposableEffect(key) {
        match.start()
        onDispose { scope.cancel() }
    }

    val state by match.state
    RecordWhenFinished(state, onFinished)
    HokmScreen(
        state = state,
        hapticsEnabled = hapticsOn,
        onUiSound = { sounds.play(it) },
        onPlayCard = match::playCard,
        onChooseHokm = match::chooseHokm,
        onToggleDiscard = match::toggleDiscard,
        onConfirmDiscard = match::confirmDiscard,
        onKeepCard = match::keepCard,
        onPassCard = match::passCard,
        onSticker = match::sendSticker,
        onClearError = match::clearError,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
    )
}

/** Lobby → shared table. One scope for the whole room so the transport outlives recompositions. */
@Composable
private fun HokmRoom(
    mode: RoomMode,
    playerName: String,
    playerCount: Int,
    targetPoints: Int,
    soundOn: Boolean,
    hapticsOn: Boolean,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    onFinished: (HokmUiState) -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val flow = remember { RoomFlow(context.applicationContext, scope, mode, playerName, playerCount, targetPoints, serverUrl) }
    DisposableEffect(Unit) {
        onDispose {
            flow.leave()
            scope.cancel()
        }
    }
    val stage by flow.stage
    val playing = stage as? RoomStage.Playing
    if (playing == null) {
        LobbyScreen(flow = flow, playerCount = playerCount, serverUrl = serverUrl, onServerUrl = onServerUrl, onExit = onExit)
    } else {
        val sounds = rememberSoundPlayer(HokmSounds.map)
        LaunchedEffect(soundOn) { sounds.enabled = soundOn }
        val label = if (mode == RoomMode.BLUETOOTH) "بلوتوث" else "آنلاین"
        val match = remember(playing.client) { HokmSessionMatch(scope, playing.client, label) { sounds.play(it) } }
        LaunchedEffect(match) { match.begin() }
        val state by match.state
        RecordWhenFinished(state, onFinished)
        HokmScreen(
            state = state,
            hapticsEnabled = hapticsOn,
            onUiSound = { sounds.play(it) },
            onPlayCard = match::playCard,
            onChooseHokm = match::chooseHokm,
            onToggleDiscard = match::toggleDiscard,
            onConfirmDiscard = match::confirmDiscard,
            onKeepCard = match::keepCard,
            onPassCard = match::passCard,
            onSticker = match::sendSticker,
            onClearError = match::clearError,
            onPlayAgain = onExit,
            onExit = onExit,
        )
    }
}

@Composable
private fun HokmSetupScreen(
    name: String,
    onName: (String) -> Unit,
    target: Int,
    onTarget: (Int) -> Unit,
    playerCount: Int,
    onPlayerCount: (Int) -> Unit,
    soundOn: Boolean,
    onSound: (Boolean) -> Unit,
    hapticsOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    botLevel: String,
    onBotLevel: (String) -> Unit,
    stats: HokmStats,
    onResetStats: () -> Unit,
    onStart: () -> Unit,
    onMode: (RoomMode) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var showRules by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F3D22), Color(0xFF071B10))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("بازگشت", color = Color.White) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showRules = !showRules }) { Text(if (showRules) "بستن قوانین" else "قوانین بازی", color = Color(0xFFD9B55A)) }
        }
        Spacer(Modifier.height(8.dp))
        Text("حکم", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
        Text("چهار نفر، دو تیم، یک حاکم", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))

        // A little table preview: four fanned cards.
        Row(horizontalArrangement = Arrangement.spacedBy((-22).dp)) {
            listOf("AS", "KH", "QD", "JC").forEach { code ->
                PlayingCardView(ir.dorehami.games.hokm.Card.parse(code)!!, width = 64.dp, elevation = 8.dp)
            }
        }
        Spacer(Modifier.height(22.dp))

        if (showRules) {
            RulesCard()
            Spacer(Modifier.height(16.dp))
        }

        SettingsCard {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 14) onName(it) },
                label = { Text("اسم شما") },
                placeholder = { Text("مثلاً گلپا") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Text("چند نفره؟", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    Triple(4, "۴ نفره", "دو تیم دو نفره"),
                    Triple(3, "۳ نفره", "هر کس برای خودش"),
                    Triple(2, "۲ نفره", "سوزاندن و برداشتن"),
                ).forEach { (count, title, sub) ->
                    val selected = count == playerCount
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .clickable { onPlayerCount(count) }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(title, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
                        Text(sub, fontSize = 10.sp, textAlign = TextAlign.Center, color = if (selected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("بازی تا چند امتیاز؟", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HokmModule.ALLOWED_TARGETS.filter { it > 1 }.forEach { t ->
                    val selected = t == target
                    Text(
                        t.toFa(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .clickable { onTarget(t) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("سطح ربات‌ها", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    HokmModule.BOT_PRO to "حرفه‌ای",
                    HokmModule.BOT_EASY to "ساده",
                ).forEach { (level, title) ->
                    val selected = level == botLevel
                    Text(
                        title,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                            .clickable { onBotLevel(level) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("افکت‌های صوتی", modifier = Modifier.weight(1f))
                Switch(checked = soundOn, onCheckedChange = onSound)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("لرزش", modifier = Modifier.weight(1f))
                Switch(checked = hapticsOn, onCheckedChange = onHaptics)
            }
        }

        if (stats.games > 0) {
            Spacer(Modifier.height(12.dp))
            StatsCard(stats, onResetStats)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Text("شروع بازی با ربات‌ها 🎴", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        ModeRow(onMode)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun StatsCard(stats: HokmStats, onReset: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("کارنامهٔ شما", color = Color(0xFFD9B55A), fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text("پاک کردن", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatCell("بازی", stats.games.toFa())
            StatCell("بُرد", stats.wins.toFa())
            StatCell("درصد برد", "٪" + stats.winRate.toFa())
            StatCell("کُت", stats.kots.toFa())
            StatCell("دست‌های برده", "${stats.handsWon.toFa()}/${stats.hands.toFa()}")
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp)
    }
}

@Composable
private fun RulesCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC102A1A))
            .border(1.dp, Color(0x99D9B55A), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("قوانین حکم", color = Color(0xFFD9B55A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
        val rules = listOf(
            "چهار نفر در دو تیم روبه‌روی هم می‌نشینند؛ شما و بازیکن روبه‌رو یک تیم هستید.",
            "اول بازی، کارت‌ها یکی‌یکی رو می‌شوند؛ اولین کسی که آس بیاورد حاکم است.",
            "حاکم پنج کارت اول را می‌بیند و خال حکم را اعلام می‌کند؛ بعد بقیهٔ کارت‌ها پخش می‌شود (هر نفر ۱۳ برگ).",
            "حاکم اولین کارت را می‌زند. هر کس باید از خالِ زمین بازی کند؛ اگر ندارد هر کارتی، از جمله حکم.",
            "بالاترین حکم دست را می‌برد؛ اگر حکم نباشد، بالاترین کارت از خالِ زمین. برندهٔ دست، کارت بعدی را شروع می‌کند.",
            "تیمی که زودتر ۷ دست ببرد، آن «دست» را برده و ۱ امتیاز می‌گیرد.",
            "اگر حریف هیچ دستی نبرد «کُت» است: ۲ امتیاز. اگر تیم حاکم کُت شود «حاکم‌کُت» است: ۳ امتیاز.",
            "تا وقتی تیم حاکم می‌برد، حاکم عوض نمی‌شود؛ وگرنه حاکم به نفر بعدی می‌رسد.",
            "اولین تیمی که به امتیاز هدف برسد برندهٔ بازی است.",
        )
        rules.forEachIndexed { i, r ->
            Text("${(i + 1).toFa()}. $r", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 20.sp)
        }
    }
}


/** Multiplayer entry points. They share this very table. */
@Composable
private fun ModeRow(onMode: (RoomMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(RoomMode.BLUETOOTH, "📶 بلوتوث / آفلاین", "دور هم، بدون اینترنت"),
            Triple(RoomMode.ONLINE, "🌐 آنلاین", "با کد اتاق یا حریف تصادفی"),
        ).forEach { (mode, title, sub) ->
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .clickable { onMode(mode) }
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(sub, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
