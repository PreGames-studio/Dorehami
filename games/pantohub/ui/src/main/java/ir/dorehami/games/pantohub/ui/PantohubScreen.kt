package ir.dorehami.games.pantohub.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import ir.dorehami.games.pantohub.GameMoment
import ir.dorehami.games.pantohub.GameSettings
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.PlayMode
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import ir.dorehami.games.pantohub.ui.data.WordBankSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PantoPage { MODE, HOME, PLAY_MODE, SETUP, GAME, HISTORY, SETTINGS, SOLO }

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
    var selectedPlayMode by remember { mutableStateOf(PlayMode.SPEEDY) }
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
            onNewGame = { page = PantoPage.PLAY_MODE },
            onContinue = { page = PantoPage.GAME },
            onSettings = { openPage(PantoPage.SETTINGS) },
        )

        PantoPage.PLAY_MODE -> PantohubPlayModeScreen(
            onBack = { page = PantoPage.HOME },
            onSelect = { mode ->
                selectedPlayMode = mode
                page = PantoPage.SETUP
            },
        )

        PantoPage.SETUP -> GameSetupScreen(
            repository = repository,
            playMode = selectedPlayMode,
            onBack = { page = PantoPage.PLAY_MODE },
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
                Text("بازی گروهیِ حدس و نمایش", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
private fun PantohubPlayModeScreen(onBack: () -> Unit, onSelect: (PlayMode) -> Unit) {
    PantohubPageFrame(title = "نوع بازی گروهی", onBack = onBack, imageRes = R.drawable.pantohub_bg_stage) {
        Text(
            "دوست داری بازی چطور پیش بره؟",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        PlayModeCard(
            title = "سرعتی",
            badge = "هیجانی و پرشتاب",
            description = "تا وقتی زمان نوبت تموم نشده، پشت سر هم واژه حدس بزنید و امتیاز جمع کنید؛ با تموم شدن زمان، نوبت تیم بعد می‌رسه.",
            buttonLabel = "انتخاب بازی سرعتی",
            accent = PantohubDesign.Violet,
            buttonStyle = PantohubButtonStyle.Primary,
            onClick = { onSelect(PlayMode.SPEEDY) },
        )
        PlayModeCard(
            title = "عادی",
            badge = "آروم و نوبتی",
            description = "اول سختی و موضوع رو انتخاب می‌کنی؛ با یک حدس درست (یا تموم شدن زمان) نوبت تموم می‌شه و می‌ره سراغ تیم بعد.",
            buttonLabel = "انتخاب بازی عادی",
            accent = PantohubDesign.Teal,
            buttonStyle = PantohubButtonStyle.Secondary,
            onClick = { onSelect(PlayMode.NORMAL) },
        )
    }
}

@Composable
private fun PlayModeCard(
    title: String,
    badge: String,
    description: String,
    buttonLabel: String,
    accent: androidx.compose.ui.graphics.Color,
    buttonStyle: PantohubButtonStyle,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(PantohubDesign.Radius.CardLarge)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, ambientColor = accent.copy(alpha = 0.28f), spotColor = accent.copy(alpha = 0.34f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(PantohubDesign.SurfaceRaised, PantohubDesign.Surface)))
            .border(1.dp, accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.15f)))),
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = PantohubDesign.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, color = PantohubDesign.TextSecondary)
            Spacer(Modifier.height(18.dp))
            PantohubButton(
                text = buttonLabel,
                style = buttonStyle,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PantohubModeScreen(onBack: () -> Unit, onGroup: () -> Unit, onSolo: () -> Unit) {
    PantohubPageFrame(title = "پانتومیم", onBack = onBack, imageRes = R.drawable.pantohub_bg_stage) {
        // Brand hero: one clean mark and wordmark, no extra question card above the modes.
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(74.dp)
                    .shadow(16.dp, CircleShape, ambientColor = PantohubDesign.Violet.copy(alpha = 0.55f), spotColor = PantohubDesign.Violet.copy(alpha = 0.55f))
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PantohubDesign.Violet, PantohubDesign.Teal)))
                    .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("پ", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(Modifier.height(12.dp))
            Text("پانتومیم", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "نمایش بده، حدس بزن، امتیاز بگیر",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ModeChoiceCard(
            glyph = ModeGlyph.GROUP,
            title = "بازی گروهی",
            description = "تیم بساز، واژه را فقط به اجراکننده نشان بده و امتیازها را روی همین گوشی ثبت کن.",
            buttonLabel = "شروع بازی گروهی",
            badge = "حالت اصلی بازی",
            accent = PantohubDesign.Violet,
            buttonStyle = PantohubButtonStyle.Primary,
            onClick = onGroup,
        )
        ModeChoiceCard(
            glyph = ModeGlyph.SOLO,
            title = "بازی تک نفره",
            description = "این حالت به‌زودی طراحی و فعال می‌شود.",
            buttonLabel = "ورود به بازی تک نفره",
            badge = "به‌زودی",
            accent = PantohubDesign.Teal,
            buttonStyle = PantohubButtonStyle.Secondary,
            onClick = onSolo,
        )
    }
}

