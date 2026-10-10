package ir.dorehami.games.pantohub.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.dorehami.core.uikit.DorehamiPageFrame as PageFrame
import ir.dorehami.games.pantohub.GamePhase
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.GameStatistics
import ir.dorehami.games.pantohub.GameStateJsonCodec
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.WordOutcome
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HistoryTab { MATCHES, TEAMS, PLAYERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameHistoryScreen(repository: PantohubRepository, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var games by remember { mutableStateOf(emptyList<GameState>()) }
    var tab by remember { mutableStateOf(HistoryTab.MATCHES) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<GameState?>(null) }
    var deleteTarget by remember { mutableStateOf<GameState?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(reload) {
        loading = true
        runCatching { withContext(Dispatchers.IO) { repository.loadAllFinishedGames() } }
            .onSuccess { games = it; loading = false }
            .onFailure { error = "تاریخچه باز نشد."; loading = false }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        val content = JSONObject().put("schemaVersion", 1).put(
                            "games",
                            JSONArray().apply { games.forEach { put(JSONObject(GameStateJsonCodec.encode(it))) } },
                        ).toString(2)
                        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                            ?: error("فایل خروجی ساخته نشد.")
                    }
                }.onFailure { error = "خروجی تاریخچه ذخیره نشد." }
            }
        }
    }

    PageFrame("تاریخچه و آمار", onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            HistoryTab.entries.forEach { item ->
                FilterChip(
                    selected = tab == item,
                    onClick = { tab = item },
                    label = { Text(when (item) {
                        HistoryTab.MATCHES -> "بازی‌ها"
                        HistoryTab.TEAMS -> "آمار تیمی"
                        HistoryTab.PLAYERS -> "آمار فردی"
                    }) },
                )
            }
        }
        if (tab == HistoryTab.MATCHES) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("جست‌وجوی نام بازی یا تیم") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { exportLauncher.launch("pantohub-history.json") }, modifier = Modifier.padding(top = 8.dp)) { Text("خروجی JSON") }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        when {
            loading -> Text("در حال بارگذاری…")
            tab == HistoryTab.MATCHES -> {
                val normalizedQuery = query.trim().lowercase()
                val filtered = games.filter { game ->
                    normalizedQuery.isBlank() || game.name.lowercase().contains(normalizedQuery) ||
                        game.teams.any { it.name.lowercase().contains(normalizedQuery) }
                }
                if (filtered.isEmpty()) Text("بازی پایان‌یافته‌ای با این جست‌وجو پیدا نشد.")
                filtered.forEach { game ->
                    HistoryGameCard(
                        game = game,
                        onOpen = { selected = game },
                        onDelete = { deleteTarget = game },
                    )
                }
            }
            tab == HistoryTab.TEAMS -> {
                val stats = GameStatistics.teams(games)
                if (stats.isEmpty()) Text("با پایان چند بازی، آمار تیمی اینجا نمایش داده می‌شود.")
                stats.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("بازی ${item.gamesPlayed.toString().toPersianDigits()}  •  برد ${item.wins.toString().toPersianDigits()}  •  باخت ${item.losses.toString().toPersianDigits()}  •  مساوی ${item.draws.toString().toPersianDigits()}")
                            Text("امتیاز ${item.totalPoints.toString().toPersianDigits()}  •  میانگین ${String.format(Locale.ROOT, "%.1f", item.averagePointsPerGame).toPersianDigits()}  •  پاسخ درست ${item.correctAnswers.toString().toPersianDigits()}")
                            Text("آسان ${item.easyCorrect.toString().toPersianDigits()}  •  متوسط ${item.mediumCorrect.toString().toPersianDigits()}  •  سخت ${item.hardCorrect.toString().toPersianDigits()}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            else -> {
                val stats = GameStatistics.players(games)
                if (stats.isEmpty()) Text("آمار فردی از رخدادهای ثبت‌شدهٔ نوبت‌ها محاسبه می‌شود؛ هنوز داده‌ای نیست.")
                stats.forEach { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("بازی ${item.gamesPlayed.toString().toPersianDigits()}  •  اجرا ${item.turnsPerformed.toString().toPersianDigits()}  •  نوبت موفق ${item.successfulTurns.toString().toPersianDigits()}")
                            Text("واژهٔ دیده‌شده ${item.wordsUsed.toString().toPersianDigits()}  •  پاسخ درست ${item.correctAnswers.toString().toPersianDigits()}  •  امتیاز منتسب به نوبت‌ها ${item.pointsEarned.toString().toPersianDigits()}")
                        }
                    }
                }
            }
        }
    }

    selected?.let { game ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(game.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("شروع: ${formatDate(game.createdAtEpochMs)}")
                    game.finishedAtEpochMs?.let { Text("پایان: ${formatDate(it)}") }
                    Text("نوبت‌های ثبت‌شده: ${game.turns.size.toString().toPersianDigits()}")
                    game.teams.forEach { team ->
                        Text("${team.name}: ${(game.teamScores[team.id] ?: 0).toString().toPersianDigits()} امتیاز")
                    }
                    game.turns.forEach { turn ->
                        val correct = turn.attempts.count { it.outcome == WordOutcome.CORRECT }
                        Text("• ${turn.playerName} (${game.teams.firstOrNull { it.id == turn.teamId }?.name.orEmpty()}): ${correct.toString().toPersianDigits()} پاسخ درست، ${turn.scoreEarned.toString().toPersianDigits()} امتیاز", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("بستن") } },
        )
    }

    deleteTarget?.let { game ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("حذف بازی از تاریخچه؟") },
            text = { Text("«${game.name}» و رخدادهای آماری همین بازی حذف می‌شوند. این کار قابل بازگشت نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { repository.deleteGame(game.id) } }
                        result.onSuccess { reload++ }.onFailure { error = "بازی از تاریخچه حذف نشد." }
                    }
                }) { Text("حذف بازی") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("لغو") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryGameCard(game: GameState, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(game.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(formatDate(game.createdAtEpochMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            game.teams.forEach { team ->
                Text("${team.name}: ${(game.teamScores[team.id] ?: 0).toString().toPersianDigits()}", style = MaterialTheme.typography.bodyMedium)
            }
            Text("${game.turns.size.toString().toPersianDigits()} نوبت  •  ${game.winnerTeamIds.mapNotNull { id -> game.teams.firstOrNull { it.id == id }?.name }.joinToString("، ").ifBlank { "بدون برنده" }}")
            TextButton(onClick = onDelete) { Text("حذف با تأیید") }
        }
    }
}

private fun formatDate(epochMs: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("fa", "IR")).format(Date(epochMs)).toPersianDigits()
