package ir.dorehami.games.hokm.ui.room

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.ui.Hokm3DButton
import ir.dorehami.games.hokm.ui.HokmBackdrop
import ir.dorehami.games.hokm.ui.HokmButtonTone
import ir.dorehami.games.hokm.ui.HokmGlassPanel
import ir.dorehami.games.hokm.ui.HokmPalette
import ir.dorehami.games.hokm.ui.HokmPill
import ir.dorehami.session.Member
import ir.dorehami.transport.nearby.NearbyPermissions
import ir.dorehami.transport.nearby.NearbyRoom
import ir.dorehami.uikit.toFa

/**
 * Everything between the game menu and table. The same premium lobby is used for Bluetooth and online rooms.
 */
@Composable
fun LobbyScreen(
    flow: RoomFlow,
    playerCount: Int,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    onExit: () -> Unit,
    onTap: () -> Unit = {},
) {
    BackHandler(onBack = onExit)
    val stage by flow.stage

    Box(Modifier.fillMaxSize()) {
        HokmBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Hokm3DButton(
                    text = "بازگشت",
                    leading = "‹",
                    tone = HokmButtonTone.GLASS,
                    height = 42.dp,
                    modifier = Modifier.width(104.dp),
                    onSound = onTap,
                    onClick = onExit,
                )
                Spacer(Modifier.weight(1f))
                HokmPill(
                    text = if (flow.mode == RoomMode.BLUETOOTH) "بلوتوث · آفلاین" else "بازی آنلاین",
                    icon = if (flow.mode == RoomMode.BLUETOOTH) "⌁" else "◎",
                    color = HokmPalette.goldLight,
                )
            }
            Spacer(Modifier.height(18.dp))
            when (val current = stage) {
                RoomStage.Choose -> if (flow.mode == RoomMode.BLUETOOTH) {
                    BluetoothChoose(flow, playerCount, onTap)
                } else {
                    OnlineChoose(flow, playerCount, serverUrl, onServerUrl, onTap)
                }
                is RoomStage.Connecting -> Waiting(current.message)
                RoomStage.Discovering -> Discovering(flow, onTap)
                is RoomStage.Lobby -> Lobby(flow, current, playerCount, onTap)
                is RoomStage.Playing -> Waiting("میز آماده می‌شود…")
                is RoomStage.Failed -> Failed(current.message, onExit, onTap)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BluetoothChoose(flow: RoomFlow, playerCount: Int, onTap: () -> Unit) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pending?.invoke()
        pending = null
    }
    fun withPermissions(action: () -> Unit) {
        if (NearbyPermissions.allGranted(context)) action() else {
            pending = action
            launcher.launch(NearbyPermissions.required())
        }
    }

    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 19.dp) {
        HokmPill("نزدیک هم، بدون اینترنت", icon = "⌁", color = HokmPalette.mint)
        Spacer(Modifier.height(10.dp))
        Text("دورهمیِ واقعی", color = HokmPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(
            "یک نفر اتاق را می‌سازد؛ بقیه با بلوتوث وصل می‌شوند. میز ${playerCount.toFa()} نفره است و صندلی‌های خالی را ربات پر می‌کند.",
            color = HokmPalette.muted,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Hokm3DButton(
            text = "ساختن اتاق",
            subtitle = "میزبان شو و دوستانت را دعوت کن",
            leading = "＋",
            tone = HokmButtonTone.EMERALD,
            height = 62.dp,
            modifier = Modifier.fillMaxWidth(),
            onSound = onTap,
            onClick = { withPermissions { flow.hostBluetooth() } },
        )
        Spacer(Modifier.height(10.dp))
        Hokm3DButton(
            text = "پیوستن به اتاق دوستان",
            subtitle = "اتاق‌های نزدیک را پیدا کن",
            leading = "↗",
            tone = HokmButtonTone.GLASS,
            height = 60.dp,
            modifier = Modifier.fillMaxWidth(),
            onSound = onTap,
            onClick = { withPermissions { flow.joinBluetooth() } },
        )
        Spacer(Modifier.height(9.dp))
        Text("برای پیدا شدن اتاق‌ها، بلوتوث دستگاه را روشن کن.", color = HokmPalette.muted, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OnlineChoose(
    flow: RoomFlow,
    playerCount: Int,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    onTap: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 19.dp) {
        HokmPill("بازی از راه دور", icon = "◎", color = HokmPalette.mint)
        Spacer(Modifier.height(10.dp))
        Text("میزت را آنلاین بچین", color = HokmPalette.text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(
            "میز ${playerCount.toFa()} نفره؛ اتاق بساز، با کد وارد شو یا حریف تصادفی پیدا کن.",
            color = HokmPalette.muted,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Hokm3DButton(
            text = "ساختن اتاق خصوصی",
            subtitle = "کد را برای دوستانت بفرست",
            leading = "＋",
            tone = HokmButtonTone.EMERALD,
            modifier = Modifier.fillMaxWidth(),
            height = 60.dp,
            onSound = onTap,
            onClick = { flow.createOnline() },
        )
        Spacer(Modifier.height(13.dp))
        Text("یا با کد اتاق وارد شو", color = HokmPalette.text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = RoomFlow.normalizeRoomCodeInput(it) },
                label = { Text("کد ۵ حرفی اتاق") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = roomFieldColors(),
            )
            Hokm3DButton(
                text = "ورود",
                tone = HokmButtonTone.GOLD,
                height = 54.dp,
                modifier = Modifier.width(86.dp),
                enabled = code.length == 5,
                onSound = onTap,
                onClick = { flow.joinOnline(code) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Hokm3DButton(
            text = "حریف تصادفی",
            subtitle = "اگر منتظر بمانی، ربات هم وارد می‌شود",
            leading = "✦",
            tone = HokmButtonTone.GLASS,
            modifier = Modifier.fillMaxWidth(),
            height = 58.dp,
            onSound = onTap,
            onClick = { flow.randomOnline() },
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = serverUrl,
            onValueChange = onServerUrl,
            label = { Text("آدرس سرور بازی") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = roomFieldColors(),
        )
        Spacer(Modifier.height(5.dp))
        Text("برای سرور شخصی، راهنمای نصب در docs/DEPLOY.md است.", color = HokmPalette.muted, fontSize = 10.sp)
    }
}

@Composable
private fun roomFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = HokmPalette.text,
    unfocusedTextColor = HokmPalette.text,
    focusedBorderColor = HokmPalette.gold,
    unfocusedBorderColor = Color(0x6677C7A5),
    focusedLabelColor = HokmPalette.goldLight,
    unfocusedLabelColor = HokmPalette.muted,
    cursorColor = HokmPalette.goldLight,
)

@Composable
private fun Discovering(flow: RoomFlow, onTap: () -> Unit) {
    val rooms by (flow.nearbyRooms?.collectAsState() ?: remember { mutableStateOf(emptyList<NearbyRoom>()) })
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(color = HokmPalette.gold, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("اتاق‌های اطراف", color = HokmPalette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        if (rooms.isEmpty()) {
            Text("هنوز اتاقی پیدا نشده؛ از میزبان بخواه «ساختن اتاق» را بزند.", color = HokmPalette.muted, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        rooms.forEach { room ->
            Hokm3DButton(
                text = "اتاق ${room.name}",
                subtitle = "کد ${room.roomCode} · برای پیوستن بزن",
                leading = "↗",
                tone = HokmButtonTone.GLASS,
                modifier = Modifier.fillMaxWidth(),
                height = 58.dp,
                onSound = onTap,
                onClick = { flow.connectBluetooth(room) },
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Lobby(flow: RoomFlow, state: RoomStage.Lobby, playerCount: Int, onTap: () -> Unit) {
    val clientState by state.client.state.collectAsState()
    val members = clientState.room?.members?.sortedBy { it.seat } ?: emptyList()
    val humans = members.count { !it.isBot }
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 18.dp) {
        Text("کد اتاق", color = HokmPalette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF174B3B), Color(0xFF0B2A21))))
                .border(1.dp, HokmPalette.gold.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(vertical = 12.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(state.code, color = HokmPalette.goldLight, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(state.hint, color = HokmPalette.muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(13.dp))
        (0 until playerCount).forEach { seat ->
            val member = members.firstOrNull { it.seat == seat }
            SeatRow(seat, member, isMe = member?.player?.id == clientState.me)
            Spacer(Modifier.height(7.dp))
        }
        if (!clientState.connected) {
            HokmPill("ارتباط قطع است · تلاش برای اتصال مجدد", icon = "!", color = HokmPalette.coral)
            Spacer(Modifier.height(10.dp))
        }
        if (state.canStart) {
            Hokm3DButton(
                text = if (humans < playerCount) "شروع با ربات‌های جایگزین" else "شروع بازی",
                subtitle = if (humans < playerCount) "ربات ${(playerCount - humans).toFa()} صندلی خالی را پر می‌کند" else "میز برای همهٔ اعضا شروع می‌شود",
                leading = "▶",
                tone = HokmButtonTone.EMERALD,
                modifier = Modifier.fillMaxWidth(),
                height = 60.dp,
                onSound = onTap,
                onClick = { flow.start() },
            )
        } else {
            val ready = members.firstOrNull { it.player.id == clientState.me }?.ready == true
            Hokm3DButton(
                text = if (ready) "آماده‌ام ✓" else "اعلام آمادگی",
                subtitle = "میزبان پس از آماده‌شدن، بازی را شروع می‌کند",
                leading = "✓",
                tone = if (ready) HokmButtonTone.EMERALD else HokmButtonTone.GOLD,
                modifier = Modifier.fillMaxWidth(),
                height = 58.dp,
                onSound = onTap,
                onClick = { flow.setReady(!ready) },
            )
            Spacer(Modifier.height(8.dp))
            Text("منتظر شروع از طرف میزبان…", color = HokmPalette.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun SeatRow(seat: Int, member: Member?, isMe: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isMe) HokmPalette.gold.copy(alpha = 0.14f) else Color(0xFF12372D))
            .border(1.dp, if (isMe) HokmPalette.gold.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        if (member == null) listOf(Color(0xFF50635B), Color(0xFF2A3A34))
                        else if (member.isBot) listOf(Color(0xFF4E9179), Color(0xFF1A624C))
                        else listOf(Color(0xFFE5C877), Color(0xFFAE7A31)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    member == null -> "·"
                    member.isBot -> "♟"
                    else -> member.player.displayName.take(1)
                },
                color = HokmPalette.night,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    member == null -> "صندلی خالی"
                    isMe -> "${member.player.displayName} (شما)"
                    else -> member.player.displayName
                },
                color = HokmPalette.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                when {
                    member == null -> "صندلی ${(seat + 1).toFa()} · ربات می‌نشیند"
                    member.isBot -> "ربات"
                    member.isHost -> "میزبان"
                    !member.connected -> "قطع شده"
                    member.ready -> "آماده"
                    else -> "در انتظار"
                },
                color = HokmPalette.muted,
                fontSize = 10.sp,
            )
        }
        if (member != null && !member.isBot) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (member.connected) HokmPalette.mint else HokmPalette.coral),
            )
        }
    }
}

@Composable
private fun Waiting(message: String) {
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 24.dp) {
        CircularProgressIndicator(color = HokmPalette.gold, strokeWidth = 3.dp)
        Spacer(Modifier.height(14.dp))
        Text(message, color = HokmPalette.text, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
    }
}

@Composable
private fun Failed(message: String, onExit: () -> Unit, onTap: () -> Unit) {
    HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 21.dp) {
        Text("ارتباط برقرار نشد", color = HokmPalette.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = HokmPalette.muted, fontSize = 13.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(15.dp))
        Hokm3DButton(
            text = "بازگشت به منو",
            leading = "‹",
            tone = HokmButtonTone.GOLD,
            modifier = Modifier.fillMaxWidth(),
            onSound = onTap,
            onClick = onExit,
        )
    }
}
