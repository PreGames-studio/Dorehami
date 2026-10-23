package ir.dorehami.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.app.R
import ir.dorehami.uikit.toFa

/** First screen of the app: bespoke artwork, the game-picker action, and settings. */
@Composable
fun HomeScreen(
    onEnterGame: () -> Unit,
    onSettings: () -> Unit,
    onStore: () -> Unit,
    coinBalance: Long = 0,
) {
    val colors = MaterialTheme.colorScheme
    val glass = colors.surface.copy(alpha = 0.68f)

    Box(Modifier.fillMaxSize()) {
        DorehamiBackdrop(
            imageRes = R.drawable.dorehami_home_art,
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(glass)
                    .border(1.dp, colors.outline.copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 15.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "♟  بازی‌های دورهمی",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "هر جمعی، یک بازی تازه",
                    color = colors.secondary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "دورهمی",
                    color = colors.onSurface,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "بازی‌های دوست‌داشتنی، برای لحظه‌های به‌یادماندنی",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }

            AppGlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onEnterGame,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 58.dp),
                        shape = RoundedCornerShape(19.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🎲", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = "ورود به بازی‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(19.dp),
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.9f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.secondary,
                            containerColor = Color.Transparent,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("⚙️", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "تنظیمات",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onStore,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                        shape = RoundedCornerShape(19.dp),
                        border = BorderStroke(1.dp, colors.outline.copy(alpha = 0.9f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.secondary,
                            containerColor = Color.Transparent,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🪙", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "فروشگاه",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "• ${coinBalance.toFa()} سکه",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(
                text = "شطرنج  •  حکم  •  پانتومیم  •  دژبان",
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(glass.copy(alpha = 0.78f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
