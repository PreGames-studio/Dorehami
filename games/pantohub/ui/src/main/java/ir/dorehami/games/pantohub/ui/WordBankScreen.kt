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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.dorehami.games.pantohub.Difficulty
import ir.dorehami.games.pantohub.PhraseType
import ir.dorehami.games.pantohub.WordBankCsvCodec
import ir.dorehami.games.pantohub.WordBankRules
import ir.dorehami.games.pantohub.WordCategories
import ir.dorehami.games.pantohub.WordRecord
import ir.dorehami.games.pantohub.ui.data.PantohubRepository
import ir.dorehami.games.pantohub.ui.data.WordBankSummary
import ir.dorehami.games.pantohub.ui.data.WordImportReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

private data class PendingWordImport(
    val records: List<WordRecord>,
    val duplicateRows: Int,
    val existingDuplicates: Int,
    val invalidRows: Int,
    val details: List<String>,
    val sourceName: String,
)

private const val MAX_IMPORT_CHARS = 20_000_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WordBankScreen(
    repository: PantohubRepository,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var words by remember { mutableStateOf(emptyList<WordRecord>()) }
    var summary by remember { mutableStateOf<WordBankSummary?>(null) }
    var categories by remember { mutableStateOf(emptyList<String>()) }
    var query by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf<Difficulty?>(null) }
    var category by remember { mutableStateOf<String?>(null) }
    var showInactive by remember { mutableStateOf(false) }
    var showUnreviewed by remember { mutableStateOf(true) }
    var pageOffset by remember { mutableStateOf(0) }
    var reload by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var editTarget by remember { mutableStateOf<WordRecord?>(null) }
    var deleteWordTarget by remember { mutableStateOf<WordRecord?>(null) }
    var addDialog by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<PendingWordImport?>(null) }
    var reportDialog by remember { mutableStateOf<WordImportReport?>(null) }

    suspend fun refresh() {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                repository.searchWords(query, difficulty, category, showInactive, showUnreviewed, 100, pageOffset),
                repository.bankSummary(),
                repository.listCategories(),
            )
        }
        words = loaded.first
        summary = loaded.second
        categories = loaded.third
        error = null
        loading = false
    }

    LaunchedEffect(query, difficulty, category, showInactive, showUnreviewed, pageOffset, reload) {
        delay(180)
        runCatching { refresh() }.onFailure {
            loading = false
            error = "خواندن بانک ناموفق بود؛ دوباره تلاش کن."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val parsed = runCatching {
                    withContext(Dispatchers.IO) {
                        val text = readImportText(context, uri)
                        parseWordImport(text, uri.lastPathSegment ?: "فایل")
                    }
                }
                parsed.onSuccess { candidate ->
                    val duplicates = withContext(Dispatchers.IO) { repository.previewImport(candidate.records) }
                    pendingImport = candidate.copy(existingDuplicates = duplicates)
                }.onFailure { error = it.message ?: "ساختار فایل قابل خواندن نیست." }
            }
        }
    }

    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) exportWords(uri, "csv", context, repository, scope, onFailure = { error = it })
    }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportWords(uri, "json", context, repository, scope, onFailure = { error = it })
    }

    PantohubPageFrame("مدیریت بانک واژه", onBack) {
        Text("جست‌وجو، بازبینی، ورود و خروجی بانک آفلاین", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary?.let { BankSummaryCard(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { addDialog = true }, modifier = Modifier.weight(1f)) { Text("افزودن واژه") }
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("text/*", "application/json", "*/*")) }, modifier = Modifier.weight(1f)) { Text("ورود فایل") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { csvExportLauncher.launch("pantohub-words.csv") }, modifier = Modifier.weight(1f)) { Text("خروجی CSV") }
            OutlinedButton(onClick = { jsonExportLauncher.launch("pantohub-words.json") }, modifier = Modifier.weight(1f)) { Text("خروجی JSON") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                "فایل ورودی غیرقابل‌اعتماد است: رکوردهای واردشده ابتدا تأییدنشده می‌مانند و تا بازبینی فعال نمی‌شوند. ورود، بانک فعلی را پاک نمی‌کند.",
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedTextField(
            colors = pantohubFieldColors(PantohubTint.Mint),
            value = query,
            onValueChange = { query = it; pageOffset = 0 },
            label = { Text("جست‌وجوی واژه یا عبارت") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = difficulty == null, onClick = { difficulty = null; pageOffset = 0 }, label = { Text("همهٔ سطح‌ها") })
            Difficulty.entries.forEach { item ->
                FilterChip(selected = difficulty == item, onClick = { difficulty = item; pageOffset = 0 }, label = { Text(item.labelFa) })
            }
        }
        if (categories.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(selected = category == null, onClick = { category = null; pageOffset = 0 }, label = { Text("همهٔ دسته‌ها") })
            }
            categories.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { item ->
                        FilterChip(
                            colors = pantohubChipColors(PantohubTint.Peach),
                            selected = category == item,
                            onClick = { category = if (category == item) null else item; pageOffset = 0 },
                            label = { Text(WordCategories.labelFa(item)) },
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(selected = showInactive, onClick = { showInactive = !showInactive }, label = { Text("نمایش غیرفعال‌ها") })
            FilterChip(selected = showUnreviewed, onClick = { showUnreviewed = !showUnreviewed }, label = { Text("نمایش بازبینی‌نشده‌ها") })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        when {
            loading -> Text("در حال خواندن واژه‌ها…")
            words.isEmpty() -> Text("رکوردی با این فیلتر پیدا نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> words.forEach { word ->
                Card(modifier = Modifier.fillMaxWidth(), onClick = { editTarget = word }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(word.text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(word.difficulty.labelFa, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("${WordCategories.labelFa(word.category)}  •  ${word.type.labelFa}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            when {
                                !word.active -> "غیرفعال"
                                !word.validated -> "در انتظار بازبینی؛ در بازی استفاده نمی‌شود"
                                else -> "فعال و بازبینی‌شده"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (word.active && word.validated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    val result = runCatching { withContext(Dispatchers.IO) { repository.setWordValidated(word.id, !word.validated) } }
                                    result.onSuccess {
                                        info = if (word.validated) "واژه از حالت بازبینی‌شده خارج شد." else "واژه بازبینی و فعال شد؛ بررسی مناسب‌بودن آن با شماست."
                                        reload++
                                        onChanged()
                                    }.onFailure { error = "تغییر وضعیت بازبینی ذخیره نشد." }
                                }
                            }) { Text(if (word.validated) "لغو تأیید" else "تأیید مناسب‌بودن") }
                            TextButton(onClick = {
                                scope.launch {
                                    val result = runCatching { withContext(Dispatchers.IO) { repository.setWordActive(word.id, !word.active) } }
                                    result.onSuccess {
                                        info = if (word.active) "واژه غیرفعال شد و از بازی‌های بعدی کنار گذاشته می‌شود." else "واژه فعال شد."
                                        reload++
                                        onChanged()
                                    }.onFailure { error = "تغییر وضعیت فعال‌بودن ذخیره نشد." }
                                }
                            }) { Text(if (word.active) "غیرفعال‌کردن" else "فعال‌کردن") }
                            TextButton(onClick = { deleteWordTarget = word }) { Text("حذف رکورد") }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { pageOffset = (pageOffset - 100).coerceAtLeast(0) }, enabled = pageOffset > 0) { Text("صفحهٔ قبل") }
            Text("${(pageOffset / 100 + 1).toString().toPersianDigits()}", modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { if (words.size == 100) pageOffset += 100 }, enabled = words.size == 100) { Text("صفحهٔ بعد") }
        }
    }

    if (addDialog || editTarget != null) {
        WordEditDialog(
            word = editTarget,
            categories = categories,
            onDismiss = { addDialog = false; editTarget = null },
            onSave = { word ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) { repository.addReviewedWord(word) }
                    result.onSuccess {
                        info = "واژه ذخیره شد و در بازی‌های بعدی قابل انتخاب است."
                        addDialog = false
                        editTarget = null
                        reload++
                        onChanged()
                    }.onFailure { error = it.message ?: "ذخیرهٔ واژه ناموفق بود." }
                }
            },
        )
    }

    deleteWordTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteWordTarget = null },
            title = { Text("حذف رکورد از بانک؟") },
            text = { Text("«${target.text}» از بانک واژه حذف می‌شود. تاریخچهٔ بازی‌های قبلی چون متن را در رخداد خود ذخیره کرده، تغییر نمی‌کند.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteWordTarget = null
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { repository.deleteWord(target.id) } }
                        result.onSuccess {
                            reload++
                            onChanged()
                            info = "رکورد از بانک حذف شد؛ تاریخچه دست‌نخورده ماند."
                        }.onFailure { error = "حذف رکورد ذخیره نشد." }
                    }
                }) { Text("حذف رکورد") }
            },
            dismissButton = { TextButton(onClick = { deleteWordTarget = null }) { Text("لغو") } },
        )
    }

    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("پیش‌نمایش ورود داده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("فایل: ${pending.sourceName}")
                    Text("رکوردهای آماده برای ورود: ${pending.records.size.toString().toPersianDigits()}")
                    Text("تکراری در فایل: ${pending.duplicateRows.toString().toPersianDigits()}  •  مشابه بانک فعلی: ${pending.existingDuplicates.toString().toPersianDigits()}")
                    Text("ردیف‌های نامعتبر: ${pending.invalidRows.toString().toPersianDigits()}")
                    Text("همهٔ رکوردهای جدید بدون تأیید وارد می‌شوند و بانک فعلی جایگزین یا پاک نخواهد شد.")
                    pending.details.take(4).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val imported = runCatching {
                            withContext(Dispatchers.IO) {
                                repository.importRecords(
                                    pending.records,
                                    duplicates = pending.duplicateRows,
                                    invalidRows = pending.invalidRows,
                                    details = pending.details,
                                )
                            }
                        }
                        imported.onSuccess {
                            reportDialog = it
                            pendingImport = null
                            reload++
                            onChanged()
                        }.onFailure { error = "ورود بانک کامل نشد؛ بانک فعلی دست‌نخورده باقی ماند." }
                    }
                }, enabled = pending.records.isNotEmpty()) { Text("ورود امن") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("لغو") } },
        )
    }

    reportDialog?.let { report ->
        AlertDialog(
            onDismissRequest = { reportDialog = null },
            title = { Text("گزارش ورود") },
            text = {
                Text("جدید: ${report.inserted.toString().toPersianDigits()}\nتکراری: ${report.duplicates.toString().toPersianDigits()}\nنامعتبر: ${report.invalidRows.toString().toPersianDigits()}\nرکوردهای جدید تا بازبینی در بازی استفاده نمی‌شوند.")
            },
            confirmButton = { TextButton(onClick = { reportDialog = null }) { Text("باشه") } },
        )
    }
}

