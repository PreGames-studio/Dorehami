package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordBankRulesTest {
    @Test
    fun `normalization catches Arabic Persian spelling and whitespace duplicates`() {
        val first = sample("1", "كِتاب\u200cخانه")
        val second = sample("2", "کتاب خانه")
        assertEquals(
            WordBankRules.normalizePersian(first.text),
            WordBankRules.normalizePersian(second.text),
        )
        assertEquals(1, WordBankRules.duplicateGroups(listOf(first, second)).size)
    }

    @Test
    fun `selection excludes used and unreviewed words and reports exhaustion`() {
        val settings = GameSettings(allowedDifficulties = setOf(Difficulty.EASY))
        val words = listOf(
            sample("used", "گربه"),
            sample("unreviewed", "چراغ", validated = false),
            sample("ready", "دوچرخه"),
            sample("hard", "از کاه کوه ساختن", difficulty = Difficulty.HARD),
        )
        val picked = WordBankRules.chooseWord(words, settings, setOf("used"), { 0 })
        assertInstanceOf(WordSelectionResult.Selected::class.java, picked)
        assertEquals("ready", (picked as WordSelectionResult.Selected).word.id)
        assertEquals(WordSelectionResult.Empty, WordBankRules.chooseWord(words, settings, setOf("used", "ready"), { 0 }))
        assertEquals(
            WordSelectionResult.InvalidRandomIndex,
            WordBankRules.chooseWord(words, settings, emptySet(), { it }, requiredDifficulty = Difficulty.EASY),
        )
    }

    @Test
    fun `selection respects category filters and reports an exhausted pool`() {
        val settings = GameSettings(allowedCategories = setOf(WordCategories.ANIMALS))
        val words = listOf(
            sample("animal", "گربه", category = WordCategories.ANIMALS),
            sample("food", "برنج", category = WordCategories.FOOD),
        )
        val selected = WordBankRules.chooseWord(words, settings, emptySet(), { 0 }) as WordSelectionResult.Selected
        assertEquals("animal", selected.word.id)
        assertEquals(
            WordSelectionResult.Empty,
            WordBankRules.chooseWord(words, settings, setOf("animal"), { 0 }),
        )
    }

    @Test
    fun `CSV round trip supports quoted values and imported rows need review`() {
        val source = sample("id,1", "گربه، روی بام", category = "custom,category")
        val exported = WordBankCsvCodec.export(listOf(source))
        val parsed = WordBankCsvCodec.parse(exported)

        assertTrue(parsed.invalidRows.isEmpty())
        assertEquals(1, parsed.records.size)
        assertEquals(source.text, parsed.records.single().text)
        assertEquals(source.category, parsed.records.single().category)
        assertFalse(parsed.records.single().validated)
    }

    @Test
    fun `CSV parser reports duplicate phrases and identifiers`() {
        val csv = "id,text,difficulty,category,type\nshared,گربه,EASY,animals,WORD\nother,گربه ,EASY,animals,WORD\nshared,دوچرخه,EASY,objects,WORD\n"
        val parsed = WordBankCsvCodec.parse(csv)
        assertEquals(1, parsed.records.size)
        assertEquals(2, parsed.duplicateRows)
        assertTrue(parsed.invalidRows.isEmpty())
    }

    @Test
    fun `CSV parser reports malformed rows`() {
        val csv = "text,difficulty,category,type\n,MEDIUM,food,WORD\n"
        val parsed = WordBankCsvCodec.parse(csv)
        assertTrue(parsed.records.isEmpty())
        assertEquals(1, parsed.invalidRows.size)
    }

    private fun sample(
        id: String,
        text: String,
        category: String = WordCategories.ANIMALS,
        difficulty: Difficulty = Difficulty.EASY,
        validated: Boolean = true,
    ) = WordRecord(
        id = id,
        text = text,
        difficulty = difficulty,
        category = category,
        validated = validated,
    )
}
