package ir.dorehami.games.chess.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.dorehami.uikit.toFa

@Composable
internal fun ChessMainMenu(
    onOffline: () -> Unit,
    onOnline: () -> Unit,
    onSettings: () -> Unit,
    onStore: () -> Unit,
    coinBalance: Long,
    onBack: () -> Unit,
) {
    ChessMenuPage(title = "شطرنج", subtitle = "یک میز، هر طور که دوست داری", onBack = onBack) {
        MenuAction("☁", "آنلاین", "اتاق خصوصی، حریف تصادفی و بازی با دوستان — فعلاً نمایشی", onOnline)
        MenuAction("⌁", "آفلاین", "تک‌نفره، دونفره روی یک گوشی، بلوتوث و هات‌اسپات", onOffline)
        MenuAction("⚙", "تنظیمات", "طرح تخته و مهره، صدا، انیمیشن و چرخش خودکار", onSettings)
        MenuAction("🪙", "فروشگاه شطرنج", "طرح تخته و مهره • ${coinBalance.toFa()} سکه در کیف‌پول", onStore)
        Text(
            text = "برای بازی با دوست در حالت آفلاین، اینترنت لازم نیست.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(12),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ChessOfflineMenu(
    onSolo: () -> Unit,
    onPassAndPlay: () -> Unit,
    onBluetooth: () -> Unit,
    onHotspot: () -> Unit,
    onBack: () -> Unit,
) {
    ChessMenuPage(title = "شطرنج آفلاین", subtitle = "بدون سرور و بدون اینترنت", onBack = onBack) {
        MenuAction("♟", "بازی تکی", "مقابل ربات در چهار سطح", onSolo)
        MenuAction("♙", "دونفره روی همین گوشی", "نوبتی بازی کن و گوشی را به حریف بده", onPassAndPlay)
        MenuAction("⌁", "دونفره با بلوتوث", "ساختن یا پیدا کردن اتاق نزدیک", onBluetooth)
        MenuAction("▦", "دونفره با هات‌اسپات", "اتصال مستقیم دو گوشی روی یک شبکهٔ محلی", onHotspot)
    }
}

@Composable
internal fun ChessOnlineDemo(onBack: () -> Unit) {
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    ChessMenuPage(title = "شطرنج آنلاین", subtitle = "این بخش فعلاً نمایشی است", onBack = onBack) {
        SettingsCard {
            Text(
                "برای حفظ تجربهٔ آفلاین، این نسخه هیچ اتصال سروری انجام نمی‌دهد. ساخت اتاق، ورود با کد و حریف تصادفی پس از اتصال سرویس آنلاین فعال می‌شوند.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = chessSp(13),
                lineHeight = chessSp(20),
            )
        }
        MenuAction("＋", "ساختن اتاق خصوصی", "نمایشی — کدی ساخته نمی‌شود") {
            message = "ساخت اتاق آنلاین هنوز به سرور وصل نیست؛ این گزینه فقط نمایشی است."
        }
        MenuAction("↗", "ورود با کد اتاق", "نمایشی — بدون اتصال به سرور") {
            message = "ورود با کد اتاق در نسخهٔ آنلاینِ واقعی فعال خواهد شد."
        }
        MenuAction("✦", "حریف تصادفی", "نمایشی — صف سرور هنوز وصل نیست") {
            message = "جست‌وجوی حریف تصادفی فعلاً فقط نمایشی است."
        }
        message?.let {
            SettingsCard { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = chessSp(13)) }
        }
    }
}

@Composable
internal fun ChessSettingsScreen(
    boardStyle: ChessBoardStyle,
    onBoardStyle: (ChessBoardStyle) -> Unit,
    pieceStyle: ChessPieceStyle,
    onPieceStyle: (ChessPieceStyle) -> Unit,
    ownedStoreItems: Set<String>,
    animationsEnabled: Boolean,
    onAnimationsEnabled: (Boolean) -> Unit,
    soundOn: Boolean,
    onSoundOn: (Boolean) -> Unit,
    autoRotate: Boolean,
    onAutoRotate: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    ChessMenuPage(title = "تنظیمات شطرنج", subtitle = "ظاهر و تجربهٔ بازی را همین‌جا عوض کن", onBack = onBack) {
        SettingsCard {
            ChessBoardStylePicker(
                selected = boardStyle,
                onSelected = onBoardStyle,
                ownedItemIds = ownedStoreItems,
                onLocked = { notice = "این طرح از «فروشگاه شطرنج» در همین منو قابل خرید است." },
            )
            Spacer(Modifier.height(18.dp))
            ChessPieceStylePicker(
                selected = pieceStyle,
                onSelected = onPieceStyle,
                ownedItemIds = ownedStoreItems,
                onLocked = { notice = "این مهره‌ها از «فروشگاه شطرنج» در همین منو قابل خرید هستند." },
            )
        }
        notice?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = chessSp(12), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        SettingsCard {
            SettingSwitch("افکت‌های صوتی", "صدای حرکت، گرفتن و پایان بازی", soundOn, onSoundOn)
            SettingSwitch("تصویر و ذرات متحرک", "حرکت آرام پس‌زمینه و برق ذرات", animationsEnabled, onAnimationsEnabled)
            SettingSwitch("چرخش خودکار در دونفرهٔ محلی", "بعد از هر نوبت، صفحه به سمت بازیکن بعدی بچرخد", autoRotate, onAutoRotate)
        }
    }
}

@Composable
internal fun ChessLocalSetupScreen(
    passAndPlay: Boolean,
    name: String,
    onName: (String) -> Unit,
    level: Int,
    onLevel: (Int) -> Unit,
    color: ColorChoice,
    onColor: (ColorChoice) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    var showRules by remember { mutableStateOf(false) }
    ChessMenuPage(
        title = if (passAndPlay) "دونفره روی یک گوشی" else "بازی تکی",
        subtitle = if (passAndPlay) "یک میز مشترک، دو بازیکن" else "حریف ربات را انتخاب کن",
        onBack = onBack,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { showRules = !showRules }) {
                Text(if (showRules) "بستن قوانین" else "قوانین بازی", color = MaterialTheme.colorScheme.primary)
            }
        }
        if (showRules) RulesCard()
        SettingsCard {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 14) onName(it) },
                label = { Text(if (passAndPlay) "نام بازیکن اول (اختیاری)" else "اسم شما") },
                placeholder = { Text("مثلاً تاله") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = chessOutlinedFieldColors(),
            )
            if (!passAndPlay) {
                Spacer(Modifier.height(16.dp))
                Text("سطح حریف", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    (1..4).forEach { value ->
                        Choice(
                            text = ChessText.levelName(value),
                            selected = value == level,
                            onClick = { onLevel(value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("رنگ شما", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Choice("سفید", color == ColorChoice.WHITE, { onColor(ColorChoice.WHITE) }, Modifier.weight(1f))
                    Choice("سیاه", color == ColorChoice.BLACK, { onColor(ColorChoice.BLACK) }, Modifier.weight(1f))
                    Choice("تصادفی", color == ColorChoice.RANDOM, { onColor(ColorChoice.RANDOM) }, Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "بازیکن سفید شروع می‌کند. تنظیم چرخش خودکار در بخش تنظیمات شطرنج است.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = chessSp(12),
                    lineHeight = chessSp(19),
                )
            }
        }
        ChessGradientButton(
            text = if (passAndPlay) "شروع بازی دونفره ♟️" else "شروع بازی با ربات ♟️",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "تخته، مهره، صدا و انیمیشن را از «تنظیمات» در منوی شطرنج عوض کن.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = chessSp(11),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun ChessMenuPage(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        ChessBackdrop(imageRes = R.drawable.chess_backdrop, modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("بازگشت", color = MaterialTheme.colorScheme.onSurface) }
                Spacer(Modifier.weight(1f))
                Text("♟", color = MaterialTheme.colorScheme.primary, fontSize = chessSp(22))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = chessSp(31), fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(12), textAlign = TextAlign.Center)
            }
            content()
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MenuAction(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(23.dp))
            .background(scheme.surface.copy(alpha = 0.82f))
            .border(1.dp, scheme.outline.copy(alpha = 0.55f), RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(scheme.primaryContainer.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) { Text(icon, color = scheme.onPrimaryContainer, fontSize = chessSp(23)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = scheme.onSurface, fontSize = chessSp(16), fontWeight = FontWeight.Bold)
            Text(subtitle, color = scheme.onSurfaceVariant, fontSize = chessSp(11), lineHeight = chessSp(17))
        }
        Text("‹", color = scheme.primary, fontSize = chessSp(28), fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(14))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = chessSp(11), lineHeight = chessSp(16))
        }
        Switch(checked = checked, onCheckedChange = onChecked, colors = chessSwitchColors())
    }
}

@Composable
internal fun Choice(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        fontSize = chessSp(12),
        color = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) scheme.primary else scheme.surfaceVariant.copy(alpha = 0.75f))
            .border(1.dp, if (selected) scheme.primary else scheme.outline, RoundedCornerShape(18.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.RadioButton, onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
    )
}

