package ir.dorehami.games.backgammon.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.backgammon.BackgammonModule
import ir.dorehami.uikit.toFa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** The hosting activity (null in a Compose preview / test harness). */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private enum class BackgammonDestination { MENU, OFFLINE, ONLINE, SETUP, PLAYING, SETTINGS, SHOP }

/** Which local table is being set up. */
private enum class LocalKind { VS_BOT, PASS_AND_PLAY }

/**
 * Backgammon entry point with the same two-section shape as chess: **آفلاین** (bot / two players on
 * one phone — the complete offline game) and **آنلاین** (a clearly-labelled demo until the online
 * room flow is wired in a later stage).
 */
@Composable
fun BackgammonEntry(onExit: () -> Unit) {
    BackgammonTheme {
        BackgammonEntryContent(onExit)
    }
}

@Composable
private fun BackgammonEntryContent(onExit: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("backgammon-settings", 0) }

    var destination by remember { mutableStateOf(BackgammonDestination.MENU) }
    var localKind by remember { mutableStateOf(LocalKind.VS_BOT) }
    var matchKey by rememberSaveable { mutableStateOf(0) }
    var playerName by rememberSaveable { mutableStateOf(prefs.getString("playerName", "") ?: "") }
    var targetPoints by rememberSaveable {
        mutableStateOf(prefs.getInt("targetPoints", BackgammonModule.DEFAULT_TARGET_POINTS))
    }
    var boardId by rememberSaveable {
        mutableStateOf(prefs.getString("boardId", BackgammonSkins.DefaultBoard.id) ?: BackgammonSkins.DefaultBoard.id)
    }
    var pieceId by rememberSaveable {
        mutableStateOf(prefs.getString("pieceId", BackgammonSkins.DefaultPieces.id) ?: BackgammonSkins.DefaultPieces.id)
    }
    var wins by rememberSaveable { mutableStateOf(prefs.getInt("wins", 0)) }

    fun updateName(value: String) {
        playerName = value
        prefs.edit().putString("playerName", value).apply()
    }

    fun updateTarget(value: Int) {
        targetPoints = value
        prefs.edit().putInt("targetPoints", value).apply()
    }

    BackgammonLookProvider(boardId = boardId, pieceId = pieceId) {
    when (destination) {
        BackgammonDestination.SETTINGS -> BackgammonSettingsPage(
            name = playerName,
            onName = ::updateName,
            targetPoints = targetPoints,
            onTargetPoints = ::updateTarget,
            boardId = boardId,
            pieceId = pieceId,
            wins = wins,
            onOpenShop = { destination = BackgammonDestination.SHOP },
            onBack = { destination = BackgammonDestination.MENU },
        )

        BackgammonDestination.SHOP -> BackgammonShopPage(
            boardId = boardId,
            pieceId = pieceId,
            wins = wins,
            onPickBoard = { id ->
                boardId = id
                prefs.edit().putString("boardId", id).apply()
            },
            onPickPieces = { id ->
                pieceId = id
                prefs.edit().putString("pieceId", id).apply()
            },
            onBack = { destination = BackgammonDestination.SETTINGS },
        )

        BackgammonDestination.MENU -> BackgammonMenuPage(
            onOffline = { destination = BackgammonDestination.OFFLINE },
            onOnline = { destination = BackgammonDestination.ONLINE },
            onSettings = { destination = BackgammonDestination.SETTINGS },
            onShop = { destination = BackgammonDestination.SHOP },
            onBack = onExit,
        )

        BackgammonDestination.OFFLINE -> BackgammonOfflinePage(
            onBot = {
                localKind = LocalKind.VS_BOT
                destination = BackgammonDestination.SETUP
            },
            onPassAndPlay = {
                localKind = LocalKind.PASS_AND_PLAY
                destination = BackgammonDestination.SETUP
            },
            onBack = { destination = BackgammonDestination.MENU },
        )

        BackgammonDestination.ONLINE -> BackgammonOnlinePage(onBack = { destination = BackgammonDestination.MENU })

        BackgammonDestination.SETUP -> BackgammonSetupPage(
            passAndPlay = localKind == LocalKind.PASS_AND_PLAY,
            name = playerName,
            onName = ::updateName,
            targetPoints = targetPoints,
            onTargetPoints = ::updateTarget,
            onStart = {
                matchKey++
                destination = BackgammonDestination.PLAYING
            },
            onBack = { destination = BackgammonDestination.OFFLINE },
        )

        BackgammonDestination.PLAYING -> BackgammonTable(
            key = matchKey,
            mode = if (localKind == LocalKind.PASS_AND_PLAY) BackgammonMode.PASS_AND_PLAY else BackgammonMode.VS_BOT,
            playerName = playerName.ifBlank { "شما" },
            targetPoints = targetPoints,
            onGameWon = {
                wins += 1
                prefs.edit().putInt("wins", wins).apply()
            },
            onPlayAgain = { matchKey++ },
            onExit = { destination = BackgammonDestination.SETUP },
        )
    }
    }
}

