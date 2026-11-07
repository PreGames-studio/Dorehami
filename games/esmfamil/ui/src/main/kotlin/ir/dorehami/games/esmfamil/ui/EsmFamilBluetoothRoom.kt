package ir.dorehami.games.esmfamil.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.dorehami.games.esmfamil.EsmFamilModule
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.view
import ir.dorehami.session.SessionClient
import kotlinx.coroutines.launch

/** How many seats a Bluetooth table opens with (the module's preferred size). */
private const val ROOM_SEATS = 4

/**
 * The Bluetooth table: everything a player on their own phone sees.
 *
 * Each phone writes its own sheet, so this is the same screen the offline table uses, fed by the
 * session instead of a local engine. The host phone is the authority (`HostController`), which is
 * why only it can open the sheets and deal the next letter.
 */
@Composable
fun EsmFamilBluetoothRoom(
    playerName: String,
    rounds: Int,
    categories: List<String>,
    letters: String,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val flow = remember {
        EsmFamilRoomFlow(context, scope, playerName, rounds, categories, letters, ROOM_SEATS)
    }
    // Leaving the screen must release the radio exactly once.
    DisposableEffect(flow) {
        onDispose { flow.leave() }
    }
    EsmFamilLobbyScreen(
        flow = flow,
        onExit = {
            flow.leave()
            onExit()
        },
    )
}

/**
 * The table itself once the host has started: session state in, actions out.
 *
 * The draft of my sheet lives here rather than in the room: the session only ever sees a finished
 * sheet, so a half-typed answer never travels over Bluetooth.
 */
@Composable
fun EsmFamilRoomTable(
    client: SessionClient,
    modeLabel: String,
    onExit: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clientState by client.state.collectAsState()
    val base = EsmFamilRoomModel.state(clientState, modeLabel)
    // A fresh draft every round; a submitted sheet shows what the session accepted.
    var draft by remember(base.round) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val state = base.copy(draft = if (base.hasWritten) base.mine else draft)

    // اسم فامیل has no bots, so an empty seat would never hand a sheet in. The host opens the
    // sheets as soon as every human is done — the same `finish_round` the offline table can press.
    LaunchedEffect(clientState.seq, clientState.phase) {
        if (clientState.isHost && EsmFamilRoomModel.allHumansSubmitted(clientState)) {
            scope.launch { client.send(GameAction(EsmFamilModule.ACTION_FINISH_ROUND)) }
        }
    }

    EsmFamilScreen(
        state = state,
        onType = { category, text -> draft = draft + (category to text) },
        onSubmit = {
            val answers = draft
            scope.launch {
                client.send(
                    GameAction(
                        type = EsmFamilModule.ACTION_SUBMIT,
                        payload = view {
                            put(
                                EsmFamilModule.PAYLOAD_ANSWERS,
                                view { state.categories.forEach { put(it, answers[it].orEmpty().trim()) } },
                            )
                        },
                    ),
                )
            }
            draft = emptyMap()
        },
        onCloseRound = {
            scope.launch { client.send(GameAction(EsmFamilModule.ACTION_FINISH_ROUND)) }
        },
        onNext = {
            scope.launch { client.send(GameAction(EsmFamilModule.ACTION_NEXT_ROUND)) }
        },
        // A room has no "again": the host closes it and the table goes back to the menu.
        onPlayAgain = onExit,
        onExit = onExit,
    )
}
