package ir.dorehami.games.chess.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.dorehami.games.chess.ui.room.ChessRoomFlow
import ir.dorehami.games.chess.ui.room.ChessRoomMode
import ir.dorehami.games.chess.ui.room.ChessRoomScreen
import ir.dorehami.games.chess.ui.room.ChessRoomStage
import ir.dorehami.session.SessionClient
import ir.dorehami.uikit.rememberSoundPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

private enum class ChessDestination { MENU, OFFLINE, ONLINE, SETTINGS, SOLO_SETUP, PASS_SETUP, ROOM }

/**
 * Chess entry flow: online (demo), offline (solo/local/Bluetooth/hotspot), appearance settings,
 * and the chess-only cosmetic store. [ownedStoreItems] and [coinBalance] are supplied by the
 * app's offline wallet; the chess module has no app dependency.
 */
@Composable
fun ChessEntry(
    onExit: () -> Unit,
    onOpenStore: () -> Unit = {},
    ownedStoreItems: Set<String> = emptySet(),
    coinBalance: Long = 0,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("chess-ui-settings", 0) }
    var destination by remember { mutableStateOf(ChessDestination.MENU) }
    var localMode by remember { mutableStateOf<ChessMode?>(null) }
    var localMatchKey by rememberSaveable { mutableStateOf(0) }
    var roomFlow by remember { mutableStateOf<ChessRoomFlow?>(null) }
    val scope = rememberCoroutineScope()

    var playerName by rememberSaveable { mutableStateOf(preferences.getString("playerName", "") ?: "") }
    var level by rememberSaveable { mutableStateOf(2) }
    var color by rememberSaveable { mutableStateOf(ColorChoice.WHITE) }
    var animationsEnabled by rememberSaveable {
        mutableStateOf(preferences.getBoolean("animationsEnabled", true))
    }
    var soundOn by rememberSaveable { mutableStateOf(preferences.getBoolean("soundOn", true)) }
    var autoRotate by rememberSaveable { mutableStateOf(preferences.getBoolean("autoRotate", true)) }
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

    // A refunded/removed store item must not leave an inaccessible appearance selected forever.
    LaunchedEffect(ownedStoreItems) {
        if (!boardStyle.isUnlocked(ownedStoreItems)) {
            boardStyle = ChessBoardStyle.WOOD
            preferences.edit().putString("boardStyle", boardStyle.name).apply()
        }
        if (!pieceStyle.isUnlocked(ownedStoreItems)) {
            pieceStyle = ChessPieceStyle.CLASSIC
            preferences.edit().putString("pieceStyle", pieceStyle.name).apply()
        }
    }

    fun updateName(value: String) {
        playerName = value
        preferences.edit().putString("playerName", value).apply()
    }

    fun openRoom(mode: ChessRoomMode) {
        roomFlow?.leave()
        destination = ChessDestination.ROOM
        roomFlow = ChessRoomFlow(context, scope, mode) { playerName }
    }

    fun closeRoom() {
        roomFlow?.leave()
        roomFlow = null
        destination = ChessDestination.OFFLINE
    }

    DisposableEffect(Unit) {
        onDispose { roomFlow?.leave() }
    }

    ChessTheme(motionEnabled = animationsEnabled) {
        val activeRoom = roomFlow
        val activeLocalMode = localMode
        when {
            activeLocalMode != null -> ChessMatchHost(
                key = localMatchKey,
                playerName = playerName,
                level = level,
                color = color,
                mode = activeLocalMode,
                autoRotate = autoRotate,
                soundOn = soundOn,
                boardStyle = boardStyle,
                pieceStyle = pieceStyle,
                onPlayAgain = { localMatchKey++ },
                onExit = {
                    localMode = null
                    destination = ChessDestination.OFFLINE
                },
            )

            activeRoom != null -> {
                val roomStage by activeRoom.stage
                when (val current = roomStage) {
                    is ChessRoomStage.Playing -> ChessNetworkMatchHost(
                        client = current.client,
                        roomMode = current.mode,
                        soundOn = soundOn,
                        boardStyle = boardStyle,
                        pieceStyle = pieceStyle,
                        onExit = { closeRoom() },
                    )
                    else -> ChessRoomScreen(
                        flow = activeRoom,
                        playerName = playerName,
                        onName = { updateName(it) },
                        onBack = { closeRoom() },
                    )
                }
            }

            else -> when (destination) {
                ChessDestination.MENU -> ChessMainMenu(
                    onOffline = { destination = ChessDestination.OFFLINE },
                    onOnline = { destination = ChessDestination.ONLINE },
                    onSettings = { destination = ChessDestination.SETTINGS },
                    onStore = onOpenStore,
                    coinBalance = coinBalance,
                    onBack = onExit,
                )
                ChessDestination.OFFLINE -> ChessOfflineMenu(
                    onSolo = { destination = ChessDestination.SOLO_SETUP },
                    onPassAndPlay = { destination = ChessDestination.PASS_SETUP },
                    onBluetooth = { openRoom(ChessRoomMode.BLUETOOTH) },
                    onHotspot = { openRoom(ChessRoomMode.HOTSPOT) },
                    onBack = { destination = ChessDestination.MENU },
                )
                ChessDestination.ONLINE -> ChessOnlineDemo(onBack = { destination = ChessDestination.MENU })
                ChessDestination.SETTINGS -> ChessSettingsScreen(
                    boardStyle = boardStyle,
                    onBoardStyle = {
                        if (it.isUnlocked(ownedStoreItems)) {
                            boardStyle = it
                            preferences.edit().putString("boardStyle", it.name).apply()
                        }
                    },
                    pieceStyle = pieceStyle,
                    onPieceStyle = {
                        if (it.isUnlocked(ownedStoreItems)) {
                            pieceStyle = it
                            preferences.edit().putString("pieceStyle", it.name).apply()
                        }
                    },
                    ownedStoreItems = ownedStoreItems,
                    animationsEnabled = animationsEnabled,
                    onAnimationsEnabled = {
                        animationsEnabled = it
                        preferences.edit().putBoolean("animationsEnabled", it).apply()
                    },
                    soundOn = soundOn,
                    onSoundOn = {
                        soundOn = it
                        preferences.edit().putBoolean("soundOn", it).apply()
                    },
                    autoRotate = autoRotate,
                    onAutoRotate = {
                        autoRotate = it
                        preferences.edit().putBoolean("autoRotate", it).apply()
                    },
                    onBack = { destination = ChessDestination.MENU },
                )
                ChessDestination.SOLO_SETUP, ChessDestination.PASS_SETUP -> ChessLocalSetupScreen(
                    passAndPlay = destination == ChessDestination.PASS_SETUP,
                    name = playerName,
                    onName = { updateName(it) },
                    level = level,
                    onLevel = { level = it },
                    color = color,
                    onColor = { color = it },
                    onStart = {
                        localMatchKey++
                        localMode = if (destination == ChessDestination.PASS_SETUP) ChessMode.PASS_AND_PLAY else ChessMode.VS_BOT
                    },
                    onBack = { destination = ChessDestination.OFFLINE },
                )
                ChessDestination.ROOM -> Unit // An active room is handled above.
            }
        }
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
    val scope = remember(key) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(key) {
        ChessLocalMatch(
            scope = scope,
            mode = mode,
            playerName = playerName,
            colorChoice = color,
            botLevel = level,
            autoRotate = autoRotate,
            // The seed is selected in UI and recorded; game rules never read the wall clock.
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
private fun ChessNetworkMatchHost(
    client: SessionClient,
    roomMode: ChessRoomMode,
    soundOn: Boolean,
    boardStyle: ChessBoardStyle,
    pieceStyle: ChessPieceStyle,
    onExit: () -> Unit,
) {
    val sounds = rememberSoundPlayer(ChessSounds.map)
    LaunchedEffect(soundOn) { sounds.enabled = soundOn }
    val scope = remember(client) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(client) { ChessSessionMatch(scope, client, roomMode, onSound = { sounds.play(it) }) }
    DisposableEffect(match) {
        match.begin()
        onDispose {
            match.stop()
            scope.cancel()
        }
    }
    val state by match.state
    ChessScreen(
        state = state,
        onSquareTap = match::onSquareTap,
        onPromotion = match::choosePromotion,
        onFlip = match::flip,
        onUndo = {},
        onResign = match::resign,
        onOfferDraw = match::offerDraw,
        onAnswerDraw = match::answerDraw,
        onClearError = match::clearError,
        onClearNote = match::clearNote,
        onPlayAgain = onExit,
        onExit = onExit,
        boardStyle = boardStyle,
        pieceStyle = pieceStyle,
        playAgainLabel = "اتاق تازه",
    )
}
