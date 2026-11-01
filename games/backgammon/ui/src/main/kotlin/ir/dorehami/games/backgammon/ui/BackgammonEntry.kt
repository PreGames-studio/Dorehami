package ir.dorehami.games.backgammon.ui

import androidx.activity.compose.BackHandler
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

private enum class BackgammonDestination { MENU, OFFLINE, ONLINE, SETUP, PLAYING }

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

    fun updateName(value: String) {
        playerName = value
        prefs.edit().putString("playerName", value).apply()
    }

    fun updateTarget(value: Int) {
        targetPoints = value
        prefs.edit().putInt("targetPoints", value).apply()
    }

    when (destination) {
        BackgammonDestination.MENU -> BackgammonMenuPage(
            onOffline = { destination = BackgammonDestination.OFFLINE },
            onOnline = { destination = BackgammonDestination.ONLINE },
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
            onPlayAgain = { matchKey++ },
            onExit = { destination = BackgammonDestination.SETUP },
        )
    }
}

@Composable
private fun BackgammonTable(
    key: Int,
    mode: BackgammonMode,
    playerName: String,
    targetPoints: Int,
    onPlayAgain: () -> Unit,
    onExit: () -> Unit,
) {
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
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "تخته نرد", subtitle = "میز چوبی، تاس و مهره‌های اصیل", onBack = onBack)
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("یک بازی، دو راه", color = BackgammonPalette.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "بخش آفلاین کامل است: مقابل ربات یا دونفره روی همین گوشی. بخش آنلاین در مرحلهٔ بعد به اتاق سرور وصل می‌شود.",
                color = BackgammonPalette.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        BackgammonMenuAction(
            icon = "⌁",
            title = "آفلاین",
            subtitle = "با ربات یا دونفره روی یک گوشی — بدون اینترنت",
            onClick = onOffline,
        )
        BackgammonMenuAction(
            icon = "☁",
            title = "آنلاین",
            subtitle = "اتاق خصوصی، کد و حریف تصادفی — فعلاً نمایشی",
            onClick = onOnline,
        )
        BackgammonRulesCard()
    }
}

@Composable
private fun BackgammonOfflinePage(onBot: () -> Unit, onPassAndPlay: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    BackgammonPage {
        BackgammonHeader(title = "تخته نرد آفلاین", subtitle = "بدون سرور و بدون اینترنت", onBack = onBack)
        BackgammonMenuAction("♛", "بازی با ربات", "قطعی و منصفانه؛ ربات فقط اطلاعات روی تخته را می‌بیند", onClick = onBot)
        BackgammonMenuAction("♙", "دونفره روی همین گوشی", "نوبتی بازی کن و گوشی را به حریف بده", onClick = onPassAndPlay)
        BackgammonMenuAction(
            icon = "⌁",
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
                color = BackgammonPalette.muted,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }
        BackgammonMenuAction("＋", "ساختن اتاق خصوصی", "نمایشی — کدی ساخته نمی‌شود") {
            message = "ساخت اتاق آنلاین هنوز به سرور وصل نیست؛ این گزینه فقط نمایشی است."
        }
        BackgammonMenuAction("↗", "ورود با کد اتاق", "نمایشی — بدون اتصال به سرور") {
            message = "ورود با کد اتاق در نسخهٔ آنلاین واقعی فعال خواهد شد."
        }
        BackgammonMenuAction("✦", "حریف تصادفی", "نمایشی — صف سرور هنوز وصل نیست") {
            message = "جست‌وجوی حریف تصادفی فعلاً فقط نمایشی است."
        }
        message?.let { text ->
            BackgammonCard(Modifier.fillMaxWidth()) {
                Text(text, color = BackgammonPalette.gold, fontSize = 12.sp, lineHeight = 18.sp)
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
                Text("نام نمایشی", color = BackgammonPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 14) onName(it) },
                    placeholder = { Text("مثلاً آرش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BackgammonPalette.text,
                        unfocusedTextColor = BackgammonPalette.text,
                        focusedContainerColor = BackgammonPalette.night.copy(alpha = 0.35f),
                        unfocusedContainerColor = BackgammonPalette.night.copy(alpha = 0.35f),
                        focusedBorderColor = BackgammonPalette.gold,
                        unfocusedBorderColor = BackgammonPalette.goldDim,
                        focusedLabelColor = BackgammonPalette.gold,
                        unfocusedLabelColor = BackgammonPalette.muted,
                        cursorColor = BackgammonPalette.gold,
                    ),
                )
            }
        }
        BackgammonCard(Modifier.fillMaxWidth()) {
            Text("مسابقه تا چند امتیاز؟", color = BackgammonPalette.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgammonModule.ALLOWED_TARGETS.forEach { target ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (target == targetPoints) BackgammonPalette.gold else BackgammonPalette.walnutLight)
                            .border(
                                1.dp,
                                if (target == targetPoints) BackgammonPalette.gold else BackgammonPalette.goldDim.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable(role = Role.Button) { onTargetPoints(target) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            target.toFa(),
                            color = if (target == targetPoints) BackgammonPalette.night else BackgammonPalette.text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "امتیاز پایان مسابقه؛ هر دست ۱ تا ۳ امتیاز (گمون و بک‌گمون) می‌ارزد.",
                color = BackgammonPalette.muted,
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
            Text("قوانین کامل تخته نرد", color = BackgammonPalette.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(if (expanded) "⌃" else "⌄", color = BackgammonPalette.gold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            rules.forEachIndexed { index, rule ->
                Text(
                    "${(index + 1).toFa()}. $rule",
                    color = BackgammonPalette.text.copy(alpha = 0.92f),
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
