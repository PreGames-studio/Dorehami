package ir.dorehami.games.hokm.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.uikit.toFa

/**
 * Settings and the card/table shop of the Hokm module — the same shape chess uses: one screen for
 * preferences, one for the collectible looks, both reachable from the main menu.
 */

@Composable
internal fun HokmSettingsScreen(
    name: String,
    onName: (String) -> Unit,
    target: Int,
    onTarget: (Int) -> Unit,
    playerCount: Int,
    onPlayerCount: (Int) -> Unit,
    botLevel: String,
    onBotLevel: (String) -> Unit,
    soundOn: Boolean,
    onSound: (Boolean) -> Unit,
    hapticsOn: Boolean,
    onHaptics: (Boolean) -> Unit,
    motion: HokmMotionLevel,
    onMotion: (HokmMotionLevel) -> Unit,
    cardBackId: String,
    tableId: String,
    wins: Int,
    onOpenShop: () -> Unit,
    onResetStats: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        HokmBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onBack = onBack, onTap = {})
            Spacer(Modifier.height(12.dp))
            Text(
                "تنظیمات حکم",
                color = HokmPalette.text,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "همه‌چیز اینجا ذخیره می‌شود و در بازی بعدی هم می‌ماند",
                color = HokmPalette.muted,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            SectionHeader("میز و بازیکن‌ها", "قبل از شروع مشخص کن")
            Spacer(Modifier.height(8.dp))
            HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 14) onName(it) },
                    label = { Text("نام نمایشی") },
                    placeholder = { Text("مثلاً آرش") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text("تعداد بازیکن", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(4 to "۴ نفره", 3 to "۳ نفره", 2 to "۲ نفره").forEach { (count, label) ->
                        Hokm3DButton(
                            text = label,
                            trailingIcon = if (playerCount == count) HokmIcon.Check else null,
                            tone = if (playerCount == count) HokmButtonTone.GOLD else HokmButtonTone.GLASS,
                            height = 46.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { onPlayerCount(count) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("امتیاز پایان مسابقه", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(1, 3, 5, 7).forEach { points ->
                        Hokm3DButton(
                            text = points.toFa(),
                            trailingIcon = if (target == points) HokmIcon.Check else null,
                            tone = if (target == points) HokmButtonTone.GOLD else HokmButtonTone.GLASS,
                            height = 44.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { onTarget(points) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("قدرت ربات‌ها", color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(HokmModule.BOT_PRO to "حرفه‌ای", HokmModule.BOT_EASY to "تمرینی").forEach { (level, label) ->
                        Hokm3DButton(
                            text = label,
                            trailingIcon = if (botLevel == level) HokmIcon.Check else null,
                            tone = if (botLevel == level) HokmButtonTone.EMERALD else HokmButtonTone.GLASS,
                            height = 46.dp,
                            modifier = Modifier.weight(1f),
                            onClick = { onBotLevel(level) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("صدا و حرکت", "برای بازی راحت‌تر")
            Spacer(Modifier.height(8.dp))
            HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 6.dp) {
                SettingToggle(
                    title = "صدای بازی",
                    subtitle = if (soundOn) "روشن" else "خاموش",
                    checked = soundOn,
                    onClick = { onSound(!soundOn) },
                )
                SettingToggle(
                    title = "لرزش لمسی",
                    subtitle = if (hapticsOn) "روشن" else "خاموش",
                    checked = hapticsOn,
                    onClick = { onHaptics(!hapticsOn) },
                )
                SettingToggle(
                    title = "جلوه‌های حرکتی",
                    subtitle = when (motion) {
                        HokmMotionLevel.FULL -> "کامل"
                        HokmMotionLevel.REDUCED -> "کاهش‌یافته"
                        HokmMotionLevel.OFF -> "خاموش"
                    },
                    checked = motion != HokmMotionLevel.OFF,
                    onClick = { onMotion(motion.next()) },
                )
            }

            Spacer(Modifier.height(14.dp))
            SectionHeader("ظاهر میز", "پشت کارت و پارچهٔ میز")
            Spacer(Modifier.height(8.dp))
            HokmGlassPanel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CardBackPreview(HokmSkins.cardBack(cardBackId), width = 40.dp)
                    Column(Modifier.weight(1f)) {
                        Text(HokmSkins.cardBack(cardBackId).titleFa, color = HokmPalette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(HokmSkins.table(tableId).titleFa, color = HokmPalette.muted, fontSize = 11.sp)
                    }
                    Hokm3DButton(
                        text = "فروشگاه",
                        leadingIcon = HokmIcon.Shop,
                        tone = HokmButtonTone.GOLD,
                        height = 44.dp,
                        modifier = Modifier.width(116.dp),
                        onClick = onOpenShop,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "با هر برد یک طرح تازه باز می‌شود · بردهای تو: ${wins.toFa()}",
                    color = HokmPalette.muted,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            Hokm3DButton(
                text = "پاک‌کردن کارنامه",
                subtitle = "آمار بردها و کُت‌ها صفر می‌شود",
                leadingIcon = HokmIcon.Trash,
                tone = HokmButtonTone.CORAL,
                height = 54.dp,
                modifier = Modifier.fillMaxWidth(),
                onClick = onResetStats,
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
internal fun HokmShopScreen(
    cardBackId: String,
    tableId: String,
    wins: Int,
    onPickCardBack: (String) -> Unit,
    onPickTable: (String) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize()) {
        HokmBackdrop(Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HomeHeader(onBack = onBack, onTap = {})
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HokmIconView(HokmIcon.Shop, color = HokmPalette.gold, size = 22.dp)
                Text("فروشگاه حکم", color = HokmPalette.text, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            Text(
                "طرح‌ها با بردهای خودت باز می‌شوند؛ هیچ‌چیز خریدنی با پول نیست",
                color = HokmPalette.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            HokmPill("بردهای تو: ${wins.toFa()}", icon = HokmIcon.Trophy, color = HokmPalette.gold)
            Spacer(Modifier.height(14.dp))

            SectionHeader("پشت کارت‌ها", "روی کارت‌های پشت‌رو می‌بینی")
            Spacer(Modifier.height(8.dp))
            HokmSkins.cardBacks.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { skin ->
                        ShopCardBackTile(
                            skin = skin,
                            selected = skin.id == cardBackId,
                            unlocked = HokmSkins.unlocked(skin, wins),
                            winsLeft = (skin.winsRequired - wins).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                            onClick = { onPickCardBack(skin.id) },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionHeader("میز بازی", "رنگ مخمل، حاشیه و نقش زمین")
            Spacer(Modifier.height(8.dp))
            HokmSkins.tables.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { skin ->
                        ShopTableTile(
                            skin = skin,
                            selected = skin.id == tableId,
                            unlocked = HokmSkins.unlocked(skin, wins),
                            winsLeft = (skin.winsRequired - wins).coerceAtLeast(0),
                            modifier = Modifier.weight(1f),
                            onClick = { onPickTable(skin.id) },
                        )
                    }
                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun CardBackPreview(skin: HokmCardBackSkin, width: androidx.compose.ui.unit.Dp) {
    CardBackView(width = width, elevation = 2.dp, skin = skin)
}

@Composable
private fun ShopCardBackTile(
    skin: HokmCardBackSkin,
    selected: Boolean,
    unlocked: Boolean,
    winsLeft: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = if (selected) HokmPalette.gold else HokmPalette.muted
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .border(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 0.95f else 0.45f), shape)
            .clickable(enabled = unlocked, role = Role.Button, onClick = onClick)
            .padding(9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CardBackView(width = 46.dp, elevation = 3.dp, skin = skin)
            if (!unlocked) {
                Box(
                    Modifier
                        .size(46.dp, 46.dp / CARD_ASPECT)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    HokmIconView(HokmIcon.Lock, color = Color.White, size = 18.dp)
                }
            }
        }
        Text(
            skin.titleFa,
            color = HokmPalette.text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(
            when {
                selected -> "انتخاب‌شده"
                unlocked -> "باز است"
                else -> "${winsLeft.toFa()} برد دیگر"
            },
            color = if (selected) HokmPalette.gold else HokmPalette.muted,
            fontSize = 10.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ShopTableTile(
    skin: HokmTableSkin,
    selected: Boolean,
    unlocked: Boolean,
    winsLeft: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = if (selected) HokmPalette.gold else HokmPalette.muted
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            .border(if (selected) 2.dp else 1.dp, accent.copy(alpha = if (selected) 0.95f else 0.42f), shape)
            .clickable(enabled = unlocked, role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(skin.wash, skin.rim))),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(skin.accent.copy(alpha = 0.22f), radius = size.minDimension * 0.28f)
                drawRect(
                    color = skin.accent.copy(alpha = 0.45f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.03f),
                )
            }
            if (!unlocked) {
                HokmIconView(HokmIcon.Lock, color = Color.White.copy(alpha = 0.9f), size = 20.dp)
            }
        }
        Text(skin.titleFa, color = HokmPalette.text, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(
            when {
                selected -> "انتخاب‌شده"
                unlocked -> skin.subtitleFa
                else -> "${winsLeft.toFa()} برد دیگر"
            },
            color = if (selected) HokmPalette.gold else HokmPalette.muted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

/** Cycles FULL → REDUCED → OFF, the same order the offline screen uses. */
private fun HokmMotionLevel.next(): HokmMotionLevel = when (this) {
    HokmMotionLevel.FULL -> HokmMotionLevel.REDUCED
    HokmMotionLevel.REDUCED -> HokmMotionLevel.OFF
    HokmMotionLevel.OFF -> HokmMotionLevel.FULL
}
