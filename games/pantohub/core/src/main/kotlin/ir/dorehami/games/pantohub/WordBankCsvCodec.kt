package ir.dorehami.games.pantohub

import java.security.MessageDigest

/** CSV import keeps every untrusted row unreviewed until it is explicitly approved in the app. */
object WordBankCsvCodec {
    private val requiredHeaders = setOf("text", "difficulty", "category")

    fun export(words: Iterable<WordRecord>): String = buildString {
        appendLine("id,text,difficulty,category,type,validated,active,sourceVersion")
        words.forEach { word ->
            append(listOf(
                word.id,
                word.text,
                word.difficulty.name,
                word.category,
                word.type.name,
                word.validated.toString(),
                word.active.toString(),
                word.sourceVersion,
            ).joinToString(",") { escape(it) })
            append('\n')
        }
    }

    fun parse(csv: String): CsvImportResult {
        val rows = parseRows(csv.removePrefix("\uFEFF")) ?: return CsvImportResult(
            records = emptyList(),
            invalidRows = listOf(CsvRowError(1, "ساختار نقل‌قول‌های فایل CSV ناقص است.")),
            duplicateRows = 0,
        )
        if (rows.isEmpty()) return CsvImportResult(emptyList(), listOf(CsvRowError(1, "فایل CSV خالی است.")), 0)

        val headers = rows.first().mapIndexed { index, value -> value.trim().lowercase() to index }.toMap()
        val missing = requiredHeaders - headers.keys
        if (missing.isNotEmpty()) {
            return CsvImportResult(
                emptyList(),
                listOf(CsvRowError(1, "ستون‌های ضروری پیدا نشد: ${missing.joinToString()}")),
                0,
            )
        }

        val records = mutableListOf<WordRecord>()
        val errors = mutableListOf<CsvRowError>()
        val seenPhrases = mutableSetOf<String>()
        val seenIds = mutableSetOf<String>()
        var duplicates = 0

        rows.drop(1).forEachIndexed { rowIndex, row ->
            val lineNumber = rowIndex + 2
            if (row.all { it.isBlank() }) return@forEachIndexed
            fun field(name: String): String = row.getOrNull(headers[name] ?: -1)?.trim().orEmpty()

            val text = field("text")
            val difficulty = parseDifficulty(field("difficulty"))
            val category = field("category")
            val type = parseType(field("type").ifBlank { "WORD" })
            if (difficulty == null) {
                errors += CsvRowError(lineNumber, "سطح دشواری ناشناخته است.")
                return@forEachIndexed
            }
            if (type == null) {
                errors += CsvRowError(lineNumber, "نوع عبارت ناشناخته است.")
                return@forEachIndexed
            }
            val normalized = WordBankRules.normalizePersian(text)
            val idFromFile = field("id")
            val id = idFromFile.ifBlank { stableId(normalized) }
            val record = WordRecord(
                id = id,
                text = text,
                difficulty = difficulty,
                category = category,
                type = type,
                validated = false,
                active = field("active").let { it.isBlank() || it.equals("true", ignoreCase = true) },
                sourceVersion = field("sourceVersion"),
            )
            val validation = WordBankRules.validate(record)
            if (validation.isNotEmpty()) {
                errors += CsvRowError(lineNumber, validation.first())
                return@forEachIndexed
            }
            if (normalized in seenPhrases || id in seenIds) {
                duplicates++
                return@forEachIndexed
            }
            seenPhrases += normalized
            seenIds += id
            records += record
        }
        return CsvImportResult(records, errors, duplicates)
    }

    private fun parseDifficulty(value: String): Difficulty? = Difficulty.entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) || it.labelFa == value
    }

    private fun parseType(value: String): PhraseType? = PhraseType.entries.firstOrNull {
        it.name.equals(value, ignoreCase = true) || it.labelFa == value
    }

    private fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }

    private fun stableId(normalizedText: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(normalizedText.toByteArray(Charsets.UTF_8))
        return "word-" + digest.take(12).joinToString("") { byte -> "%02x".format(byte) }
    }

    /** RFC-4180-compatible enough for normal spreadsheet CSV, including escaped quotes/newlines. */
    private fun parseRows(input: String): List<List<String>>? {
        val result = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < input.length) {
            val char = input[index]
            when {
                quoted && char == '"' && input.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                char == '"' && quoted -> quoted = false
                char == '"' && cell.isEmpty() -> quoted = true
                char == ',' && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                (char == '\n' || char == '\r') && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                    result += row.toList()
                    row.clear()
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                }
                else -> cell.append(char)
            }
            index++
        }
        if (quoted) return null
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            result += row.toList()
        }
        return result
    }
}

data class CsvRowError(val line: Int, val messageFa: String)

data class CsvImportResult(
    val records: List<WordRecord>,
    val invalidRows: List<CsvRowError>,
    val duplicateRows: Int,
)
