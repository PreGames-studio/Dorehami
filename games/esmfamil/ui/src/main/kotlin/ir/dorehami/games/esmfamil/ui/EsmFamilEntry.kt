package ir.dorehami.games.esmfamil.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.esmfamil.EsmFamilModule
import ir.dorehami.games.esmfamil.EsmFamilSheet
import ir.dorehami.uikit.toFa
import kotlin.random.Random

/** Where the player is inside the اسم فامیل module. */
private enum class EsmFamilDestination { MENU, OFFLINE, PLAYING, BLUETOOTH, ONLINE, SETTINGS }

/**
 * Entry point of the module, in the same three-section shape the rest of the app uses:
 * **آفلاین** (the complete game, one phone passed around), **بلوتوث** (each player on their own
 * phone, no internet) and **آنلاین** (a clearly labelled demo until the server rooms are wired).
 */
@Composable
fun EsmFamilEntry(onExit: () -> Unit) {
    EsmFamilTheme { EsmFamilEntryContent(onExit) }
}

@Composable
private fun EsmFamilEntryContent(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ESFAMIL_PREFS, 0) }

    var destination by remember { mutableStateOf(EsmFamilDestination.MENU) }
    var matchKey by rememberSaveable { mutableStateOf(0) }
    var playerName by rememberSaveable { mutableStateOf(prefs.getString("playerName", "").orEmpty()) }
    var players by rememberSaveable { mutableStateOf(prefs.getInt("players", 3).coerceIn(2, 6)) }
    var rounds by rememberSaveable { mutableStateOf(prefs.getInt("rounds", EsmFamilModule.DEFAULT_ROUNDS)) }
    var letters by rememberSaveable {
        mutableStateOf(prefs.getString("letters", EsmFamilSheet.LETTERS_COMMON) ?: EsmFamilSheet.LETTERS_COMMON)
    }
    var categories by rememberSaveable {
        mutableStateOf(prefs.getString("categories", EsmFamilSheet.DEFAULT_IDS.joinToString(",")) ?: "")
    }
    val chosenCategories = remember(categories) {
        categories.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            .ifEmpty { EsmFamilSheet.DEFAULT_IDS }
    }

    fun saveCategories(ids: List<String>) {
        categories = ids.joinToString(",")
        prefs.edit().putString("categories", categories).apply()
    }

    when (destination) {
        EsmFamilDestination.MENU -> EsmFamilMenuPage(
            onOffline = { destination = EsmFamilDestination.OFFLINE },
            onBluetooth = { destination = EsmFamilDestination.BLUETOOTH },
            onOnline = { destination = EsmFamilDestination.ONLINE },
            onSettings = { destination = EsmFamilDestination.SETTINGS },
            onExit = onExit,
        )

        EsmFamilDestination.OFFLINE -> EsmFamilTableSetupPage(
            playerCount = players,
            playerName = playerName,
            rounds = rounds,
            onPlayerCount = {
                players = it
                prefs.edit().putInt("players", it).apply()
            },
            onName = {
                playerName = it
                prefs.edit().putString("playerName", it).apply()
            },
            onRounds = {
                rounds = it
                prefs.edit().putInt("rounds", it).apply()
            },
            onStart = {
                matchKey++
                destination = EsmFamilDestination.PLAYING
            },
            onBack = { destination = EsmFamilDestination.MENU },
        )

        EsmFamilDestination.PLAYING -> EsmFamilLocalTable(
            key = matchKey,
            playerCount = players,
            playerName = playerName,
            rounds = rounds,
            categories = chosenCategories,
            letters = letters,
            onPlayAgain = { matchKey++ },
            onExit = { destination = EsmFamilDestination.OFFLINE },
        )

        EsmFamilDestination.BLUETOOTH -> EsmFamilBluetoothRoom(
            playerName = playerName.ifBlank { "بازیکن" },
            rounds = rounds,
            categories = chosenCategories,
            letters = letters,
            onExit = { destination = EsmFamilDestination.MENU },
        )

        EsmFamilDestination.ONLINE -> EsmFamilOnlinePage(onBack = { destination = EsmFamilDestination.MENU })

        EsmFamilDestination.SETTINGS -> EsmFamilSettingsPage(
            name = playerName,
            onName = {
                playerName = it
                prefs.edit().putString("playerName", it).apply()
            },
            rounds = rounds,
            onRounds = {
                rounds = it
                prefs.edit().putInt("rounds", it).apply()
            },
            letters = letters,
            onLetters = {
                letters = it
                prefs.edit().putString("letters", it).apply()
            },
            categories = chosenCategories,
            onCategories = ::saveCategories,
            onBack = { destination = EsmFamilDestination.MENU },
        )
    }
}

