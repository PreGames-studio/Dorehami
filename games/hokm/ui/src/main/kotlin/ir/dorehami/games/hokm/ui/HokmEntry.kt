package ir.dorehami.games.hokm.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.ui.room.LobbyScreen
import ir.dorehami.games.hokm.ui.room.RoomFlow
import ir.dorehami.games.hokm.ui.room.RoomMode
import ir.dorehami.games.hokm.ui.room.RoomStage
import ir.dorehami.uikit.rememberSoundPlayer
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Default for the self-hosted server. `10.0.2.2` is the dev machine from the emulator. */
const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"

/** Entry point of the Hokm feature: a premium lobby, then the same table in all play modes. */
@Composable
fun HokmEntry(onExit: () -> Unit) {
    HokmTheme {
        HokmEntryContent(onExit)
    }
}

/** Which Hokm section the player is in. The online room keeps its own flow (LobbyScreen). */
private enum class HokmScreen { MENU, OFFLINE, SOLO, SETTINGS, SHOP }

@Composable
private fun HokmEntryContent(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { HokmPrefs(context) }
    var screen by rememberSaveable { mutableStateOf(HokmScreen.MENU.name) }
    /** Section to come back to after a room is closed (Bluetooth → offline, online → menu). */
    var roomReturnScreen by rememberSaveable { mutableStateOf(HokmScreen.MENU.name) }
    var name by rememberSaveable { mutableStateOf(prefs.name) }
    var target by rememberSaveable { mutableStateOf(prefs.targetPoints) }
    var playerCount by rememberSaveable { mutableStateOf(prefs.playerCount) }
    var soundOn by rememberSaveable { mutableStateOf(prefs.soundOn) }
    var hapticsOn by rememberSaveable { mutableStateOf(prefs.hapticsOn) }
    var motionName by rememberSaveable { mutableStateOf(prefs.motionLevel) }
    // Android's own "remove animations" setting silently downgrades FULL to REDUCED.
    val systemReducedMotion = remember {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f
        }.getOrDefault(false)
    }
    val preferredMotion = HokmMotionLevel.fromStored(motionName)
    val effectiveMotion = if (systemReducedMotion && preferredMotion == HokmMotionLevel.FULL) {
        HokmMotionLevel.REDUCED
    } else {
        preferredMotion
    }
    var botLevel by rememberSaveable { mutableStateOf(prefs.botLevel) }
    var matchKey by rememberSaveable { mutableStateOf(0) }
    var roomMode by rememberSaveable { mutableStateOf<String?>(null) }
    var serverUrl by rememberSaveable { mutableStateOf(prefs.serverUrl) }
    var cardBackId by rememberSaveable { mutableStateOf(prefs.cardBack) }
    var tableId by rememberSaveable { mutableStateOf(prefs.tableSkin) }
    var stats by remember { mutableStateOf(prefs.stats) }

    val onFinished: (HokmUiState) -> Unit = { state ->
        state.matchStatsDelta()?.let { delta ->
            prefs.recordMatch(
                won = delta.won,
                hands = delta.hands,
                handsWon = delta.handsWon,
                kots = delta.kots,
            )
            stats = prefs.stats
        }
    }

    HokmMotionProvider(effectiveMotion) {
        HokmLookProvider(cardBackId = cardBackId, tableId = tableId) {
        when {
            roomMode != null -> HokmRoom(
                mode = if (roomMode == "bt") RoomMode.BLUETOOTH else RoomMode.ONLINE,
                playerName = name,
                playerCount = playerCount,
                targetPoints = target,
                soundOn = soundOn,
                hapticsOn = hapticsOn,
                serverUrl = serverUrl,
                onServerUrl = { serverUrl = it; prefs.serverUrl = it },
                onFinished = onFinished,
                onExit = {
                    roomMode = null
                    screen = roomReturnScreen
                },
            )

            screen == HokmScreen.SOLO.name -> HokmSoloGame(
                key = matchKey,
                playerName = name,
                playerCount = playerCount,
                targetPoints = target,
                botLevel = botLevel,
                soundOn = soundOn,
                hapticsOn = hapticsOn,
                onFinished = onFinished,
                onPlayAgain = { matchKey++ },
                onExit = { screen = HokmScreen.OFFLINE.name },
            )

            screen == HokmScreen.OFFLINE.name -> HokmOfflineScreen(
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
                motion = effectiveMotion,
                onMotion = { motionName = it.name; prefs.motionLevel = it.name },
                botLevel = botLevel,
                onBotLevel = { botLevel = it; prefs.botLevel = it },
                stats = stats,
                onResetStats = { prefs.resetStats(); stats = prefs.stats },
                onStartBots = { screen = HokmScreen.SOLO.name },
                onBluetooth = {
                    roomReturnScreen = HokmScreen.OFFLINE.name
                    roomMode = "bt"
                },
                onBack = { screen = HokmScreen.MENU.name },
            )

            screen == HokmScreen.SETTINGS.name -> HokmSettingsScreen(
                name = name,
                onName = { name = it; prefs.name = it },
                target = target,
                onTarget = { target = it; prefs.targetPoints = it },
                playerCount = playerCount,
                onPlayerCount = { playerCount = it; prefs.playerCount = it },
                botLevel = botLevel,
                onBotLevel = { botLevel = it; prefs.botLevel = it },
                soundOn = soundOn,
                onSound = { soundOn = it; prefs.soundOn = it },
                hapticsOn = hapticsOn,
                onHaptics = { hapticsOn = it; prefs.hapticsOn = it },
                motion = effectiveMotion,
                onMotion = { motionName = it.name; prefs.motionLevel = it.name },
                cardBackId = cardBackId,
                tableId = tableId,
                wins = stats.wins,
                onOpenShop = { screen = HokmScreen.SHOP.name },
                onResetStats = { prefs.resetStats(); stats = prefs.stats },
                onBack = { screen = HokmScreen.MENU.name },
            )

            screen == HokmScreen.SHOP.name -> HokmShopScreen(
                cardBackId = cardBackId,
                tableId = tableId,
                wins = stats.wins,
                onPickCardBack = { skin -> cardBackId = skin; prefs.cardBack = skin },
                onPickTable = { skin -> tableId = skin; prefs.tableSkin = skin },
                onBack = { screen = HokmScreen.MENU.name },
            )

            else -> HokmMainMenu(
                playerName = name,
                playerCount = playerCount,
                target = target,
                cardBackId = cardBackId,
                tableId = tableId,
                onOffline = { screen = HokmScreen.OFFLINE.name },
                onOnline = {
                    roomReturnScreen = HokmScreen.MENU.name
                    roomMode = "online"
                },
                onSettings = { screen = HokmScreen.SETTINGS.name },
                onShop = { screen = HokmScreen.SHOP.name },
                onBack = onExit,
            )
        }
        }
    }
}

