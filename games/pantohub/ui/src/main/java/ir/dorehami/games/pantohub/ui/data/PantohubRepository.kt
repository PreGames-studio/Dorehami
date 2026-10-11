package ir.dorehami.games.pantohub.ui.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import ir.dorehami.games.pantohub.Difficulty
import ir.dorehami.games.pantohub.GameSettings
import ir.dorehami.games.pantohub.GameState
import ir.dorehami.games.pantohub.GameStateJsonCodec
import ir.dorehami.games.pantohub.PantohubEngine
import ir.dorehami.games.pantohub.PhraseType
import ir.dorehami.games.pantohub.Player
import ir.dorehami.games.pantohub.WordBankCsvCodec
import ir.dorehami.games.pantohub.WordBankRules
import ir.dorehami.games.pantohub.WordRecord
import ir.dorehami.games.pantohub.WordSelectionResult
import java.security.SecureRandom
import java.util.UUID

/** Counts distinguish all rows from active, reviewed words; never displays a target as an actual count. */
data class WordBankSummary(
    val total: Int,
    val active: Int,
    val reviewed: Int,
    val playable: Int,
    val byDifficulty: Map<Difficulty, Int>,
    val byCategory: Map<String, Int>,
)

data class WordImportReport(
    val inserted: Int,
    val duplicates: Int,
    val invalidRows: Int,
    val details: List<String>,
)

/** Local-first storage adapter. Word search and history never require a network connection. */
class PantohubRepository(context: Context) {
    private val appContext = context.applicationContext
    private val database = PantohubDatabase(appContext)
    private val secureRandom = SecureRandom()

