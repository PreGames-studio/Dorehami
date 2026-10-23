package ir.dorehami.games.chess.ui.room

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.dorehami.games.chess.ui.ChessGradientButton
import ir.dorehami.games.chess.ui.ChessMenuPage
import ir.dorehami.games.chess.ui.ChessTonalButton
import ir.dorehami.games.chess.ui.ChessButtonTone
import ir.dorehami.games.chess.ui.SettingsCard
import ir.dorehami.games.chess.ui.chessOutlinedFieldColors
import ir.dorehami.games.chess.ui.chessSp
import ir.dorehami.session.Member
import ir.dorehami.transport.hotspot.HotspotTransport
import ir.dorehami.transport.nearby.NearbyPermissions
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.uikit.toFa

/** Real offline lobby for both Bluetooth Nearby and direct hotspot play. */
@Composable
fun ChessRoomScreen(
    flow: ChessRoomFlow,
    playerName: String,
    onName: (String) -> Unit,
    onBack: () -> Unit,
) {
    val stage by flow.stage
    val notice by flow.notice
    val title = if (flow.mode == ChessRoomMode.BLUETOOTH) "بلوتوث" else "هات‌اسپات"
    ChessMenuPage(
        title = "شطرنج با $title",
        subtitle = "اتاق آفلاین · میزبان مرجع حرکت‌هاست",
        onBack = onBack,
    ) {
        when (val current = stage) {
            ChessRoomStage.Choose -> RoomChoose(flow, playerName, onName)
            is ChessRoomStage.Connecting -> Waiting(current.message)
            ChessRoomStage.Discovering -> Discovering(flow)
            is ChessRoomStage.Lobby -> Lobby(flow, current)
            is ChessRoomStage.Playing -> Waiting("میز شطرنج آماده می‌شود…")
            is ChessRoomStage.Failed -> Failed(current.message, onBack)
        }
        notice?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
                fontSize = chessSp(12),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RoomChoose(flow: ChessRoomFlow, playerName: String, onName: (String) -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(HotspotTransport.DEFAULT_PORT.toString()) }
    var permissionMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val action = pending
        pending = null
        if (result.values.all { it }) action?.invoke() else permissionMessage = "برای اتصال بلوتوث، اجازه‌های Nearby لازم است."
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    fun withPermissions(action: () -> Unit) {
        if (NearbyPermissions.allGranted(context)) action() else {
            pending = action
            permissionLauncher.launch(NearbyPermissions.required())
        }
    }

    SettingsCard {
        OutlinedTextField(
            value = playerName,
            onValueChange = { if (it.length <= 14) onName(it) },
            label = { Text("نام شما در اتاق") },
            placeholder = { Text("مثلاً تاله") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = chessOutlinedFieldColors(),
        )
        Text(
            if (flow.mode == ChessRoomMode.BLUETOOTH) {
                "یک نفر اتاق می‌سازد و نفر دوم اتاق نزدیک را انتخاب می‌کند. اینترنت لازم نیست."
            } else {
                "یک نفر میزبان می‌شود؛ نفر دوم IP و پورت نمایش‌داده‌شده را وارد می‌کند. هر دو گوشی روی یک هات‌اسپات باشند."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(12),
            lineHeight = chessSp(19),
        )
        if (flow.mode == ChessRoomMode.BLUETOOTH) {
            ChessGradientButton("ساختن اتاق بلوتوث", { withPermissions { flow.hostBluetooth() } }, Modifier.fillMaxWidth())
            ChessTonalButton(
                text = "پیوستن به اتاق نزدیک",
                onClick = { withPermissions { flow.joinBluetooth() } },
                modifier = Modifier.fillMaxWidth(),
                tone = ChessButtonTone.Secondary,
            )
        } else {
            ChessGradientButton("میزبان روی هات‌اسپات شو", { flow.hostHotspot() }, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("IP گوشی میزبان") },
                placeholder = { Text("مثلاً 192.168.43.1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = chessOutlinedFieldColors(),
            )
            OutlinedTextField(
                value = port,
                onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("پورت") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = chessOutlinedFieldColors(),
            )
            ChessTonalButton(
                text = "اتصال به میزبان",
                onClick = { flow.joinHotspot(address, port) },
                modifier = Modifier.fillMaxWidth(),
                tone = ChessButtonTone.Secondary,
            )
        }
        permissionMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = chessSp(12)) }
    }
}

@Composable
private fun Waiting(message: String) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Text(message, color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(14))
        }
    }
}

