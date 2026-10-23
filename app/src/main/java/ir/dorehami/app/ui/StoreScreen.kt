package ir.dorehami.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.dorehami.app.R
import ir.dorehami.app.store.CoinPack
import ir.dorehami.app.store.PurchaseResult
import ir.dorehami.app.store.StoreCatalog
import ir.dorehami.app.store.StoreItem
import ir.dorehami.app.store.WalletSnapshot
import ir.dorehami.uikit.toFa

/**
 * Chess-only coin wallet and cosmetic store. Item purchases are local; paid coin packs are
 * explicitly a demo and cannot change the wallet until verified payment/backend integration.
 */
@Composable
internal fun StoreScreen(
    wallet: WalletSnapshot,
    onPurchase: (StoreItem) -> PurchaseResult,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    var message by remember { mutableStateOf<String?>(null) }
    var demoPack by remember { mutableStateOf<CoinPack?>(null) }

    Box(Modifier.fillMaxSize()) {
        DorehamiBackdrop(
            imageRes = R.drawable.dorehami_home_art,
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { DorehamiTopBar(title = "فروشگاه شطرنج", onBack = onBack) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppGlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(24.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "موجودی کیف‌پول",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = "${wallet.balance.toFa()} سکه",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                            )
                        }
                        Text("🪙", style = MaterialTheme.typography.displayMedium)
                    }
                }

                message?.let {
                    AppGlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }

                SectionTitle("آیتم‌های ظاهری", "خرید و نگه‌داری مالکیت آیتم‌ها روی همین گوشی انجام می‌شود.")
                StoreCatalog.items.forEach { item ->
                    ItemCard(
                        item = item,
                        owned = item.id in wallet.ownedItemIds,
                        onBuy = {
                            message = when (val result = onPurchase(item)) {
                                is PurchaseResult.Purchased ->
                                    "«${item.title}» خریداری شد؛ ${result.balance.toFa()} سکه باقی مانده."
                                is PurchaseResult.AlreadyOwned -> "«${item.title}» قبلاً خریداری شده است."
                                is PurchaseResult.InsufficientCoins ->
                                    "سکه کافی نیست؛ موجودی ${result.balance.toFa()} و قیمت ${item.price.toFa()} سکه است."
                            }
                        },
                    )
                }

                SectionTitle("بسته‌های سکه", "پرداخت و شارژ آنلاین فعلاً نمایشی است؛ هیچ تراکنشی انجام نمی‌شود.")
                StoreCatalog.coinPacks.forEach { pack ->
                    AppGlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = pack.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "${pack.coins.toFa()} سکه",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            OutlinedButton(onClick = { demoPack = pack }) {
                                Text("خرید آنلاین · نمایشی")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    demoPack?.let { pack ->
        AlertDialog(
            onDismissRequest = { demoPack = null },
            title = { Text("پرداخت آنلاین نمایشی") },
            text = {
                Text(
                    "«${pack.title}» شامل ${pack.coins.toFa()} سکه است. درگاه پرداخت و تأیید سرور هنوز وصل نشده‌اند؛ بنابراین با این دکمه پول یا سکه‌ای جابه‌جا نمی‌شود.",
                )
            },
            confirmButton = { TextButton(onClick = { demoPack = null }) { Text("متوجه شدم") } },
        )
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(horizontal = 4.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = detail,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ItemCard(item: StoreItem, owned: Boolean, onBuy: () -> Unit) {
    AppGlassPanel(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.emoji, style = MaterialTheme.typography.headlineMedium)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = item.description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = onBuy,
                enabled = !owned,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (owned) "در کیف‌پول شما ✓" else "خرید با ${item.price.toFa()} سکه")
            }
        }
    }
}