/** Fires [onFinished] exactly once per match, when the table reports the game over. */
@Composable
private fun RecordWhenFinished(state: HokmUiState, matchKey: Any, onFinished: (HokmUiState) -> Unit) {
    var recorded by rememberSaveable(matchKey) { mutableStateOf(false) }
    LaunchedEffect(state.finished) {
        if (shouldRecordMatchStats(state.finished, recorded)) {
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

    val context = LocalContext.current
    val store = remember { HokmMatchStore(context) }
    // A saved unfinished match resumes here; a finished match was already cleared below, so this
    // is null for a brand-new solo game (HK-011).
    val restored = remember { store.load() }
    val scope = remember(key) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(key) {
        HokmLocalMatch(
            scope = scope,
            playerName = playerName,
            playerCount = playerCount,
            targetPoints = targetPoints,
            // Seed picked by the UI (never by rules): wall clock is fine here, it is recorded.
            seed = System.currentTimeMillis() xor (key.toLong() shl 20),
            botLevel = botLevel,
            onSound = { sounds.play(it) },
            // Persist after every accepted move so process death never loses the match.
            onSaved = { store.save(it) },
        ).apply { restored?.let { restore(it) } }
    }
    DisposableEffect(key) {
        match.start()
        onDispose { scope.cancel() }
    }

    val state by match.state
    RecordWhenFinished(state, matchKey = key, onFinished = onFinished)
    // The file holds only unfinished matches: once the match is over (and stats were recorded
    // exactly once above), clear it so the next solo game starts fresh.
    LaunchedEffect(state.finished) {
        if (state.finished) store.clear()
    }
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
    val sounds = rememberSoundPlayer(HokmSounds.map)
    LaunchedEffect(soundOn) { sounds.enabled = soundOn }
    val haptics = LocalHapticFeedback.current
    val onTap: () -> Unit = {
        sounds.play("tap")
        if (hapticsOn) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    DisposableEffect(Unit) {
        onDispose {
            // HK-007: request teardown before cancelling the room scope. RoomFlow releases its
            // suspend closers on an independent Job, so they survive this cancel (R-05).
            // `rememberSoundPlayer` owns the SoundPool and releases it in its own DisposableEffect,
            // independently of this scope — do not move sound release behind scope.cancel().
            flow.leave()
            scope.cancel()
        }
    }
    val stage by flow.stage
    val playing = stage as? RoomStage.Playing
    if (playing == null) {
        LobbyScreen(
            flow = flow,
            playerCount = playerCount,
            serverUrl = serverUrl,
            onServerUrl = { value ->
                flow.updateServerUrl(value)
                onServerUrl(value)
            },
            onExit = onExit,
            onTap = onTap,
        )
    } else {
        val label = if (mode == RoomMode.BLUETOOTH) "بلوتوث" else "آنلاین"
        val match = remember(playing.client) { HokmSessionMatch(scope, playing.client, label) { sounds.play(it) } }
        LaunchedEffect(match) { match.begin() }
        val state by match.state
        RecordWhenFinished(state, matchKey = playing.client, onFinished = onFinished)
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
            playAgainAvailable = false,
        )
    }
}

/**
 * The **offline** section: play with bots on this phone, or open a Bluetooth room where empty seats
 * become bots. Table settings live here because every offline table uses them.
 */
@Composable
private fun HokmOfflineScreen(
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
    motion: HokmMotionLevel,
    onMotion: (HokmMotionLevel) -> Unit,
    botLevel: String,
    onBotLevel: (String) -> Unit,
    stats: HokmStats,
    onResetStats: () -> Unit,
    onStartBots: () -> Unit,
    onBluetooth: () -> Unit,
    onBack: () -> Unit,
) {
    var showRules by rememberSaveable { mutableStateOf(false) }
    var confirmResetStats by rememberSaveable { mutableStateOf(false) }
    BackHandler {
        if (confirmResetStats) confirmResetStats = false else onBack()
    }
    val sounds = rememberSoundPlayer(HokmSounds.map)
    LaunchedEffect(soundOn) { sounds.enabled = soundOn }
    val rawHaptics = LocalHapticFeedback.current
    val tap: () -> Unit = {
        sounds.play("tap")
        if (hapticsOn) rawHaptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(Modifier.fillMaxSize()) {
        HokmBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onBack = onBack, onTap = tap)
            Spacer(Modifier.height(10.dp))
            Text(
                "حکم آفلاین",
                color = HokmPalette.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "بدون اینترنت: با ربات‌ها یا با بلوتوث کنار هم",
                color = HokmPalette.muted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            SectionHeader("ترکیب میز", "تعداد بازیکن‌ها را انتخاب کن")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    Triple(4, "۴ نفره", "دو تیم"),
                    Triple(3, "۳ نفره", "انفرادی"),
                    Triple(2, "۲ نفره", "دونفره"),
                ).forEach { (count, title, detail) ->
                    Hokm3DButton(
                        text = title,
                        subtitle = detail,
                        trailingIcon = if (playerCount == count) HokmIcon.Check else null,
                        tone = if (playerCount == count) HokmButtonTone.GOLD else HokmButtonTone.GLASS,
                        height = 52.dp,
                        modifier = Modifier.weight(1f),
                        onSound = tap,
                        onClick = { onPlayerCount(count) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Hokm3DButton(
                text = "بازی با ربات‌ها",
                subtitle = "یک میز کامل، حتی بدون اینترنت",
                leadingIcon = HokmIcon.Crown,
                tone = HokmButtonTone.EMERALD,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
                onSound = tap,
                onClick = onStartBots,
            )
            Spacer(Modifier.height(8.dp))
            Hokm3DButton(
                text = "اتاق بلوتوثی",
                subtitle = "میزبان شو یا به اتاق دوستانت وصل شو",
                leadingIcon = HokmIcon.Bolt,
                tone = HokmButtonTone.GLASS,
                height = 56.dp,
                modifier = Modifier.fillMaxWidth(),
                onSound = tap,
                onClick = onBluetooth,
            )
            Spacer(Modifier.height(16.dp))

            SectionHeader("تنظیمات میز", "همه‌چیز را مطابق سلیقه‌ات بچین")
            Spacer(Modifier.height(8.dp))
            HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 14) onName(it) },
                    label = { Text("نام نمایشی") },
                    placeholder = { Text("مثلاً آرش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                Text("امتیاز پایان بازی", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    HokmModule.ALLOWED_TARGETS.forEach { points ->
                        Hokm3DButton(
                            text = points.toFa(),
                            subtitle = "امتیاز",
                            trailingIcon = if (target == points) HokmIcon.Check else null,
                            tone = if (target == points) HokmButtonTone.GOLD else HokmButtonTone.GLASS,
                            height = 50.dp,
                            modifier = Modifier.weight(1f),
                            onSound = tap,
                            onClick = { onTarget(points) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("قدرت ربات‌ها", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Hokm3DButton(
                        text = "حرفه‌ای",
                        subtitle = "بازی حساب‌شده",
                        trailingIcon = if (botLevel == HokmModule.BOT_PRO) HokmIcon.Check else null,
                        tone = if (botLevel == HokmModule.BOT_PRO) HokmButtonTone.EMERALD else HokmButtonTone.GLASS,
                        height = 54.dp,
                        modifier = Modifier.weight(1f),
                        onSound = tap,
                        onClick = { onBotLevel(HokmModule.BOT_PRO) },
                    )
                    Hokm3DButton(
                        text = "ساده",
                        subtitle = "برای شروع یادگیری",
                        trailingIcon = if (botLevel == HokmModule.BOT_EASY) HokmIcon.Check else null,
                        tone = if (botLevel == HokmModule.BOT_EASY) HokmButtonTone.EMERALD else HokmButtonTone.GLASS,
                        height = 54.dp,
                        modifier = Modifier.weight(1f),
                        onSound = tap,
                        onClick = { onBotLevel(HokmModule.BOT_EASY) },
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SettingToggle(
                        title = "صدای بازی",
                        subtitle = if (soundOn) "روشن" else "خاموش",
                        checked = soundOn,
                        modifier = Modifier.weight(1f),
                        onClick = { tap(); onSound(!soundOn) },
                    )
                    SettingToggle(
                        title = "لرزش",
                        subtitle = if (hapticsOn) "روشن" else "خاموش",
                        checked = hapticsOn,
                        modifier = Modifier.weight(1f),
                        onClick = { tap(); onHaptics(!hapticsOn) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                SettingToggle(
                    title = "جلوه‌های حرکتی",
                    subtitle = when (motion) {
                        HokmMotionLevel.FULL -> "کامل · لمس کنید تا کم شود"
                        HokmMotionLevel.REDUCED -> "کاهش‌یافته"
                        HokmMotionLevel.OFF -> "خاموش"
                    },
                    checked = motion != HokmMotionLevel.OFF,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        tap()
                        val levels = HokmMotionLevel.entries
                        onMotion(levels[(motion.ordinal + 1) % levels.size])
                    },
                )
            }

            if (stats.games > 0) {
                Spacer(Modifier.height(12.dp))
                StatsCard(stats = stats, onReset = { confirmResetStats = true }, onTap = tap)
            }

            Spacer(Modifier.height(12.dp))
            Hokm3DButton(
                text = if (showRules) "بستن راهنمای بازی" else "راهنمای حکم",
                subtitle = "قوانین کوتاه و تفاوت حالت‌ها",
                leading = "؟",
                trailing = if (showRules) "⌃" else "⌄",
                tone = HokmButtonTone.GLASS,
                height = 54.dp,
                modifier = Modifier.fillMaxWidth(),
                onSound = tap,
                onClick = { showRules = !showRules },
            )
            AnimatedVisibility(
                visible = showRules,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180)),
            ) {
                Column(Modifier.padding(top = 9.dp)) {
                    RulesCard(playerCount = playerCount)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
        if (confirmResetStats) {
            AlertDialog(
                onDismissRequest = { confirmResetStats = false },
                title = { Text("پاک‌کردن کارنامه؟") },
                text = { Text("آمار بردها و دست‌های ثبت‌شده برای همیشه پاک می‌شود.") },
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.primary,
                textContentColor = MaterialTheme.colorScheme.onSurface,
                confirmButton = {
                    Hokm3DButton(
                        text = "پاک کن",
                        tone = HokmButtonTone.CORAL,
                        height = 42.dp,
                        modifier = Modifier.width(88.dp),
                        onSound = tap,
                        onClick = {
                            onResetStats()
                            confirmResetStats = false
                        },
                    )
                },
                dismissButton = {
                    Hokm3DButton(
                        text = "انصراف",
                        tone = HokmButtonTone.GLASS,
                        height = 42.dp,
                        modifier = Modifier.width(92.dp),
                        onSound = tap,
                        onClick = { confirmResetStats = false },
                    )
                },
            )
        }
    }
}

/** The entry screen: two big sections (offline / online), the same shape as the chess menu. */
@Composable
private fun HokmMainMenu(
    playerName: String,
    playerCount: Int,
    target: Int,
    cardBackId: String,
    tableId: String,
    onOffline: () -> Unit,
    onOnline: () -> Unit,
    onSettings: () -> Unit,
    onShop: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        HokmBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onBack = onBack, onTap = {})
            Spacer(Modifier.height(10.dp))
            HokmHero(playerCount = playerCount, target = target)
            Spacer(Modifier.height(14.dp))
            HokmSectionCard(
                icon = HokmIcon.Bolt,
                title = "آفلاین",
                subtitle = "با ربات‌ها یا بلوتوث · بدون اینترنت",
                detail = "میز ${playerCount.toFa()} نفره",
                tone = HokmButtonTone.EMERALD,
                onClick = onOffline,
            )
            Spacer(Modifier.height(10.dp))
            HokmSectionCard(
                icon = HokmIcon.Globe,
                title = "آنلاین",
                subtitle = "اتاق خصوصی، کد اتاق و حریف تصادفی",
                detail = "با سرور دورهمی",
                tone = HokmButtonTone.GOLD,
                onClick = onOnline,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hokm3DButton(
                    text = "تنظیمات",
                    leadingIcon = HokmIcon.Settings,
                    tone = HokmButtonTone.GLASS,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onSettings,
                )
                Hokm3DButton(
                    text = "فروشگاه",
                    leadingIcon = HokmIcon.Shop,
                    tone = HokmButtonTone.GLASS,
                    height = 48.dp,
                    modifier = Modifier.weight(1f),
                    onClick = onShop,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HokmPill("پشت کارت: ${HokmSkins.cardBack(cardBackId).titleFa}", icon = HokmIcon.Deck, color = HokmPalette.mint)
                HokmPill("میز: ${HokmSkins.table(tableId).titleFa}", icon = HokmIcon.Palette, color = HokmPalette.gold)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (playerName.isBlank()) {
                    "برای دیدن نامت روی میز، در «تنظیمات» نام نمایشی را وارد کن."
                } else {
                    "نام نمایشی: $playerName · امتیاز هدف: ${target.toFa()}"
                },
                color = HokmPalette.muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

/** One big, readable section card of the Hokm menu. */
@Composable
private fun HokmSectionCard(
    icon: HokmIcon,
    title: String,
    subtitle: String,
    detail: String,
    tone: HokmButtonTone,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val accent = when (tone) {
        HokmButtonTone.GOLD -> HokmPalette.gold
        HokmButtonTone.EMERALD -> HokmPalette.emerald
        HokmButtonTone.CORAL -> HokmPalette.coral
        HokmButtonTone.GLASS -> HokmPalette.muted
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.6f), shape)
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            HokmIconView(icon, color = accent, size = 24.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = HokmPalette.text, fontSize = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                subtitle,
                color = HokmPalette.muted,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            HokmPill(detail, icon = HokmIcon.Star, color = accent)
        }
        HokmIconView(HokmIcon.Back, color = accent, size = 20.dp)
    }
}

@Composable
internal fun HomeHeader(onBack: () -> Unit, onTap: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(42.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("د", color = MaterialTheme.colorScheme.onPrimary, fontSize = 21.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("دورهمی", color = HokmPalette.text, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text("باشگاه بازی‌های ایرانی", color = HokmPalette.muted, fontSize = 10.sp)
        }
        Hokm3DButton(
            text = "بازگشت",
            leadingIcon = HokmIcon.Back,
            tone = HokmButtonTone.GLASS,
            height = 42.dp,
            modifier = Modifier.width(104.dp),
            onSound = onTap,
            onClick = onBack,
        )
    }
}

@Composable
private fun HokmHero(playerCount: Int, target: Int) {
    val transition = rememberInfiniteTransition(label = "hokm-hero")
    val glow by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "hero-glow",
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(225.dp)
            .shadow(6.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f), RoundedCornerShape(26.dp)),
    ) {
        val narrow = maxWidth < 355.dp
        // Capture themed colors before entering the non-composable Canvas draw scope.
        val mint = HokmPalette.mint
        val gold = HokmPalette.gold
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = mint.copy(alpha = 0.10f + 0.10f * glow),
                radius = size.width * 0.46f,
                center = Offset(size.width * 0.06f, size.height * 0.14f),
            )
            drawCircle(
                color = gold.copy(alpha = 0.07f),
                radius = size.width * 0.25f,
                center = Offset(size.width * 0.93f, size.height * 0.95f),
                style = Stroke(width = 1.2.dp.toPx()),
            )
            val step = 32.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(gold.copy(alpha = 0.045f), Offset(x, 0f), Offset(x + size.height, size.height), 1.dp.toPx())
                x += step
            }
        }
        Row(
            Modifier.fillMaxSize().padding(horizontal = if (narrow) 17.dp else 21.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                HokmPill("بازی اصیل ایرانی", icon = HokmIcon.Star, color = HokmPalette.gold)
                Spacer(Modifier.height(10.dp))
                Text("حُکم", color = HokmPalette.text, fontSize = if (narrow) 36.sp else 42.sp, fontWeight = FontWeight.Black)
                Text(
                    "یک دست ورق، یک دورهمی گرم",
                    color = HokmPalette.muted,
                    fontSize = if (narrow) 12.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(12.dp))
                if (narrow) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        HokmPill("${playerCount.toFa()} نفره", icon = HokmIcon.Users, color = HokmPalette.mint)
                        HokmPill("تا ${target.toFa()} امتیاز", icon = HokmIcon.Target, color = HokmPalette.gold)
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HokmPill("${playerCount.toFa()} نفره", icon = HokmIcon.Users, color = HokmPalette.mint)
                        HokmPill("تا ${target.toFa()} امتیاز", icon = HokmIcon.Target, color = HokmPalette.gold)
                    }
                }
            }
            Spacer(Modifier.width(if (narrow) 2.dp else 8.dp))
            Box(Modifier.width(if (narrow) 102.dp else 126.dp).height(175.dp)) {
                val cardWidth = if (narrow) 66.dp else 76.dp
                // The bob is read inside `graphicsLayer`, so the hero cards float without a single
                // recomposition per frame (the old `.offset(bob.dp)` re-laid out the row 60× a second).
                val bobState = transition.animateFloat(
                    initialValue = -3f,
                    targetValue = 3f,
                    animationSpec = infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "card-bob-draw",
                )
                PlayingCardView(
                    card = Card.parse("7D")!!,
                    width = cardWidth,
                    elevation = 9.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = (-15).dp.toPx()
                            translationY = (bobState.value + 5f).dp.toPx()
                            rotationZ = -11f
                        },
                )
                PlayingCardView(
                    card = Card.parse("QH")!!,
                    width = cardWidth,
                    elevation = 11.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationX = 11.dp.toPx()
                            translationY = (bobState.value - 5f).dp.toPx()
                            rotationZ = 8f
                        },
                )
                PlayingCardView(
                    card = Card.parse("AS")!!,
                    width = cardWidth,
                    elevation = 15.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            translationY = bobState.value.dp.toPx()
                            rotationZ = -1f
                        },
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = HokmPalette.muted, fontSize = 11.sp)
        }
        Box(
            Modifier
                .size(width = 34.dp, height = 3.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(HokmPalette.gold, HokmPalette.emerald))),
        )
    }
}