private enum class ModeGlyph { GROUP, SOLO }

/** Minimal two-person / one-person mark drawn as vectors — no emoji icons. */
@Composable
private fun ModeGlyphIcon(glyph: ModeGlyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val head = Color.White
        when (glyph) {
            ModeGlyph.SOLO -> {
                drawCircle(color = head, radius = size.minDimension * 0.20f, center = Offset(size.width * 0.5f, size.height * 0.34f))
                drawArc(color = head, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(size.width * 0.18f, size.height * 0.52f), size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.55f))
            }
            ModeGlyph.GROUP -> {
                drawCircle(color = head, radius = size.minDimension * 0.16f, center = Offset(size.width * 0.34f, size.height * 0.36f))
                drawCircle(color = color.copy(alpha = 0.75f), radius = size.minDimension * 0.14f, center = Offset(size.width * 0.66f, size.height * 0.38f))
                drawArc(color = head, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(size.width * 0.06f, size.height * 0.54f), size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.5f))
                drawArc(color = color.copy(alpha = 0.75f), startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = Offset(size.width * 0.44f, size.height * 0.56f), size = androidx.compose.ui.geometry.Size(size.width * 0.5f, size.height * 0.46f))
            }
        }
    }
}

/**
 * Mode card on the shared design system: deep navy surface, one accent per mode
 * (violet for the main group mode, teal for solo), a restrained glow and shine.
 * Motion freezes when the user turns animations off in Pantohub settings.
 */
@Composable
private fun ModeChoiceCard(
    glyph: ModeGlyph,
    title: String,
    description: String,
    buttonLabel: String,
    badge: String,
    accent: Color,
    buttonStyle: PantohubButtonStyle,
    onClick: () -> Unit,
) {
    val motion = LocalPantohubMotion.current
    val transition = rememberInfiniteTransition(label = "modeCardGlow")
    val glow by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_800), RepeatMode.Reverse),
        label = "modeCardGlowAlpha",
    )
    val glowAlpha = if (motion) glow else 0.6f
    val shape = RoundedCornerShape(PantohubDesign.Radius.CardLarge)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = accent.copy(alpha = 0.30f * glowAlpha),
                spotColor = accent.copy(alpha = 0.38f * glowAlpha),
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(PantohubDesign.SurfaceRaised, PantohubDesign.Surface),
                ),
            )
            .border(1.dp, accent.copy(alpha = 0.35f + 0.30f * glowAlpha), shape)
            .clickable(onClick = onClick),
    ) {
        // Accent spine on the top edge of the card.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Brush.horizontalGradient(listOf(accent, accent.copy(alpha = 0.15f)))),
        )
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f))))
                        .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    ModeGlyphIcon(glyph = glyph, color = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = PantohubDesign.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(accent.copy(alpha = 0.18f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(badge, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accent)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyLarge,
                color = PantohubDesign.TextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            PantohubButton(
                text = buttonLabel,
                style = buttonStyle,
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
                Text("بازی تک نفره", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
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