    fun initialize() {
        val db = database.writableDatabase
        val seedVersion = metadata(db, SEED_MARKER)
        if (seedVersion == SEED_VERSION) return

        val parsed = appContext.assets.open(SEED_ASSET).bufferedReader(Charsets.UTF_8).use { reader ->
            WordBankCsvCodec.parse(reader.readText())
        }
        check(parsed.invalidRows.isEmpty() && parsed.duplicateRows == 0 && parsed.records.size == SEED_RECORD_COUNT) {
            "Pantohub seed bank must contain exactly $SEED_RECORD_COUNT valid, unique records."
        }
        db.beginTransaction()
        try {
            parsed.records.forEach { record ->
                val reviewedBootstrapWord = record.copy(validated = true, sourceVersion = SEED_VERSION)
                insertWordIfAbsent(db, reviewedBootstrapWord)
            }
            putMetadata(db, SEED_MARKER, SEED_VERSION)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveGame(state: GameState) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            saveGameInTransaction(db, state)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun startNewGame(state: GameState) {
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            db.update(
                "games",
                ContentValues().apply { put("status", STATUS_ABANDONED) },
                "status = ?",
                arrayOf(STATUS_ACTIVE),
            )
            saveGameInTransaction(db, state)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadActiveGame(): GameState? = database.readableDatabase.rawQuery(
        "SELECT state_json FROM games WHERE status = ? ORDER BY updated_at DESC LIMIT 1",
        arrayOf(STATUS_ACTIVE),
    ).use { cursor -> if (cursor.moveToFirst()) GameStateJsonCodec.decode(cursor.getString(0)) else null }

    fun loadFinishedGames(limit: Int = 100): List<GameState> = database.readableDatabase.rawQuery(
        "SELECT state_json FROM games WHERE status = ? ORDER BY updated_at DESC LIMIT ?",
        arrayOf(STATUS_FINISHED, limit.coerceIn(1, 5_000).toString()),
    ).use { cursor -> cursor.map { GameStateJsonCodec.decode(it.getString(0)) } }

    fun loadAllFinishedGames(): List<GameState> = database.readableDatabase.rawQuery(
        "SELECT state_json FROM games WHERE status = ? ORDER BY updated_at DESC",
        arrayOf(STATUS_FINISHED),
    ).use { cursor -> cursor.map { GameStateJsonCodec.decode(it.getString(0)) } }

    fun deleteGame(id: String) {
        database.writableDatabase.delete("games", "id = ?", arrayOf(id))
    }

    fun bankSummary(): WordBankSummary {
        val db = database.readableDatabase
        fun count(where: String, args: Array<String> = emptyArray()): Int = db.rawQuery(
            "SELECT COUNT(*) FROM words $where",
            args,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }

        val difficultyCounts = Difficulty.entries.associateWith { difficulty ->
            count("WHERE active = 1 AND validated = 1 AND difficulty = ?", arrayOf(difficulty.name))
        }
        val categories = linkedMapOf<String, Int>()
        db.rawQuery(
            "SELECT category, COUNT(*) FROM words WHERE active = 1 AND validated = 1 GROUP BY category ORDER BY category",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) categories[cursor.getString(0)] = cursor.getInt(1)
        }
        return WordBankSummary(
            total = count(""),
            active = count("WHERE active = 1"),
            reviewed = count("WHERE validated = 1"),
            playable = count("WHERE active = 1 AND validated = 1"),
            byDifficulty = difficultyCounts,
            byCategory = categories,
        )
    }

    fun listCategories(): List<String> = database.readableDatabase.rawQuery(
        "SELECT DISTINCT category FROM words WHERE active = 1 AND validated = 1 ORDER BY category",
        null,
    ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }

    fun searchWords(
        query: String = "",
        difficulty: Difficulty? = null,
        category: String? = null,
        showInactive: Boolean = false,
        showUnreviewed: Boolean = true,
        limit: Int = 100,
        offset: Int = 0,
    ): List<WordRecord> {
        val clauses = mutableListOf("1 = 1")
        val args = mutableListOf<String>()
        if (!showInactive) clauses += "active = 1"
        if (!showUnreviewed) clauses += "validated = 1"
        if (difficulty != null) {
            clauses += "difficulty = ?"
            args += difficulty.name
        }
        if (!category.isNullOrBlank()) {
            clauses += "category = ?"
            args += category
        }
        val normalizedQuery = WordBankRules.normalizePersian(query)
        if (normalizedQuery.isNotEmpty()) {
            clauses += "instr(normalized_text, ?) > 0"
            args += normalizedQuery
        }
        args += limit.coerceIn(1, 500).toString()
        args += offset.coerceAtLeast(0).toString()
        val sql = "SELECT * FROM words WHERE ${clauses.joinToString(" AND ")} ORDER BY text LIMIT ? OFFSET ?"
        return database.readableDatabase.rawQuery(sql, args.toTypedArray()).use { cursor ->
            cursor.map(::wordFromCursor)
        }
    }

    fun allWordsForExport(): List<WordRecord> = database.readableDatabase.rawQuery(
        "SELECT * FROM words ORDER BY category, difficulty, text",
        null,
    ).use { cursor -> cursor.map(::wordFromCursor) }

    fun pickWord(settings: GameSettings, usedWordIds: Set<String>): WordSelectionResult {
        val clauses = mutableListOf("active = 1", "validated = 1")
        val args = mutableListOf<String>()
        val levels = settings.allowedDifficulties.map { it.name }
        if (levels.isNotEmpty()) {
            clauses += "difficulty IN (${levels.joinToString(",") { "?" }})"
            args += levels
        }
        // Keep the SQL under SQLite's bind-parameter ceiling for unusually large custom taxonomies.
        if (settings.allowedCategories.isNotEmpty() && settings.allowedCategories.size <= 500) {
            val categories = settings.allowedCategories.toList()
            clauses += "category IN (${categories.joinToString(",") { "?" }})"
            args += categories
        }
        val candidateWords = database.readableDatabase.rawQuery(
            "SELECT * FROM words WHERE ${clauses.joinToString(" AND ")}",
            args.toTypedArray(),
        ).use { cursor -> cursor.map(::wordFromCursor) }
        return WordBankRules.chooseWord(
            words = candidateWords,
            settings = settings,
            usedWordIds = usedWordIds,
            randomIndex = { bound -> secureRandom.nextInt(bound) },
        )
    }

    fun addReviewedWord(word: WordRecord): Result<Unit> {
        val validation = WordBankRules.validate(word)
        if (validation.isNotEmpty()) return Result.failure(IllegalArgumentException(validation.joinToString(" ")))
        val db = database.writableDatabase
        val duplicate = db.rawQuery(
            "SELECT id FROM words WHERE normalized_text = ? AND id <> ? LIMIT 1",
            arrayOf(WordBankRules.normalizePersian(word.text), word.id),
        ).use { it.moveToFirst() }
        if (duplicate) return Result.failure(IllegalArgumentException("واژهٔ مشابهی از قبل در بانک وجود دارد."))

        val values = wordValues(word)
        val existing = db.rawQuery("SELECT 1 FROM words WHERE id = ?", arrayOf(word.id)).use { it.moveToFirst() }
        if (existing) db.update("words", values, "id = ?", arrayOf(word.id))
        else {
            val normalizedExists = db.rawQuery(
                "SELECT 1 FROM words WHERE normalized_text = ?",
                arrayOf(WordBankRules.normalizePersian(word.text)),
            ).use { it.moveToFirst() }
            if (normalizedExists) return Result.failure(IllegalArgumentException("واژهٔ مشابهی از قبل در بانک وجود دارد."))
            db.insertOrThrow("words", null, values)
        }
        return Result.success(Unit)
    }

    fun setWordActive(id: String, active: Boolean) {
        database.writableDatabase.update(
            "words",
            ContentValues().apply { put("active", if (active) 1 else 0) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun setWordValidated(id: String, validated: Boolean) {
        database.writableDatabase.update(
            "words",
            ContentValues().apply { put("validated", if (validated) 1 else 0) },
            "id = ?",
            arrayOf(id),
        )
    }

    fun deleteWord(id: String) {
        database.writableDatabase.delete("words", "id = ?", arrayOf(id))
    }

    fun previewImport(records: List<WordRecord>): Int {
        val (existingIds, existingPhrases) = existingWordKeys(database.readableDatabase)
        return records.count { record ->
            record.id in existingIds || WordBankRules.normalizePersian(record.text) in existingPhrases
        }
    }

    fun importCsv(csv: String): WordImportReport {
        val parsed = WordBankCsvCodec.parse(csv)
        return importRecords(
            records = parsed.records,
            duplicates = parsed.duplicateRows,
            invalidRows = parsed.invalidRows.size,
            details = parsed.invalidRows.map { "ردیف ${it.line}: ${it.messageFa}" },
        )
    }

    fun importRecords(
        records: List<WordRecord>,
        duplicates: Int = 0,
        invalidRows: Int = 0,
        details: List<String> = emptyList(),
    ): WordImportReport {
        val db = database.writableDatabase
        var inserted = 0
        var duplicateCount = duplicates
        db.beginTransaction()
        try {
            val (existingIds, existingPhrases) = existingWordKeys(db)
            records.forEach { record ->
                val normalized = WordBankRules.normalizePersian(record.text)
                if (record.id in existingIds || normalized in existingPhrases) {
                    duplicateCount++
                } else {
                    insertWord(db, record.copy(validated = false))
                    existingIds += record.id
                    existingPhrases += normalized
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return WordImportReport(inserted, duplicateCount, invalidRows, details)
    }

    /** Reuses a stable local identity by normalized name; identical names intentionally share one profile. */
    fun resolvePlayer(name: String): Player {
        val trimmed = name.trim()
        val normalized = WordBankRules.normalizePersian(trimmed)
        require(normalized.isNotEmpty()) { "نام بازیکن خالی است." }
        val db = database.writableDatabase
        db.rawQuery(
            "SELECT id FROM players WHERE normalized_name = ? LIMIT 1",
            arrayOf(normalized),
        ).use { cursor ->
            if (cursor.moveToFirst()) return Player(cursor.getString(0), trimmed)
        }
        val id = UUID.randomUUID().toString()
        db.insertOrThrow(
            "players",
            null,
            ContentValues().apply {
                put("id", id)
                put("normalized_name", normalized)
                put("display_name", trimmed)
            },
        )
        return Player(id, trimmed)
    }

    fun close() = database.close()

    private fun saveGameInTransaction(db: SQLiteDatabase, state: GameState) {
        val serialized = GameStateJsonCodec.encode(state)
        val status = if (state.phase.name == "FINISHED") STATUS_FINISHED else STATUS_ACTIVE
        val existing = db.rawQuery(
            "SELECT state_version, status FROM games WHERE id = ?",
            arrayOf(state.id),
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getString(1) else null
        }
        if (existing == null) {
            db.insertOrThrow(
                "games",
                null,
                ContentValues().apply {
                    put("id", state.id)
                    put("name", state.name)
                    put("status", status)
                    put("created_at", state.createdAtEpochMs)
                    put("updated_at", state.finishedAtEpochMs ?: System.currentTimeMillis())
                    put("state_version", state.version)
                    put("state_json", serialized)
                },
            )
        } else if (existing.second != STATUS_ABANDONED && state.version >= existing.first) {
            db.update(
                "games",
                ContentValues().apply {
                    put("name", state.name)
                    put("status", status)
                    put("updated_at", state.finishedAtEpochMs ?: System.currentTimeMillis())
                    put("state_version", state.version)
                    put("state_json", serialized)
                },
                "id = ? AND state_version <= ? AND status <> ?",
                arrayOf(state.id, state.version.toString(), STATUS_ABANDONED),
            )
        }
    }

    private fun insertWord(db: SQLiteDatabase, word: WordRecord) {
        db.insertOrThrow("words", null, wordValues(word))
    }

    /** Seed upgrades only add absent rows; existing seed edits and user-imported words are preserved. */
    private fun insertWordIfAbsent(db: SQLiteDatabase, word: WordRecord) {
        db.insertWithOnConflict("words", null, wordValues(word), SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun wordValues(word: WordRecord) = ContentValues().apply {
        put("id", word.id)
        put("normalized_text", WordBankRules.normalizePersian(word.text))
        put("text", word.text.trim())
        put("difficulty", word.difficulty.name)
        put("category", word.category.trim())
        put("type", word.type.name)
        put("validated", if (word.validated) 1 else 0)
        put("active", if (word.active) 1 else 0)
        put("source_version", word.sourceVersion)
    }

    private fun existingWordKeys(db: SQLiteDatabase): Pair<MutableSet<String>, MutableSet<String>> {
        val ids = mutableSetOf<String>()
        val phrases = mutableSetOf<String>()
        db.rawQuery("SELECT id, normalized_text FROM words", null).use { cursor ->
            while (cursor.moveToNext()) {
                ids += cursor.getString(0)
                phrases += cursor.getString(1)
            }
        }
        return ids to phrases
    }

    private fun metadata(db: SQLiteDatabase, key: String): String? = db.rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf(key),
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun putMetadata(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            "metadata",
            null,
            ContentValues().apply { put("key", key); put("value", value) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    private fun wordFromCursor(cursor: Cursor): WordRecord = WordRecord(
        id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
        text = cursor.getString(cursor.getColumnIndexOrThrow("text")),
        difficulty = Difficulty.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("difficulty"))),
        category = cursor.getString(cursor.getColumnIndexOrThrow("category")),
        type = PhraseType.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("type"))),
        validated = cursor.getInt(cursor.getColumnIndexOrThrow("validated")) != 0,
        active = cursor.getInt(cursor.getColumnIndexOrThrow("active")) != 0,
        sourceVersion = cursor.getString(cursor.getColumnIndexOrThrow("source_version")),
    )

    private fun <T> Cursor.map(transform: (Cursor) -> T): List<T> {
        val cursor = this
        val rows = mutableListOf<T>()
        while (cursor.moveToNext()) rows += transform(cursor)
        return rows
    }

    private class PantohubDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE words (" +
                    "id TEXT PRIMARY KEY NOT NULL," +
                    "normalized_text TEXT NOT NULL UNIQUE," +
                    "text TEXT NOT NULL," +
                    "difficulty TEXT NOT NULL," +
                    "category TEXT NOT NULL," +
                    "type TEXT NOT NULL," +
                    "validated INTEGER NOT NULL DEFAULT 0," +
                    "active INTEGER NOT NULL DEFAULT 1," +
                    "source_version TEXT NOT NULL DEFAULT ''" +
                    ")",
            )
            db.execSQL("CREATE INDEX words_filter_idx ON words(active, validated, difficulty, category)")
            db.execSQL("CREATE TABLE games (id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, status TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, state_version INTEGER NOT NULL, state_json TEXT NOT NULL)")
            db.execSQL("CREATE INDEX games_status_updated_idx ON games(status, updated_at DESC)")
            db.execSQL("CREATE TABLE players (id TEXT PRIMARY KEY NOT NULL, normalized_name TEXT NOT NULL UNIQUE, display_name TEXT NOT NULL)")
            db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Schema migrations are added here before the database version is incremented.
        }
    }

    private companion object {
        const val DB_NAME = "pantohub-local.db"
        const val DB_VERSION = 1
        const val SEED_ASSET = "pantohub-words-fa.csv"
        const val SEED_MARKER = "bootstrap_words"
        const val SEED_VERSION = "curated-bootstrap-v2"
        const val SEED_RECORD_COUNT = 1_000
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_FINISHED = "FINISHED"
        const val STATUS_ABANDONED = "ABANDONED"
    }
}