@Composable
internal fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.48f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
internal fun RulesCard() {
    SettingsCard {
        Text("قوانین شطرنج", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = chessSp(16))
        val rules = listOf(
            "پیاده یک خانه جلو می‌رود (اول بازی دو خانه)، کج می‌زند و وقتی به آخرین خط برسد، وزیر یا هر مهرهٔ دیگری می‌شود.",
            "اسب «L» حرکت می‌کند و از روی مهره‌ها رد می‌شود؛ فیل مورب، رخ راست، وزیر هر دو.",
            "شاه یک خانه به هر طرف؛ یک بار در بازی می‌تواند «قلعه» برود (با شرط خالی و غیرکیش بودن مسیر).",
            "کیش یعنی شاه زیر ضربه است؛ اگر هیچ راه قانونی برای نجات نباشد، مات است.",
            "پات، تکرار سه‌بارهٔ وضعیت، قانون ۵۰ حرکت و نبود مهرهٔ کافی هم مساوی است.",
            "می‌توانی پیشنهاد مساوی بدهی یا تسلیم شوی؛ حرکت‌های قانونی از همان نمای خصوصی بازیکن می‌آیند.",
        )
        rules.forEachIndexed { index, rule ->
            Text("${(index + 1).toFa()}. $rule", color = MaterialTheme.colorScheme.onSurface, fontSize = chessSp(13), lineHeight = chessSp(20))
        }
    }
}
