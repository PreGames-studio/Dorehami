package ir.dorehami.games.backgammon.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.transport.nearby.NearbyPermissions
import ir.dorehami.transport.nearby.NearbyRoom

/**
 * The two-tap lobby for a Bluetooth backgammon table: pick host or join, then wait for the other
 * phone. Same words the hokm lobby uses, so the two offline rooms behave the same way.
 */
@Composable
fun BackgammonLobbyScreen(
    flow: BackgammonRoomFlow,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val stage by flow.stage
    // A transport only exists after host/join; before that the list is simply empty.
    val roomsFlow = flow.nearbyRooms
    val rooms: List<NearbyRoom> = if (roomsFlow != null) {
        val state by roomsFlow.collectAsState(initial = emptyList())
        state
    } else {
        emptyList()
    }

    // Ask for the Nearby permissions once, then run the tap the player already made — otherwise the
    // button looks dead on a fresh install.
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) pendingAction?.invoke()
        pendingAction = null
    }
    val withBluetooth: (() -> Unit) -> Unit = { action ->
        if (NearbyPermissions.allGranted(context)) {
            action()
        } else {
            pendingAction = action
            permissions.launch(NearbyPermissions.required())
        }
    }

    BackgammonPage {
        BackgammonHeader(title = "تخته نرد بلوتوثی", subtitle = "دو گوشی نزدیک، بدون اینترنت", onBack = onExit)
        when (val current = stage) {
            BackgammonRoomStage.Choose -> {
                BackgammonMenuAction(
                    icon = BackgammonIcon.Bluetooth,
                    title = "ساختن میز",
                    subtitle = "تو سفید بازی می‌کنی؛ حریف به میز تو وصل می‌شود",
                    onClick = { withBluetooth { flow.host() } },
                )
                BackgammonMenuAction(
                    icon = BackgammonIcon.TwoPlayers,
                    title = "پیوستن به میز",
                    subtitle = "میزهای اطراف را ببین و یکی را انتخاب کن",
                    onClick = {
                        withBluetooth {
                            flow.discover()
                        }
                    },
                )
                BackgammonCard(Modifier.fillMaxWidth()) {
                    Text("چطور کار می‌کند", color = BackgammonPalette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "یکی از دو گوشی میز می‌سازد و یک کد نشان می‌دهد؛ گوشی دیگر «پیوستن» را می‌زند و همان میز را انتخاب می‌کند. " +
                            "هر دو گوشی باید بلوتوث روشن و اجازهٔ «نزدیک» داشته باشند. بعد از شروع، هر حرکت با قواعد همان میز آفلاین بررسی می‌شود.",
                        color = BackgammonPalette.inkSoft,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            is BackgammonRoomStage.Connecting -> Note(current.message)
            BackgammonRoomStage.Discovering -> {
                Note("در حال جست‌وجوی میزهای اطراف…")
                if (rooms.isEmpty()) {
                    Note("هنوز میزی پیدا نشد. مطمئن شو گوشی میزبان روی همین صفحه است.")
                } else {
                    rooms.forEach { room ->
                        RoomRow(room) { flow.join(room) }
                    }
                }
            }

            is BackgammonRoomStage.Lobby -> {
                BackgammonCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = "کد میز: ${current.code}",
                        color = BackgammonPalette.ink,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (current.members >= 2) "حریف وصل شد؛ می‌توانی شروع کنی." else "منتظر حریف…",
                        color = BackgammonPalette.inkSoft,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(current.hint, color = BackgammonPalette.inkSoft, fontSize = 12.sp, lineHeight = 18.sp)
                    if (current.isHost) {
                        Spacer(Modifier.height(12.dp))
                        BigButton(
                            text = if (current.members >= 2) "شروع بازی" else "منتظر وصل شدن حریف…",
                            enabled = current.members >= 2,
                            onClick = { flow.start() },
                        )
                    }
                }
            }

            is BackgammonRoomStage.Playing -> Note("تخته آماده می‌شود…")
            is BackgammonRoomStage.Failed -> {
                Note(current.message)
                BigButton(text = "بازگشت به انتخاب", enabled = true, onClick = onExit)
            }
        }
    }
}

@Composable
private fun Note(text: String) {
    BackgammonCard(Modifier.fillMaxWidth()) {
        Text(text, color = BackgammonPalette.inkSoft, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun RoomRow(room: NearbyRoom, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BackgammonPalette.cardAlt)
            .border(1.dp, BackgammonPalette.line.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(room.name, color = BackgammonPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("کد ${room.roomCode}", color = BackgammonPalette.inkSoft, fontSize = 11.sp)
        }
        Text("پیوستن", color = BackgammonPalette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(if (enabled) BackgammonPalette.accent else BackgammonPalette.line)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) BackgammonPalette.ink else BackgammonPalette.inkSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
        )
    }
}
