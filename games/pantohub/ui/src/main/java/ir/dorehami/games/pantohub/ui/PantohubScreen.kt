package ir.dorehami.games.pantohub.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ir.dorehami.games.pantohub.GameMoment
import ir.dorehami.games.pantohub.GameSettings
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import ir.dorehami.games.pantohub.ui.data.WordBankSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PantoPage { MODE, HOME, SETUP, GAME, HISTORY, SETTINGS, SOLO }

/** Native, offline-first pantomime area attached to the existing Android game picker. */
@Composable
fun PantohubScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pantohub-settings", 0) }
    var motionEnabled by remember { mutableStateOf(preferences.getBoolean("animations", true)) }
    PantohubTheme(motionEnabled = motionEnabled) {
        PantohubScreenContent(onBack = onBack, onMotionChange = { motionEnabled = it })
    }
}

@Composable
private fun PantohubScreenContent(onBack: () -> Unit, onMotionChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val repository = remember { PantohubRepository(context) }
    DisposableEffect(repository) { onDispose { repository.close() } }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(PantoPage.MODE) }
    var returnPage by remember { mutableStateOf(PantoPage.MODE) }
    var activeGame by remember { mutableStateOf<GameState?>(null) }
    var bankSummary by remember { mutableStateOf<WordBankSummary?>(null) }
    var loading by remember { mutableStateOf(true) }
    var storageError by remember { mutableStateOf<String?>(null) }

    fun persistGame(state: GameState) {
        activeGame = state
        scope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { repository.saveGame(state) } }
            result.onSuccess { storageError = null }
                .onFailure { storageError = "ذخیرهٔ بازی انجام نشد؛ فضای خالی دستگاه را بررسی کن." }
        }
    }

    fun openPage(destination: PantoPage) {
        returnPage = page
        page = destination
    }

    BackHandler(enabled = page != PantoPage.MODE) {
        page = when (page) {
            PantoPage.HISTORY, PantoPage.SETTINGS -> returnPage
            PantoPage.HOME, PantoPage.SOLO -> PantoPage.MODE
            else -> PantoPage.HOME
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val load = runCatching {
            withContext(Dispatchers.IO) {
                repository.initialize()
                val saved = repository.loadActiveGame()
                val recovered = saved?.let {
                    PantohubEngine.recoverAfterProcessRestart(
                        it,
                        GameMoment(SystemClock.elapsedRealtime(), System.currentTimeMillis()),
                    )
                }
                if (saved != null && recovered != null && recovered.version != saved.version) {
                    repository.saveGame(recovered)
                }
                recovered to repository.bankSummary()
            }
        }
        load.onSuccess { (saved, summary) ->
            activeGame = saved
            bankSummary = summary
        }.onFailure {
            storageError = "داده‌های بازی محلی باز نشدند. اگر مشکل ادامه داشت، از پشتیبان‌گیری دستگاه استفاده کن."
        }
        loading = false
    }

    when (page) {
        PantoPage.MODE -> PantohubModeScreen(
            onBack = onBack,
            onGroup = { page = PantoPage.HOME },
            onSolo = { page = PantoPage.SOLO },
        )

        PantoPage.SOLO -> PantohubSoloScreen(onBack = { page = PantoPage.MODE })

        PantoPage.HOME -> PantohubHome(
            onBack = { page = PantoPage.MODE },
            loading = loading,
            activeGame = activeGame,
            storageError = storageError,
            onNewGame = { page = PantoPage.SETUP },
            onContinue = { page = PantoPage.GAME },
            onSettings = { openPage(PantoPage.SETTINGS) },
        )

        PantoPage.SETUP -> GameSetupScreen(
            repository = repository,
            onBack = { page = PantoPage.HOME },
            onStart = { state ->
                scope.launch {
                    val result = runCatching { withContext(Dispatchers.IO) { repository.startNewGame(state) } }
                    result.onSuccess {
                        activeGame = state
                        storageError = null
                        page = PantoPage.GAME
                    }.onFailure {
                        storageError = "بازی جدید ذخیره نشد؛ می‌توانی دوباره تلاش کنی."
                        page = PantoPage.HOME
                    }
                }
            },
        )

        PantoPage.GAME -> {
            val state = activeGame
            if (state == null) {
                SimplePantoPage("بازی فعالی نیست", onBack = { page = PantoPage.HOME }) {
                    PantohubButton(
                        text = "ساخت بازی جدید",
                        emoji = "✨",
                        onClick = { page = PantoPage.SETUP },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                PantohubGameScreen(
                    state = state,
                    repository = repository,
                    storageError = storageError,
                    onStateChange = ::persistGame,
                    onBack = { page = PantoPage.HOME },
                    onReturnHome = { page = PantoPage.HOME },
                )
            }
        }

        PantoPage.HISTORY -> GameHistoryScreen(
            repository = repository,
            onBack = { page = returnPage },
        )

        PantoPage.SETTINGS -> PantohubSettingsScreen(
            onBack = { page = returnPage },
            onMotionChange = onMotionChange,
        )
    }
}

@Composable
private fun PantohubHome(
    onBack: () -> Unit,
    loading: Boolean,
    activeGame: GameState?,
    storageError: String?,
    onNewGame: () -> Unit,
    onContinue: () -> Unit,
    onSettings: () -> Unit,
) {
    PantohubPageFrame(title = "بازی گروهی", onBack = onBack, imageRes = R.drawable.pantohub_bg_party) {
        if (loading) {
            Text("در حال آماده‌سازی بازی و بانک محلی…", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@PantohubPageFrame
        }
        storageError?.let {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = it,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("🎭  بازی گروهیِ حدس و نمایش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "تیم بسازید، واژه را فقط به اجراکننده نشان دهید و امتیازها را روی همین گوشی ثبت کنید.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                if (activeGame != null && activeGame.phase.name != "FINISHED") {
                    PantohubButton(
                        text = "ادامهٔ بازی «${activeGame.name}»",
                        emoji = "▶️",
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "برای حفظ بازی جاری، اول آن را ادامه بده یا با تأیید پایانش بده؛ بعد بازی جدید بساز.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PantohubButton(
                        text = "شروع بازی جدید",
                        emoji = "🎉",
                        onClick = onNewGame,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PantohubButton(
                text = "تنظیمات",
                emoji = "⚙️",
                style = PantohubButtonStyle.Secondary,
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PantohubModeScreen(onBack: () -> Unit, onGroup: () -> Unit, onSolo: () -> Unit) {
    PantohubPageFrame(title = "پانتومیم", onBack = onBack, imageRes = R.drawable.pantohub_bg_stage) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Text(
                text = "بازی پانتومیم را چطور می‌خواهی اجرا کنی؟",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        ModeChoiceCard(
            emoji = "👥",
            title = "بازی گروهی",
            description = "تیم بساز، واژه را فقط به اجراکننده نشان بده و امتیازها را روی همین گوشی ثبت کن.",
            buttonLabel = "ورود به بازی گروهی",
            style = PantohubButtonStyle.Primary,
            onClick = onGroup,
        )
        ModeChoiceCard(
            emoji = "🧑",
            title = "بازی تک نفره",
            description = "این حالت به‌زودی طراحی و فعال می‌شود.",
            buttonLabel = "ورود به بازی تک نفره",
            style = PantohubButtonStyle.Accent,
            onClick = onSolo,
        )
    }
}

@Composable
private fun ModeChoiceCard(
    emoji: String,
    title: String,
    description: String,
    buttonLabel: String,
    style: PantohubButtonStyle,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text("$emoji  $title", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            PantohubButton(
                text = buttonLabel,
                style = style,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PantohubSoloScreen(onBack: () -> Unit) {
    PantohubPageFrame(title = "بازی تک نفره", onBack = onBack, imageRes = R.drawable.pantohub_bg_stage) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("🧑  بازی تک نفره", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "این بخش هنوز طراحی نشده است. بعداً با هم قوانین و صفحه‌های آن را طراحی و اضافه می‌کنیم.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun SimplePantoPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    PantohubPageFrame(title, onBack) { content() }
}

internal fun Int.toPersianDigits(): String = toString().toPersianDigits()

internal fun String.toPersianDigits(): String = map { char ->
    if (char in '0'..'9') '۰' + (char - '0') else char
}.joinToString("")

@Composable
private fun PantohubSettingsScreen(onBack: () -> Unit, onMotionChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("pantohub-settings", 0) }
    var soundEnabled by remember { mutableStateOf(preferences.getBoolean("sound", true)) }
    var animationsEnabled by remember { mutableStateOf(preferences.getBoolean("animations", true)) }

    PantohubPageFrame("تنظیمات پانتومیم", onBack) {
        PreferenceToggleRow(
            title = "صدای رویدادها",
            subtitle = "صدای کوتاه برای پاسخ درست؛ بدون ضبط صدا.",
            checked = soundEnabled,
            onCheckedChange = {
                soundEnabled = it
                preferences.edit().putBoolean("sound", it).apply()
            },
        )
        PreferenceToggleRow(
            title = "حرکت‌های رابط کاربری",
            subtitle = "برای کم‌کردن جلوه‌های حرکتی خاموشش کن.",
            checked = animationsEnabled,
            onCheckedChange = {
                animationsEnabled = it
                preferences.edit().putBoolean("animations", it).apply()
                onMotionChange(it)
            },
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ذخیره‌سازی", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("بازی، تاریخچه و بانک واژه روی همین دستگاه و در SQLite ذخیره می‌شوند و بازی آفلاین است.")
                Spacer(Modifier.height(6.dp))
                Text("همگام‌سازی ابری در این مخزن پیکربندی نشده؛ اطلاعات بین دستگاه‌ها همگام نمی‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "بازی روی گوشی مشترک محرمانگی کامل ندارد. پیش از نمایش واژه، گوشی را به اجراکننده بدهید؛ صفحه را رو به بقیه نگیرید.",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
