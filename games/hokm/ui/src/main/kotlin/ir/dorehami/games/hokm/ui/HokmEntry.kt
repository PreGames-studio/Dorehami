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
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.uikit.rememberSoundPlayer
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Entry point of the Hokm feature: a short setup screen, then the table against three bots.
 * Online and Bluetooth rooms plug in here once `core/session` lands — the table itself is unchanged.
 */
@Composable
fun HokmEntry(onExit: () -> Unit) {
    var started by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf(HokmModule.DEFAULT_TARGET_POINTS) }
    var soundOn by rememberSaveable { mutableStateOf(true) }
    var matchKey by rememberSaveable { mutableStateOf(0) }

    if (!started) {
        HokmSetupScreen(
            name = name,
            onName = { name = it },
            target = target,
            onTarget = { target = it },
            soundOn = soundOn,
            onSound = { soundOn = it },
            onStart = { started = true },
            onBack = onExit,
        )
    } else {
        HokmSoloGame(
            key = matchKey,
            playerName = name,
            targetPoints = target,
            soundOn = soundOn,
            onPlayAgain = { matchKey++ },
            onExit = { started = false },
        )
    }
}

@Composable
private fun HokmSoloGame(
    key: Int,
    playerName: String,
    targetPoints: Int,
    soundOn: Boolean,
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
            targetPoints = targetPoints,
            // Seed picked by the UI (never by the rules): wall clock is fine here, it is recorded.
            seed = System.currentTimeMillis() xor (key.toLong() shl 20),
            onSound = { sounds.play(it) },
        )
    }
    DisposableEffect(key) {
        match.start()
        onDispose { scope.cancel() }
    }

    val state by match.state
    HokmScreen(
        state = state,
        onPlayCard = match::playCard,
        onChooseHokm = match::chooseHokm,
        onClearError = match::clearError,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
    )
}

@Composable
private fun HokmSetupScreen(
    name: String,
    onName: (String) -> Unit,
    target: Int,
    onTarget: (Int) -> Unit,
    soundOn: Boolean,
    onSound: (Boolean) -> Unit,
    onStart: () -> Unit,
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("افکت‌های صوتی", modifier = Modifier.weight(1f))
                Switch(checked = soundOn, onCheckedChange = onSound)
            }
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
        Spacer(Modifier.height(8.dp))
        Text(
            "بازی بلوتوثی و آنلاین با همین میز به‌زودی",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
        )
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