@Composable
private fun BankSummaryCard(summary: WordBankSummary) {
    var showAllCategories by remember(summary.byCategory) { mutableStateOf(false) }
    val orderedCategories = summary.byCategory.entries.sortedByDescending { it.value }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("شمارش واقعی بانک", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("کل رکوردها: ${summary.total.toString().toPersianDigits()}  •  فعال: ${summary.active.toString().toPersianDigits()}  •  بازبینی‌شده: ${summary.reviewed.toString().toPersianDigits()}  •  قابل‌بازی: ${summary.playable.toString().toPersianDigits()}")
            Difficulty.entries.forEach { difficulty ->
                Text("${difficulty.labelFa}: ${(summary.byDifficulty[difficulty] ?: 0).toString().toPersianDigits()}", style = MaterialTheme.typography.bodySmall)
            }
            (if (showAllCategories) orderedCategories else orderedCategories.take(8)).forEach { (category, count) ->
                Text("${WordCategories.labelFa(category)}: ${count.toString().toPersianDigits()}", style = MaterialTheme.typography.bodySmall)
            }
            if (orderedCategories.size > 8) {
                TextButton(onClick = { showAllCategories = !showAllCategories }) {
                    Text(if (showAllCategories) "نمایش دسته‌های کمتر" else "نمایش همهٔ ${orderedCategories.size.toString().toPersianDigits()} دسته")
                }
            }
            Text("هدف ۲۰٬۰۰۰ رکورد هنوز تکمیل نشده است؛ رکوردها با عدد هدف پر نمی‌شوند.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WordEditDialog(
    word: WordRecord?,
    categories: List<String>,
    onDismiss: () -> Unit,
    onSave: (WordRecord) -> Unit,
) {
    var text by remember(word?.id) { mutableStateOf(word?.text.orEmpty()) }
    var category by remember(word?.id) { mutableStateOf(word?.category ?: WordCategories.ANIMALS) }
    var difficulty by remember(word?.id) { mutableStateOf(word?.difficulty ?: Difficulty.EASY) }
    var type by remember(word?.id) { mutableStateOf(word?.type ?: PhraseType.WORD) }
    var validationError by remember(word?.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (word == null) "افزودن واژهٔ بازبینی‌شده" else "ویرایش واژه") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it; validationError = null }, label = { Text("واژه یا عبارت فارسی") }, modifier = Modifier.fillMaxWidth())
                Text("سطح دشواری", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Difficulty.entries.forEach { item ->
                        FilterChip(selected = difficulty == item, onClick = { difficulty = item }, label = { Text(item.labelFa) })
                    }
                }
                OutlinedTextField(
                    colors = pantohubFieldColors(PantohubTint.Peach),
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("شناسهٔ دسته (مثلاً animals)") },
                    supportingText = { Text("دسته‌های شناخته‌شده: ${categories.take(5).joinToString { WordCategories.labelFa(it) }}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PhraseType.entries.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { item ->
                            FilterChip(selected = type == item, onClick = { type = item }, label = { Text(item.labelFa) })
                        }
                    }
                }
                Text("با ذخیره، تأیید می‌کنی عبارت واقعی، روشن و مناسب پانتومیم است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val record = WordRecord(
                    id = word?.id ?: UUID.randomUUID().toString(),
                    text = text.trim(),
                    difficulty = difficulty,
                    category = category.trim(),
                    type = type,
                    validated = true,
                    active = word?.active ?: true,
                    sourceVersion = word?.sourceVersion ?: "manual-reviewed-v1",
                )
                val errors = WordBankRules.validate(record)
                if (errors.isEmpty()) onSave(record) else validationError = errors.first()
            }) { Text("ذخیره و تأیید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } },
    )
}