/** Shared preferences file of the module. */
internal const val ESFAMIL_PREFS = "esmfamil-settings"

@Composable
private fun EsmFamilMenuPage(
    onOffline: () -> Unit,
    onBluetooth: () -> Unit,
    onOnline: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
) {
    BackHandler(onBack = onExit)
    EsmFamilPage {
        EsmFamilHeader(title = "اسم فامیل", subtitle = "یک حرف، یک برگه، یک جدول امتیاز", onBack = onExit)
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text("سه راه بازی", color = EsmFamilPalette.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "روی یک گوشی کامل بازی می‌شود؛ با بلوتوث هر کس برگهٔ خودش را روی گوشی خودش پر می‌کند. " +
                    "بخش آنلاین مثل بقیهٔ بازی‌ها فعلاً نمایشی است تا اتاق سرور وصل شود.",
                color = EsmFamilPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        EsmFamilMenuAction(
            icon = EsmFamilIcon.Offline,
            title = "آفلاین",
            subtitle = "۲ تا ۶ نفر روی همین گوشی — بدون اینترنت",
            onClick = onOffline,
        )
        EsmFamilMenuAction(
            icon = EsmFamilIcon.Bluetooth,
            title = "بلوتوث / نزدیک",
            subtitle = "هر کس روی گوشی خودش؛ بدون اینترنت",
            onClick = onBluetooth,
        )
        EsmFamilMenuAction(
            icon = EsmFamilIcon.Online,
            title = "آنلاین",
            subtitle = "اتاق خصوصی و کد — فعلاً نمایشی",
            onClick = onOnline,
        )
        EsmFamilButton(
            text = "تنظیمات",
            primary = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSettings,
        )
        EsmFamilRulesCard()
    }
}

@Composable
private fun EsmFamilTableSetupPage(
    playerCount: Int,
    playerName: String,
    rounds: Int,
    onPlayerCount: (Int) -> Unit,
    onName: (String) -> Unit,
    onRounds: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    EsmFamilPage {
        EsmFamilHeader(title = "میز آفلاین", subtitle = "چند نفر و چند دور؟", onBack = onBack)
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text("تعداد بازیکن", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { count ->
                    ChoiceChip(
                        label = count.toFa(),
                        selected = count == playerCount,
                        modifier = Modifier.weight(1f),
                        onClick = { onPlayerCount(count) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("نام تو (صندلی اول)", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            EsmFamilNameField(value = playerName, onValueChange = onName)
        }
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text("تعداد دور", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EsmFamilModule.ALLOWED_ROUNDS.forEach { option ->
                    ChoiceChip(
                        label = option.toFa(),
                        selected = option == rounds,
                        modifier = Modifier.weight(1f),
                        onClick = { onRounds(option) },
                    )
                }
            }
        }
        EsmFamilButton(
            text = "شروع بازی",
            primary = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStart,
        )
        EsmFamilNote("گوشی دست‌به‌دست می‌شود؛ هر نفر برگهٔ خودش را پر می‌کند و بعد گوشی را می‌دهد.")
    }
}

@Composable
private fun EsmFamilSettingsPage(
    name: String,
    onName: (String) -> Unit,
    rounds: Int,
    onRounds: (Int) -> Unit,
    letters: String,
    onLetters: (String) -> Unit,
    categories: List<String>,
    onCategories: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    EsmFamilPage {
        EsmFamilHeader(title = "تنظیمات اسم فامیل", subtitle = "نام، دورها، حروف و ستون‌ها", onBack = onBack)
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text("نام نمایشی", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            EsmFamilNameField(value = name, onValueChange = onName)
        }
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text("تعداد دور", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EsmFamilModule.ALLOWED_ROUNDS.forEach { option ->
                    ChoiceChip(
                        label = option.toFa(),
                        selected = option == rounds,
                        modifier = Modifier.weight(1f),
                        onClick = { onRounds(option) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("حروف", color = EsmFamilPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = "حروف آسان",
                    selected = letters == EsmFamilSheet.LETTERS_COMMON,
                    modifier = Modifier.weight(1f),
                    onClick = { onLetters(EsmFamilSheet.LETTERS_COMMON) },
                )
                ChoiceChip(
                    label = "همهٔ حروف",
                    selected = letters == EsmFamilSheet.LETTERS_ALL,
                    modifier = Modifier.weight(1f),
                    onClick = { onLetters(EsmFamilSheet.LETTERS_ALL) },
                )
            }
        }
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Text(
                "ستون‌ها (دست‌کم ${EsmFamilModule.MIN_CATEGORIES.toFa()})",
                color = EsmFamilPalette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            EsmFamilSheet.ALL.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { category ->
                        val selected = category.id in categories
                        ChoiceChip(
                            label = category.titleFa,
                            selected = selected,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val next = if (selected) {
                                    categories - category.id
                                } else {
                                    EsmFamilSheet.ALL.map { it.id }.filter { it in categories || it == category.id }
                                }
                                if (next.size >= EsmFamilModule.MIN_CATEGORIES) onCategories(next)
                            },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun EsmFamilOnlinePage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    EsmFamilPage {
        EsmFamilHeader(title = "اسم فامیل آنلاین", subtitle = "اتاق خصوصی با کد", onBack = onBack)
        EsmFamilCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EsmFamilIconView(EsmFamilIcon.Online, color = EsmFamilPalette.violet, size = 20.dp)
                Text("این بخش هنوز به سرور وصل نشده", color = EsmFamilPalette.ink, fontSize = 14.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "قواعد و موتور همین بازی آمادهٔ آنلاین است: همان نشست و همان رکورد قطعی که آفلاین اجرا می‌شود. " +
                    "به‌محض برقراری اتصال سرور (مرحلهٔ M6 نقشهٔ راه)، همین صفحه به اتاق با کد تبدیل می‌شود. " +
                    "تا آن روز از «آفلاین» یا «بلوتوث» استفاده کن — هر دو کامل کار می‌کنند.",
                color = EsmFamilPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
        }
        EsmFamilButton(
            text = "بازگشت",
            primary = false,
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack,
        )
    }
}

@Composable
private fun EsmFamilRulesCard() {
    EsmFamilCard(Modifier.fillMaxWidth()) {
        Text("قاعده در یک نگاه", color = EsmFamilPalette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "یک حرف قرعه‌کشی می‌شود. هر کس در هر ستون (اسم، فامیل، شهر، …) یک جواب می‌نویسد که با همان حرف شروع شود. " +
                "جوابی که فقط خودت نوشتی ${EsmFamilSheet.POINTS_UNIQUE.toFa()} امتیاز دارد، جواب تکراری ${EsmFamilSheet.POINTS_SHARED.toFa()}، " +
                "و خالی یا با حرف اشتباه صفر. آخرین دور، برندهٔ میز مشخص می‌شود.",
            color = EsmFamilPalette.inkSoft,
            fontSize = 12.sp,
            lineHeight = 19.sp,
        )
    }
}

@Composable
private fun EsmFamilNameField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 14) onValueChange(it) },
        placeholder = { Text("مثلاً سارا") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = EsmFamilPalette.ink,
            unfocusedTextColor = EsmFamilPalette.ink,
            focusedContainerColor = EsmFamilPalette.cardAlt.copy(alpha = 0.7f),
            unfocusedContainerColor = EsmFamilPalette.cardAlt.copy(alpha = 0.55f),
            focusedBorderColor = EsmFamilPalette.accent,
            unfocusedBorderColor = EsmFamilPalette.line,
            cursorColor = EsmFamilPalette.accent,
        ),
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) EsmFamilPalette.accent else EsmFamilPalette.cardAlt)
            .border(
                1.dp,
                if (selected) EsmFamilPalette.accent else EsmFamilPalette.line.copy(alpha = 0.5f),
                RoundedCornerShape(14.dp),
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) androidx.compose.ui.graphics.Color.White else EsmFamilPalette.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** The pass-and-play table: owns one [EsmFamilLocalMatch] for the life of the screen. */
@Composable
private fun EsmFamilLocalTable(
    key: Int,
    playerCount: Int,
    playerName: String,
    rounds: Int,
    categories: List<String>,
    letters: String,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    val names = remember(key, playerCount) {
        listOf(playerName.ifBlank { "شما" }) + (2..playerCount).map { "بازیکن ${it.toFa()}" }
    }
    val match = remember(key, playerCount) {
        EsmFamilLocalMatch(
            names = names.take(playerCount),
            rounds = rounds,
            categories = categories,
            letters = letters,
            // The UI picks the seed and the record keeps it; the rules never read the clock.
            seed = Random.nextLong(),
        )
    }
    val state by match.state
    EsmFamilScreen(
        state = state,
        onType = match::type,
        onSubmit = { match.submit() },
        onCloseRound = match::closeRound,
        onNext = match::next,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
    )
}