@Composable
internal fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .heightIn(min = 68.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(15.dp))
            .toggleable(value = checked, role = Role.Switch) { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HokmPalette.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, color = HokmPalette.muted, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val knob by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(180),
            label = "toggle-knob",
        )
        Box(
            Modifier
                .width(39.dp)
                .height(23.dp)
                .clip(CircleShape)
                .background(if (checked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.62f), CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = (2f + 15f * knob).dp)
                    .size(18.dp)
                    .shadow(3.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (checked) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun StatsCard(stats: HokmStats, onReset: () -> Unit, onTap: () -> Unit) {
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("کارنامهٔ شما", color = HokmPalette.goldLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("نتیجهٔ همهٔ بازی‌ها", color = HokmPalette.muted, fontSize = 10.sp)
            }
            Hokm3DButton(
                text = "پاک‌کردن",
                tone = HokmButtonTone.GLASS,
                height = 40.dp,
                modifier = Modifier.width(96.dp),
                onSound = onTap,
                onClick = onReset,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatCell("بازی", stats.games.toFa(), HokmIcon.Deck)
            StatCell("برد", stats.wins.toFa(), HokmIcon.Trophy)
            StatCell("کُت", stats.kots.toFa(), HokmIcon.Star)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            StatCell("درصد برد", "٪${stats.winRate.toFa()}", HokmIcon.Target)
            StatCell("دست برده", "${stats.handsWon.toFa()} / ${stats.hands.toFa()}", HokmIcon.Check)
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, icon: HokmIcon) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        HokmIconView(icon, color = HokmPalette.gold, size = 16.dp)
        Text(value, color = HokmPalette.text, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(label, color = HokmPalette.muted, fontSize = 10.sp)
    }
}

@Composable
private fun RulesCard(playerCount: Int) {
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
        Text("قوانین دورهمی", color = HokmPalette.goldLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        val common = listOf(
            "حاکم با دیدن پنج کارت اول، خالِ حکم را انتخاب می‌کند.",
            "اگر از خالِ زمین کارت داشته باشی، باید همان خال را بازی کنی.",
            "بالاترین حکم، یا در نبودِ حکم بالاترین کارتِ خالِ زمین، آن دور را می‌برد.",
            "اولین نفر یا تیمی که ۷ دور ببرد، آن دست را میگیرد (۱ امتیاز)؛ کُت ۲ و حاکمکُت ۳ امتیاز دارد.",
            "هر «دور» یک کارت از هر بازیکن است؛ «دست» تا ۷ دور و «مسابقه» تا امتیاز هدف (۱/۳/۵/۷) ادامه دارد.",
        )
        val variant = when (playerCount) {
            2 -> listOf("در دونفره ۳ کارت برای حاکم و ۲ کارت برای حریف سوزانده می‌شود؛ سپس نوبتی از دسته کارت می‌کشید.")
            3 -> listOf(
                "در سه‌نفره هر بازیکن برای خودش بازی می‌کند؛ یک کارت ۲ از دسته حذف می‌شود و هر نفر ۱۷ کارت دارد.",
                "کُت یعنی دست‌کم یکی از حریف‌ها صفر دور بگیرد؛ اگر ۱۷ دور تمام شود و دو نفر مساوی باشند، دست بی‌نتیجه است.",
            )
            else -> listOf("در چهار‌نفره، صندلی‌های روبه‌رو هم‌تیمی‌اند؛ امتیاز هر دست به کل تیم داده می‌شود.")
        }
        (common + variant).forEachIndexed { index, rule ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("${(index + 1).toFa()}.", color = HokmPalette.gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(rule, color = HokmPalette.text.copy(alpha = 0.9f), fontSize = 12.sp, lineHeight = 19.sp)
            }
        }
    }
}

/** Main identity panel with a softly animated fan of real vector playing cards. */