@Composable
private fun BackgammonTable(
    key: Int,
    mode: BackgammonMode,
    playerName: String,
    targetPoints: Int,
    onGameWon: () -> Unit,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
    // The table is a landscape board: lock the orientation while it is open and hand control back
    // to the system when the player leaves, so the rest of the app still rotates freely.
    val context = LocalContext.current
    DisposableEffect(key) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (previous != null) activity?.requestedOrientation = previous
        }
    }
    val tableScope = remember(key) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val match = remember(key) {
        BackgammonLocalMatch(
            scope = tableScope,
            mode = mode,
            humanName = playerName,
            botName = "ربات دژبان",
            targetPoints = targetPoints,
            // The seed is picked by the UI and recorded; the rules never read the clock.
            seed = System.currentTimeMillis() xor (key.toLong() shl 17),
        )
    }
    DisposableEffect(key) {
        match.start()
        onDispose {
            match.stop()
            tableScope.cancel()
        }
    }
    val state by match.state
    // The shop unlocks on real wins: count a finished match once.
    var reportedWin by remember(key) { mutableStateOf(false) }
    LaunchedEffect(state.finished) {
        if (state.finished && !reportedWin && state.outcomeTitle == "سفید برد") {
            reportedWin = true
            onGameWon()
        }
    }
    BackgammonScreen(
        state = state,
        modeLabel = if (mode == BackgammonMode.PASS_AND_PLAY) "آفلاین · دونفره روی همین گوشی" else "آفلاین · مقابل ربات",
        onPoint = match::tapPoint,
        onDie = match::tapDie,
        onRoll = match::roll,
        onUndo = match::undo,
        onSubmit = match::submitTurn,
        onPlayAgain = onPlayAgain,
        onExit = onExit,
    )
}

@Composable
private fun BackgammonMenuPage(
    onOffline: () -> Unit,
    onOnline: () -> Unit,
    onSettings: () -> Unit,
    onShop: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "تخته نرد", subtitle = "میز چوبی، تاس و مهره‌های اصیل", onBack = onBack)
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("یک بازی، دو راه", color = BackgammonPalette.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "بخش آفلاین کامل است: مقابل ربات یا دونفره روی همین گوشی. بخش آنلاین در مرحلهٔ بعد به اتاق سرور وصل می‌شود.",
                color = BackgammonPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        BackgammonMenuAction(
            icon = BackgammonIcon.Offline,
            title = "آفلاین",
            subtitle = "با ربات یا دونفره روی یک گوشی — بدون اینترنت",
            onClick = onOffline,
        )
        BackgammonMenuAction(
            icon = BackgammonIcon.Online,
            title = "آنلاین",
            subtitle = "اتاق خصوصی، کد و حریف تصادفی — فعلاً نمایشی",
            onClick = onOnline,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TableButton(
                text = "تنظیمات",
                primary = false,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onSettings,
            )
            TableButton(
                text = "فروشگاه",
                primary = false,
                enabled = true,
                modifier = Modifier.weight(1f),
                onClick = onShop,
            )
        }
        BackgammonRulesCard()
    }
}

@Composable
private fun BackgammonOfflinePage(onBot: () -> Unit, onPassAndPlay: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "تخته نرد آفلاین", subtitle = "بدون سرور و بدون اینترنت", onBack = onBack)
        BackgammonMenuAction(BackgammonIcon.Bot, "بازی با ربات", "قطعی و منصفانه؛ ربات فقط اطلاعات روی تخته را می‌بیند", onClick = onBot)
        BackgammonMenuAction(BackgammonIcon.TwoPlayers, "دونفره روی همین گوشی", "نوبتی بازی کن و گوشی را به حریف بده", onClick = onPassAndPlay)
        BackgammonMenuAction(
            icon = BackgammonIcon.Bluetooth,
            title = "بلوتوث (به‌زودی)",
            subtitle = "اتاق نزدیک در همین مرحله فعال نیست",
            enabled = false,
            onClick = {},
        )
        BackgammonRulesCard()
    }
}

