package ir.dorehami.app.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.dorehami.app.R

/**
 * One game-picker entry. `module` is the existing or planned Gradle module and stays the routing
 * key, so the presentation redesign does not change any individual game's behavior.
 */
private data class GameEntry(
    val title: String,
    val emoji: String,
    val subtitle: String,
    val module: String,
    val accent: GameAccent,
    val implemented: Boolean = false,
)

private enum class GameAccent { Warm, Teal, Purple, Gold }

private val games = listOf(
    GameEntry("حکم", "🎴", "۲ تا ۴ نفره • ربات و آنلاین", "games/hokm", GameAccent.Warm, implemented = true),
    GameEntry("شطرنج", "♟️", "تختهٔ سه‌بعدی • بازی نوبتی", "games/chess", GameAccent.Teal, implemented = true),
    GameEntry("پانتومیم", "🎭", "بازی نمایشی گروهی", "games/pantohub", GameAccent.Purple, implemented = true),
    GameEntry("دژبان", "🏰", "بازی استراتژیک", "games/dejban", GameAccent.Gold),
)

/** Compact two-column game picker over original Dorehami artwork. */
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onOpenHokm: () -> Unit = {},
    onOpenChess: () -> Unit = {},
    onPantomime: () -> Unit = {},
) {
    val context = LocalContext.current
    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize()) {
        DorehamiBackdrop(
            imageRes = R.drawable.dorehami_games_art,
            modifier = Modifier.fillMaxSize(),
        )
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { DorehamiTopBar(title = "انتخاب بازی", onBack = onBack) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
            ) {
                AppGlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(
                        text = "یکی رو انتخاب کن و بازی رو شروع کن",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = games, key = { it.module }) { game ->
                        GameButton(
                            game = game,
                            onClick = {
                                when {
                                    !game.implemented -> Toast.makeText(
                                        context,
                                        "بازی «${game.title}» هنوز آماده نشده است (${game.module})",
                                        Toast.LENGTH_SHORT,
                                    ).show()

                                    game.module == "games/hokm" -> onOpenHokm()
                                    game.module == "games/chess" -> onOpenChess()
                                    game.module == "games/pantohub" -> onPantomime()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GameButton(
    game: GameEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminance() < 0.4f
    val accent = when (game.accent) {
        GameAccent.Warm -> colors.primary
        GameAccent.Teal -> colors.secondary
        GameAccent.Purple -> colors.tertiary
        GameAccent.Gold -> if (dark) Color(0xFFE4C46A) else Color(0xFF8B6412)
    }
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(166.dp)
            .clip(shape)
            .background(colors.surface.copy(alpha = if (dark) 0.84f else 0.78f))
            .border(1.dp, accent.copy(alpha = 0.48f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(game.emoji, fontSize = 25.sp)
                }
                Text(
                    text = if (game.implemented) "آماده" else "به‌زودی",
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
            Text(
                text = game.title,
                color = colors.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = game.subtitle,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (game.implemented) "شروع بازی" else "به‌زودی",
                color = accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "←",
                color = accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
