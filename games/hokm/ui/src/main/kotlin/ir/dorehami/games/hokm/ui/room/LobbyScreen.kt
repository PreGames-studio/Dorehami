package ir.dorehami.games.hokm.ui.room

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.session.Member
import ir.dorehami.transport.nearby.NearbyPermissions
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.uikit.toFa

private val Gold = Color(0xFFD9B55A)

/**
 * Everything between "Bluetooth / Online" and the table: choose, connect, wait in the lobby.
 * Game-agnostic: it only shows members and a start button.
 */
@Composable
fun LobbyScreen(
    flow: RoomFlow,
    playerCount: Int,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    val stage by flow.stage

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
            TextButton(onClick = onExit) { Text("بازگشت", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(
                if (flow.mode == RoomMode.BLUETOOTH) "📶 بلوتوث / آفلاین" else "🌐 آنلاین",
                color = Gold, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))

        when (val s = stage) {
            RoomStage.Choose -> if (flow.mode == RoomMode.BLUETOOTH) BluetoothChoose(flow, playerCount) else OnlineChoose(flow, playerCount, serverUrl, onServerUrl)
            is RoomStage.Connecting -> Waiting(s.message)
            RoomStage.Discovering -> Discovering(flow)
            is RoomStage.Lobby -> Lobby(flow, s, playerCount)
            is RoomStage.Playing -> Waiting("میز آماده می‌شود…")
            is RoomStage.Failed -> Failed(s.message, onExit)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun BluetoothChoose(flow: RoomFlow, playerCount: Int) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pending?.invoke()
        pending = null
    }
    fun withPermissions(action: () -> Unit) {
        if (NearbyPermissions.allGranted(context)) action() else { pending = action; launcher.launch(NearbyPermissions.required()) }
    }

    Panel {
        Text("دور هم، بدون اینترنت", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "یک نفر اتاق می‌سازد، بقیه از فهرست اتاق‌های اطراف به آن می‌پیوندند. گوشی‌ها باید نزدیک هم باشند و بلوتوث روشن باشد. میز ${playerCount.toFa()} نفره؛ صندلی‌های خالی را ربات پر می‌کند.",
            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        BigButton("ساختن اتاق (میزبان)") { withPermissions { flow.hostBluetooth() } }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { withPermissions { flow.joinBluetooth() } }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("پیوستن به اتاق دوستان", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OnlineChoose(flow: RoomFlow, playerCount: Int, serverUrl: String, onServerUrl: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    Panel {
        Text("بازی آنلاین", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            "میز ${playerCount.toFa()} نفره. اتاق بساز و کدش را برای دوستانت بفرست، با کد به اتاق آن‌ها برو، یا حریف تصادفی بگیر.",
            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        BigButton("ساختن اتاق") { flow.createOnline() }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(5) },
                label = { Text("کد اتاق", color = Color.White.copy(alpha = 0.7f)) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { flow.joinOnline(code) }, enabled = code.length == 5, shape = RoundedCornerShape(14.dp)) { Text("پیوستن") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { flow.randomOnline() }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Text("حریف تصادفی 🎲", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrl,
            label = { Text("آدرس سرور", color = Color.White.copy(alpha = 0.7f)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("نسخهٔ خودمیزبان: سرور را طبق docs/DEPLOY.md بالا بیاور و آدرسش را اینجا بگذار.", color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

@Composable
private fun Discovering(flow: RoomFlow) {
    val rooms by (flow.nearbyRooms?.collectAsState() ?: remember { mutableStateOf(emptyList<NearbyRoom>()) })
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = Gold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("اتاق‌های اطراف", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (rooms.isEmpty()) {
            Text("هنوز اتاقی پیدا نشده… میزبان باید «ساختن اتاق» را زده باشد.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        rooms.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { flow.connectBluetooth(r) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("اتاق ${r.name}", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("کد ${r.roomCode}", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                Text("پیوستن ←", color = Gold, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Lobby(flow: RoomFlow, s: RoomStage.Lobby, playerCount: Int) {
    val st by s.client.state.collectAsState()
    val members = st.room?.members?.sortedBy { it.seat } ?: emptyList()
    val humans = members.count { !it.isBot }
    Panel {
        Text("کد اتاق", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        Text(s.code, color = Gold, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Text(s.hint, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        (0 until playerCount).forEach { seat ->
            val m = members.firstOrNull { it.seat == seat }
            SeatRow(seat, m, isMe = m?.player?.id == st.me)
            Spacer(Modifier.height(6.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (!st.connected) {
            Text("ارتباط قطع است… در حال تلاش برای برگشت", color = Color(0xFFFFB4A9), fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (s.canStart) {
            BigButton(if (humans < playerCount) "شروع (${(playerCount - humans).toFa()} صندلی با ربات)" else "شروع بازی") { flow.start() }
        } else {
            val ready = members.firstOrNull { it.player.id == st.me }?.ready == true
            OutlinedButton(onClick = { flow.setReady(!ready) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (ready) "آماده‌ام ✓" else "آماده‌ام؟", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = Gold, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Text("منتظر شروع از طرف میزبان…", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SeatRow(seat: Int, m: Member?, isMe: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (isMe) Gold else Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (m == null) Color.White.copy(alpha = 0.1f) else if (m.isBot) Color(0xFF4A6FA5) else Color(0xFF2E7D4F)),
            contentAlignment = Alignment.Center,
        ) {
            Text(if (m == null) "·" else if (m.isBot) "🤖" else m.player.displayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    m == null -> "صندلی خالی (ربات می‌نشیند)"
                    isMe -> "${m.player.displayName} (شما)"
                    else -> m.player.displayName
                },
                color = Color.White, fontWeight = FontWeight.SemiBold,
            )
            Text(
                when {
                    m == null -> "صندلی ${(seat + 1).toFa()}"
                    m.isHost -> "میزبان"
                    !m.connected -> "قطع شده"
                    m.ready -> "آماده"
                    else -> "در انتظار"
                },
                color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
            )
        }
        if (m != null && !m.isBot) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (m.connected) Color(0xFF6BD48A) else Color(0xFFE57373)))
        }
    }
}

@Composable
private fun Waiting(message: String) {
    Panel {
        CircularProgressIndicator(color = Gold)
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Failed(message: String, onExit: () -> Unit) {
    Panel {
        Text("نشد 😕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        BigButton("بازگشت", onExit)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xCC102A1A))
            .border(1.dp, Gold.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun BigButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