private fun readImportText(context: android.content.Context, uri: Uri): String {
    val stream = context.contentResolver.openInputStream(uri) ?: error("فایل قابل خواندن نیست.")
    return stream.bufferedReader(Charsets.UTF_8).use { reader ->
        val output = StringBuilder()
        val buffer = CharArray(8_192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            require(output.length + count <= MAX_IMPORT_CHARS) { "حجم فایل از سقف ۲۰ مگابایت متنی بیشتر است." }
            output.append(buffer, 0, count)
        }
        output.toString()
    }
}

private fun parseWordImport(text: String, sourceName: String): PendingWordImport {
    val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        try {
            val root = JSONTokener(trimmed).nextValue()
            val array = when (root) {
                is JSONArray -> root
                is JSONObject -> root.optJSONArray("words") ?: throw JSONException("فیلد words پیدا نشد.")
                else -> throw JSONException("ساختار JSON باید آرایه یا شیء دارای words باشد.")
            }
            val records = mutableListOf<WordRecord>()
            val errors = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            val seenIds = mutableSetOf<String>()
            var duplicateRows = 0
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index)
                if (obj == null) {
                    errors += "ردیف ${index + 1}: شیء JSON نیست."
                    continue
                }
                val phrase = obj.optString("text", obj.optString("word", "")).trim()
                val difficulty = parseDifficulty(obj.optString("difficulty"))
                val category = obj.optString("category", "").trim()
                val type = parseType(obj.optString("type", "WORD"))
                if (difficulty == null || type == null) {
                    errors += "ردیف ${index + 1}: سطح دشواری یا نوع عبارت نامعتبر است."
                    continue
                }
                val record = WordRecord(
                    id = obj.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: UUID.randomUUID().toString(),
                    text = phrase,
                    difficulty = difficulty,
                    category = category,
                    type = type,
                    validated = false,
                    active = obj.optBoolean("active", true),
                    sourceVersion = "imported-unreviewed",
                )
                val validation = WordBankRules.validate(record)
                if (validation.isNotEmpty()) {
                    errors += "ردیف ${index + 1}: ${validation.first()}"
                    continue
                }
                val normalized = WordBankRules.normalizePersian(record.text)
                if (normalized in seen || record.id in seenIds) {
                    duplicateRows++
                    continue
                }
                seen += normalized
                seenIds += record.id
                records += record
            }
            return PendingWordImport(records, duplicateRows, 0, errors.size, errors, sourceName)
        } catch (exception: JSONException) {
            throw IllegalArgumentException("فایل JSON معتبر نیست: ${exception.message}")
        }
    }
    val parsed = WordBankCsvCodec.parse(trimmed)
    return PendingWordImport(
        records = parsed.records,
        duplicateRows = parsed.duplicateRows,
        existingDuplicates = 0,
        invalidRows = parsed.invalidRows.size,
        details = parsed.invalidRows.map { "ردیف ${it.line}: ${it.messageFa}" },
        sourceName = sourceName,
    )
}

