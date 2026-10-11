package ir.dorehami.games.pantohub.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.dorehami.games.pantohub.Difficulty
import ir.dorehami.games.pantohub.GameSettings
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.NewGameRequest
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.GameActionResult
import ir.dorehami.games.pantohub.Team
import ir.dorehami.games.pantohub.WordBankRules
import ir.dorehami.games.pantohub.WordCategories
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private data class TeamDraft(
    val name: String,
    val playersText: String,
    val colorArgb: Int,
)

private val teamColors = listOf(
    0xffd94f52.toInt(),
    0xff3777c8.toInt(),
    0xff229b73.toInt(),
    0xffdd922e.toInt(),
    0xff8b62bb.toInt(),
    0xffd45b9b.toInt(),
    0xff547d83.toInt(),
    0xff737b2e.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameSetupScreen(
    repository: PantohubRepository,
    onBack: () -> Unit,
    onStart: (GameState) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferences = remember { context.getSharedPreferences("pantohub-settings", 0) }
    val teams = remember {
        mutableStateListOf(
            TeamDraft("تیم ۱", "بازیکن ۱", teamColors[0]),
            TeamDraft("تیم ۲", "بازیکن ۱", teamColors[1]),
        )
    }
    var gameName by remember { mutableStateOf("پانتومیم") }
    var roundsPerTeam by remember { mutableStateOf(3) }
    var turnSeconds by remember { mutableStateOf(60) }
    var finalRoundSeconds by remember { mutableStateOf(60) }
    var finalRoundMultiplier by remember { mutableStateOf(2) }
    var easyPoints by remember { mutableStateOf(1) }
    var mediumPoints by remember { mutableStateOf(2) }
    var hardPoints by remember { mutableStateOf(3) }
    var skipPenalty by remember { mutableStateOf(0) }
    var maxSkips by remember { mutableStateOf(3) }
    var allowSkip by remember { mutableStateOf(true) }
    var cardsEnabled by remember { mutableStateOf(false) }
    var finalRoundEnabled by remember { mutableStateOf(true) }
    var individualStatsEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(preferences.getBoolean("sound", true)) }
    var animationsEnabled by remember { mutableStateOf(preferences.getBoolean("animations", true)) }
    var difficulties by remember { mutableStateOf(Difficulty.entries.toSet()) }
    var categories by remember { mutableStateOf(emptySet<String>()) }
    var availableCategories by remember { mutableStateOf(emptyList<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availableCategories = runCatching { withContext(Dispatchers.IO) { repository.listCategories() } }.getOrDefault(emptyList())
    }

    fun editTeam(index: Int, transform: (TeamDraft) -> TeamDraft) {
        if (index in teams.indices) teams[index] = transform(teams[index])
    }

    PantohubPageFrame("آماده‌سازی بازی", onBack) {
        Text("نام بازی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = gameName,
            onValueChange = { gameName = it; errorMessage = null },
            label = { Text("مثلاً دورهمی جمعه") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("تیم‌ها", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = {
                teams += TeamDraft("تیم ${teams.size + 1}", "بازیکن ۱", teamColors[teams.size % teamColors.size])
            }) { Text("+ افزودن تیم") }
        }
        Text("تعداد تیم‌ها سقف ثابتی ندارد؛ بازیکنان را با ویرگول یا رفتن به خط بعد جدا کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("نام‌های یکسان در تیم‌های مختلف مجازند؛ هر حضور در بازی جداگانه شناخته می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        teams.forEachIndexed { index, draft ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color(draft.colorArgb), CircleShape),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("تیم ${index + 1}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            if (index > 0) {
                                val previous = teams[index - 1]
                                teams[index - 1] = teams[index]
                                teams[index] = previous
                            }
                        }, enabled = index > 0) { Text("↑") }
                        TextButton(onClick = {
                            if (index < teams.lastIndex) {
                                val next = teams[index + 1]
                                teams[index + 1] = teams[index]
                                teams[index] = next
                            }
                        }, enabled = index < teams.lastIndex) { Text("↓") }
                        if (teams.size > 2) {
                            TextButton(onClick = { teams.removeAt(index) }) { Text("حذف") }
                        }
                    }
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> editTeam(index) { it.copy(name = value) }; errorMessage = null },
                        label = { Text("نام تیم") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = draft.playersText,
                        onValueChange = { value -> editTeam(index) { it.copy(playersText = value) }; errorMessage = null },
                        label = { Text("نام بازیکنان (با ویرگول جدا کن)") },
                        minLines = 1,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("رنگ تیم:", style = MaterialTheme.typography.bodySmall)
                        teamColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(25.dp)
                                    .background(Color(color), CircleShape)
                                    .then(if (draft.colorArgb == color) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .padding(2.dp)
                                    .background(Color(color), CircleShape)
                                    .then(Modifier.clickableNoRipple { editTeam(index) { it.copy(colorArgb = color) } }),
                            )
                        }
                    }
                }
            }
        }

        SectionTitle("زمان و تعداد دورها")
        SettingChoiceRow(
            title = "زمان هر نوبت",
            values = listOf(30, 60, 90, 120),
            selected = turnSeconds,
            suffix = "ثانیه",
            onSelect = { turnSeconds = it },
        )
        SettingStepper("نوبت هر تیم", roundsPerTeam, 1, 20, "دور") { roundsPerTeam = it }

        SectionTitle("دشواری و موضوع")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Difficulty.entries.forEach { difficulty ->
                FilterChip(
                    selected = difficulty in difficulties,
                    onClick = {
                        difficulties = if (difficulty in difficulties) difficulties - difficulty else difficulties + difficulty
                        errorMessage = null
                    },
                    label = { Text(difficulty.labelFa) },
                )
            }
        }
        if (availableCategories.isEmpty()) {
            Text("هنوز دستهٔ تأییدشده‌ای در بانک نیست.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = categories.isEmpty(),
                    onClick = { categories = emptySet() },
                    label = { Text("همه") },
                )
            }
            availableCategories.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { category ->
                        FilterChip(
                            selected = category in categories,
                            onClick = {
                                categories = if (category in categories) categories - category else categories + category
                            },
                            label = { Text(WordCategories.labelFa(category)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Text("اگر «همه» انتخاب باشد، همهٔ دسته‌های معتبر و فعال مجازند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("امتیازدهی")
        SettingStepper("آسان", easyPoints, 1, 10, "امتیاز") { easyPoints = it }
        SettingStepper("متوسط", mediumPoints, 1, 10, "امتیاز") { mediumPoints = it }
        SettingStepper("سخت", hardPoints, 1, 10, "امتیاز") { hardPoints = it }
        PreferenceToggleRow("امکان رد کردن واژه", "رد کردن می‌تواند جریمه داشته باشد.", allowSkip) { allowSkip = it }
        if (allowSkip) {
            SettingStepper("جریمهٔ رد کردن", skipPenalty, 0, 5, "امتیاز") { skipPenalty = it }
            SettingStepper("سقف رد در هر نوبت", maxSkips, 0, 20, "بار") { maxSkips = it }
        }

        SectionTitle("قوانین اختیاری")
        PreferenceToggleRow("کارت‌های ویژه", "هر تیم در هر بازی از هر کارت فقط یک‌بار می‌تواند استفاده کند.", cardsEnabled) { cardsEnabled = it }
        PreferenceToggleRow("دور پایانی برای تیم‌های مساوی", "پس از دورهای اصلی، تیم‌های هم‌امتیاز یک نوبت اضافه با ضریب ویژه می‌گیرند.", finalRoundEnabled) { finalRoundEnabled = it }
        if (finalRoundEnabled) {
            SettingChoiceRow("زمان دور پایانی", listOf(30, 60, 90, 120), finalRoundSeconds, "ثانیه") { finalRoundSeconds = it }
            SettingStepper("ضریب امتیاز دور پایانی", finalRoundMultiplier, 1, 10, "برابر") { finalRoundMultiplier = it }
        }
        PreferenceToggleRow("ثبت آمار فردی", "نام اجراکننده و رویدادهای هر نوبت در تاریخچه ذخیره می‌شود.", individualStatsEnabled) { individualStatsEnabled = it }
        PreferenceToggleRow("صدای پاسخ درست", "قابل خاموش‌کردن از تنظیمات.", soundEnabled) { soundEnabled = it }
        PreferenceToggleRow("انیمیشن رابط", "حرکت‌ها سبک هستند و می‌توانی خاموششان کنی.", animationsEnabled) { animationsEnabled = it }

        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Button(
            onClick = {
                if (creating) return@Button
                if (teams.size < 2) {
                    errorMessage = "برای شروع دست‌کم دو تیم بساز."
                    return@Button
                }
                if (difficulties.isEmpty()) {
                    errorMessage = "حداقل یک سطح دشواری را انتخاب کن."
                    return@Button
                }
                val drafts = teams.toList()
                creating = true
                errorMessage = null
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val playerNamesByTeam = drafts.map { teamDraft ->
                                teamDraft.playersText
                                    .split(',', '،', '\n')
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .distinctBy(WordBankRules::normalizePersian)
                            }
                            // Name-keyed profiles may repeat across teams; the engine requires roster-seat IDs to be unique.
                            val playerAppearances = mutableMapOf<String, Int>()
                            val builtTeams = drafts.mapIndexed { teamIndex, teamDraft ->
                                val players = playerNamesByTeam[teamIndex].map { playerName ->
                                    val profile = repository.resolvePlayer(playerName)
                                    val appearance = (playerAppearances[profile.id] ?: 0) + 1
                                    playerAppearances[profile.id] = appearance
                                    if (appearance == 1) profile else profile.copy(id = "${profile.id}:roster:$appearance")
                                }
                                Team(
                                    id = UUID.randomUUID().toString(),
                                    name = teamDraft.name.trim(),
                                    colorArgb = teamDraft.colorArgb,
                                    players = players,
                                )
                            }
                            val settings = GameSettings(
                                turnSeconds = turnSeconds,
                                roundsPerTeam = roundsPerTeam,
                                allowedDifficulties = difficulties,
                                allowedCategories = categories,
                                easyPoints = easyPoints,
                                mediumPoints = mediumPoints,
                                hardPoints = hardPoints,
                                allowSkip = allowSkip,
                                skipPenalty = skipPenalty,
                                maxSkipsPerTurn = maxSkips,
                                cardsEnabled = cardsEnabled,
                                finalRoundEnabled = finalRoundEnabled,
                                finalRoundSeconds = finalRoundSeconds,
                                finalRoundMultiplier = finalRoundMultiplier,
                                individualStatisticsEnabled = individualStatsEnabled,
                                soundEnabled = soundEnabled,
                                animationsEnabled = animationsEnabled,
                            )
                            PantohubEngine.createGame(
                                NewGameRequest(
                                    id = UUID.randomUUID().toString(),
                                    name = gameName.trim(),
                                    createdAtEpochMs = System.currentTimeMillis(),
                                    teams = builtTeams,
                                    settings = settings,
                                ),
                            )
                        }
                    }
                    result.onSuccess { created ->
                        when (created) {
                            is GameActionResult.Accepted -> {
                                preferences.edit()
                                    .putBoolean("sound", soundEnabled)
                                    .putBoolean("animations", animationsEnabled)
                                    .apply()
                                onStart(created.state)
                            }
                            is GameActionResult.Rejected -> errorMessage = created.messageFa
                        }
                    }.onFailure { errorMessage = it.message ?: "ساخت بازی ناموفق بود." }
                    creating = false
                }
            },
            enabled = !creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (creating) "در حال ساخت بازی…" else "ساخت بازی و شروع")
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingChoiceRow(
    title: String,
    values: List<Int>,
    selected: Int,
    suffix: String,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            values.forEach { value ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text("${value.toString().toPersianDigits()} $suffix") },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettingStepper(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (value > min) onChange(value - 1) }, enabled = value > min) { Text("−") }
        Text("${value.toString().toPersianDigits()} $suffix", fontWeight = FontWeight.Bold)
        TextButton(onClick = { if (value < max) onChange(value + 1) }, enabled = value < max) { Text("+") }
    }
}

@Composable
internal fun PreferenceToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
