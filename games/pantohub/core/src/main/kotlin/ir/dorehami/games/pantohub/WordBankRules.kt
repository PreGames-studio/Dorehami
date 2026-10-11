package ir.dorehami.games.pantohub

import java.text.Normalizer
import java.util.Locale

/** Validation, duplicate detection, and selection rules shared by the bank and game engine. */
object WordBankRules {
    fun normalizePersian(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('\u0640', ' ')
            .replace('\u200c', ' ')
            .replace('\u200f', ' ')
            .replace('\u200e', ' ')
            .lowercase(Locale.ROOT)
        return normalized
            .replace(DIACRITICS, "")
            .replace(PUNCTUATION, " ")
            .replace(WHITESPACE, " ")
            .trim()
    }

    /** Returns human-readable Persian validation errors; no word is silently repaired. */
    fun validate(word: WordRecord): List<String> = buildList {
        if (word.id.isBlank()) add("شناسهٔ واژه خالی است.")
        val text = word.text.trim()
        if (text.isEmpty()) add("متن واژه یا عبارت خالی است.")
        if (text.codePointCount(0, text.length) > 120) add("متن واژه بیش از اندازه طولانی است.")
        if (text.any { Character.isISOControl(it) }) add("متن واژه نویسهٔ کنترلی دارد.")
        if (text.none { it.isPersianLetter() }) add("متن باید دست‌کم یک حرف فارسی داشته باشد.")
        if (normalizePersian(text).isEmpty()) add("متن پس از یکسان‌سازی خالی است.")
        if (word.category.isBlank()) add("دستهٔ موضوعی واژه مشخص نشده است.")
    }

    fun duplicateGroups(words: Iterable<WordRecord>): List<List<WordRecord>> = words
        .filter { normalizePersian(it.text).isNotEmpty() }
        .groupBy { normalizePersian(it.text) }
        .values
        .filter { it.size > 1 }

    /** Computes the exact eligible pool first, so selection cannot loop forever on an exhausted filter. */
    fun eligibleWords(
        words: Iterable<WordRecord>,
        settings: GameSettings,
        usedWordIds: Set<String>,
        requiredDifficulty: Difficulty? = null,
    ): List<WordRecord> = words.asSequence()
        .filter { it.active && it.validated }
        .filter { it.id !in usedWordIds }
        .filter { (requiredDifficulty ?: it.difficulty) == it.difficulty }
        .filter { it.difficulty in settings.allowedDifficulties }
        .filter { settings.allowedCategories.isEmpty() || it.category in settings.allowedCategories }
        .filter { validate(it).isEmpty() }
        .toList()

    /** [randomIndex] is injected by the caller; game rules themselves never access system randomness. */
    fun chooseWord(
        words: Iterable<WordRecord>,
        settings: GameSettings,
        usedWordIds: Set<String>,
        randomIndex: (Int) -> Int,
        requiredDifficulty: Difficulty? = null,
    ): WordSelectionResult {
        val pool = eligibleWords(words, settings, usedWordIds, requiredDifficulty)
        if (pool.isEmpty()) return WordSelectionResult.Empty
        val index = randomIndex(pool.size)
        if (index !in pool.indices) return WordSelectionResult.InvalidRandomIndex
        return WordSelectionResult.Selected(pool[index], pool.size)
    }

    private fun Char.isPersianLetter(): Boolean = isLetter() &&
        (this in '\u0600'..'\u06ff' || this in '\u0750'..'\u077f' || this in '\u08a0'..'\u08ff')

    private val DIACRITICS = Regex("[\\u064b-\\u065f\\u0670\\u06d6-\\u06ed]")
    private val PUNCTUATION = Regex("[\\p{Punct}،؛؟!…«»٫٬]+")
    private val WHITESPACE = Regex("\\s+")
}

sealed interface WordSelectionResult {
    data class Selected(val word: WordRecord, val eligibleCount: Int) : WordSelectionResult
    data object Empty : WordSelectionResult
    data object InvalidRandomIndex : WordSelectionResult
}