@Composable
private fun Discovering(flow: ChessRoomFlow) {
    val rooms = flow.nearbyRooms?.collectAsState()?.value.orEmpty()
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("اتاق‌های شطرنج اطراف", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
        if (rooms.isEmpty()) {
            Text("هنوز اتاقی پیدا نشد؛ از میزبان بخواه اتاق بسازد و گوشی‌ها نزدیک باشند.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(12))
        }
        rooms.forEach { room ->
            RoomButton(room, onClick = { flow.connectBluetooth(room) })
        }
    }
}

@Composable
private fun RoomButton(room: NearbyRoom, onClick: () -> Unit) {
    ChessTonalButton(
        text = "اتاق ${room.name} · ${room.roomCode}",
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        tone = ChessButtonTone.Primary,
    )
}

@Composable
private fun Lobby(flow: ChessRoomFlow, stage: ChessRoomStage.Lobby) {
    val clientState by stage.client.state.collectAsState()
    val members = clientState.room?.members?.sortedBy { it.seat }.orEmpty()
    val humans = members.count { !it.isBot }
    SettingsCard {
        if (stage.hotspotAddress != null) {
            Text("آدرس اتصال برای بازیکن دوم", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(12))
            Text(
                stage.hotspotAddress,
                color = MaterialTheme.colorScheme.primary,
                fontSize = chessSp(18),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text("کد اتاق", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(12))
            Text(
                stage.code,
                color = MaterialTheme.colorScheme.primary,
                fontSize = chessSp(34),
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stage.hint, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(12), lineHeight = chessSp(18), textAlign = TextAlign.Center)
        (0 until 2).forEach { seat ->
            SeatRow(seat, members.firstOrNull { it.seat == seat }, isMe = members.firstOrNull { it.seat == seat }?.player?.id == clientState.me)
        }
        Text(
            if (humans == 2) "هر دو بازیکن وصل هستند." else "در انتظار بازیکن دوم… (${humans.toFa()} از ۲)",
            color = if (humans == 2) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(12),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (stage.canStart) {
            ChessGradientButton(
                text = "شروع بازی دونفره",
                onClick = { flow.start() },
                modifier = Modifier.fillMaxWidth(),
                enabled = humans == 2,
            )
        } else {
            val ready = members.firstOrNull { it.player.id == clientState.me }?.ready == true
            ChessTonalButton(
                text = if (ready) "آماده‌ام ✓" else "اعلام آمادگی",
                onClick = { flow.setReady(!ready) },
                modifier = Modifier.fillMaxWidth(),
                tone = if (ready) ChessButtonTone.Secondary else ChessButtonTone.Primary,
            )
            Text("میزبان بازی را شروع می‌کند.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(11), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SeatRow(seat: Int, member: Member?, isMe: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (seat == 0) "♔" else "♚", fontSize = chessSp(24), modifier = Modifier.padding(end = 10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                member?.player?.displayName ?: "صندلی خالی",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = chessSp(14),
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    member == null -> "منتظر اتصال"
                    member.connected.not() -> "قطع شده"
                    isMe -> "شما"
                    else -> "وصل شده"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = chessSp(11),
            )
        }
        if (member?.ready == true) Text("آماده", color = MaterialTheme.colorScheme.secondary, fontSize = chessSp(11))
    }
}

@Composable
private fun Failed(message: String, onBack: () -> Unit) {
    SettingsCard {
        Text("اتصال برقرار نشد", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, fontSize = chessSp(17))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(13), lineHeight = chessSp(20))
        ChessGradientButton("بازگشت به آفلاین", onBack, Modifier.fillMaxWidth())
    }
}