@Composable
private fun BackgammonOnlinePage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    BackgammonPage {
        BackgammonHeader(title = "تخته نرد آنلاین", subtitle = "این بخش فعلاً نمایشی است", onBack = onBack)
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text(
                "قواعد، میز و ربات همین حالا کامل کار می‌کنند؛ اتصال اتاق آنلاین در مرحلهٔ بعد به همان SessionController سرور وصل می‌شود.",
                color = BackgammonPalette.inkSoft,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        BackgammonMenuAction(BackgammonIcon.Plus, "ساختن اتاق خصوصی", "نمایشی — کدی ساخته نمی‌شود") {
            message = "ساخت اتاق آنلاین هنوز به سرور وصل نیست؛ این گزینه فقط نمایشی است."
        }
        BackgammonMenuAction(BackgammonIcon.Enter, "ورود با کد اتاق", "نمایشی — بدون اتصال به سرور") {
            message = "ورود با کد اتاق در نسخهٔ آنلاین واقعی فعال خواهد شد."
        }
        BackgammonMenuAction(BackgammonIcon.Sparkle, "حریف تصادفی", "نمایشی — صف سرور هنوز وصل نیست") {
            message = "جست‌وجوی حریف تصادفی فعلاً فقط نمایشی است."
        }
        message?.let { text ->
            BackgammonCard(Modifier.fillMaxWidth()) {
                Text(text, color = BackgammonPalette.accent, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun BackgammonSetupPage(
    passAndPlay: Boolean,
    name: String,
    onName: (String) -> Unit,
    targetPoints: Int,
    onTargetPoints: (Int) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(
            title = if (passAndPlay) "دونفره روی یک گوشی" else "بازی با ربات",
            subtitle = "تنظیمات کوتاه میز",
            onBack = onBack,
        )
        if (!passAndPlay) {
            BackgammonCard(Modifier.fillMaxWidth()) {
                Text("نام نمایشی", color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 14) onName(it) },
                    placeholder = { Text("مثلاً آرش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BackgammonPalette.ink,
                        unfocusedTextColor = BackgammonPalette.ink,
                        focusedContainerColor = BackgammonPalette.cardAlt.copy(alpha = 0.7f),
                        unfocusedContainerColor = BackgammonPalette.cardAlt.copy(alpha = 0.55f),
                        focusedBorderColor = BackgammonPalette.accent,
                        unfocusedBorderColor = BackgammonPalette.line,
                        focusedLabelColor = BackgammonPalette.accent,
                        unfocusedLabelColor = BackgammonPalette.inkSoft,
                        cursorColor = BackgammonPalette.accent,
                    ),
                )
            }
        }
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("مسابقه تا چند امتیاز؟", color = BackgammonPalette.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgammonModule.ALLOWED_TARGETS.forEach { target ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (target == targetPoints) BackgammonPalette.accent else BackgammonPalette.cardAlt)
                            .border(
                                1.dp,
                                if (target == targetPoints) BackgammonPalette.accent else BackgammonPalette.line.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(role = Role.Button) { onTargetPoints(target) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            target.toFa(),
                            color = if (target == targetPoints) Color.White else BackgammonPalette.ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "امتیاز پایان مسابقه؛ هر دست ۱ تا ۳ امتیاز (گمون و بک‌گمون) می‌ارزد.",
                color = BackgammonPalette.inkSoft,
                fontSize = 11.sp,
            )
        }
        TableButton(
            text = "شروع بازی",
            primary = true,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStart,
        )
        BackgammonRulesCard()
    }
}

@Composable
private fun BackgammonRulesCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackgammonCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("قوانین کامل تخته نرد", color = BackgammonPalette.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            BackgammonIconView(
                if (expanded) BackgammonIcon.ChevronUp else BackgammonIcon.ChevronDown,
                color = BackgammonPalette.accent,
                size = 18.dp,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            rules.forEachIndexed { index, rule ->
                Text(
                    "${(index + 1).toFa()}. $rule",
                    color = BackgammonPalette.ink.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

private val rules = listOf(
    "هر بازیکن ۱۵ مهره دارد و با دو تاس بازی می‌کند؛ تاس از تزریق تصادف قطعیِ سرور/میزان می‌آید، نه از ساعت گوشی.",
    "سفید از نقطهٔ ۲۴ به سمت نقطهٔ ۱ و سیاه در جهت مخالف حرکت می‌کند؛ هدف رساندن همهٔ مهره‌ها به خانهٔ خود و بیرون‌بردن آن‌هاست.",
    "اگر مهرهٔ حریف تنها (بلات) در خانه باشد، با ورود مهره به آن خانه زده می‌شود و به «بار» می‌رود.",
    "مهرهٔ روی بار باید پیش از هر حرکت دیگری با یکی از تاس‌ها وارد خانهٔ حریف شود؛ نقطه‌ای که دو مهرهٔ حریف دارد بسته است.",
    "باید بیشترین استفاده از تاس‌ها بشود؛ اگر فقط یکی از دو تاس قابل بازی است، تاس بزرگ‌تر اجباری است. دوبل چهار حرکت می‌دهد.",
    "بیرون‌بردن فقط وقتی مجاز است که همهٔ ۱۵ مهره در خانهٔ خودی باشند؛ تاس باید دقیقاً با فاصله بخواند یا بزرگ‌تر باشد بی‌آنکه مهره‌ای عقب‌تر مانده باشد.",
    "برندهٔ دست ۱ امتیاز می‌گیرد؛ اگر حریف هیچ مهره‌ای بیرون نبرده باشد گمون ۲ و اگر مهره‌اش در خانهٔ برنده یا روی بار مانده باشد بک‌گمون ۳ امتیاز دارد.",
    "مسابقه تا رسیدن یکی از بازیکن‌ها به امتیاز هدف ادامه دارد؛ برندهٔ هر دست، دست بعد را شروع می‌کند.",
    "ربات فقط تختهٔ عمومی، تاس‌ها و همین قوانین را می‌بیند؛ هیچ مهره یا اطلاعات پنهانی در کار نیست و حرکتش در لاگ بازی ثبت می‌شود.",
)