private fun parseDifficulty(value: String): Difficulty? = Difficulty.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true) || it.labelFa == value
}

private fun parseType(value: String): PhraseType? = PhraseType.entries.firstOrNull {
    it.name.equals(value, ignoreCase = true) || it.labelFa == value
}

private fun exportWords(
    uri: Uri,
    format: String,
    context: android.content.Context,
    repository: PantohubRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    onFailure: (String) -> Unit,
) {
    scope.launch {
        val result = runCatching {
            withContext(Dispatchers.IO) {
                val words = repository.allWordsForExport()
                val content = if (format == "csv") {
                    WordBankCsvCodec.export(words)
                } else {
                    JSONObject().put("schemaVersion", 1).put("words", JSONArray().apply {
                        words.forEach { word ->
                            put(JSONObject()
                                .put("id", word.id)
                                .put("text", word.text)
                                .put("difficulty", word.difficulty.name)
                                .put("category", word.category)
                                .put("type", word.type.name)
                                .put("validated", word.validated)
                                .put("active", word.active)
                                .put("sourceVersion", word.sourceVersion))
                        }
                    }).toString(2)
                }
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                    ?: error("فایل خروجی ساخته نشد.")
                words.size
            }
        }
        result.onSuccess { }.onFailure { onFailure(it.message ?: "خروجی گرفتن ناموفق بود.") }
    }
}
