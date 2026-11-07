package ir.dorehami.games.esmfamil

import ir.dorehami.protocol.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pure scoring rules of the sheet: normalisation, the letter test, and 10/5/0 points. */
class EsmFamilSheetTest {

    @Test
    fun `arabic and persian spellings of the same answer normalise alike`() {
        assertEquals(EsmFamilSheet.normalise("كاظم"), EsmFamilSheet.normalise("کاظم"))
        assertEquals(EsmFamilSheet.normalise("ياسمين"), EsmFamilSheet.normalise("یاسمین"))
        assertEquals(EsmFamilSheet.normalise("  سارا  "), "سارا")
        assertEquals(EsmFamilSheet.normalise("سید\u200cمحمد"), "سیدمحمد")
        // Alef with madda is the same letter as alef for the game's purposes.
        assertEquals(EsmFamilSheet.normalise("آرش"), EsmFamilSheet.normalise("ارش"))
    }

    @Test
    fun `only answers that start with the round letter count`() {
        assertTrue(EsmFamilSheet.startsWithLetter("سارا", 'س'))
        assertTrue(EsmFamilSheet.startsWithLetter("آرش", 'ا'))
        assertFalse(EsmFamilSheet.startsWithLetter("رضا", 'س'))
        assertFalse(EsmFamilSheet.startsWithLetter("   ", 'س'))
    }

    @Test
    fun `unique answer pays ten, a shared answer pays five, a wrong letter pays nothing`() {
        val sara = PlayerId("p_sara")
        val reza = PlayerId("p_reza")
        val mina = PlayerId("p_mina")
        val points = EsmFamilSheet.scoreRound(
            letter = 'س',
            categories = listOf("name"),
            answers = mapOf(
                sara to mapOf("name" to "سارا"),
                reza to mapOf("name" to "سارا"),
                mina to mapOf("name" to "سعید"),
            ),
        )
        assertEquals(5, points[sara]!!["name"])
        assertEquals(5, points[reza]!!["name"])
        assertEquals(10, points[mina]!!["name"])
    }

    @Test
    fun `an empty cell and a wrong-letter cell both pay zero`() {
        val sara = PlayerId("p_sara")
        val reza = PlayerId("p_reza")
        val points = EsmFamilSheet.scoreRound(
            letter = 'س',
            categories = listOf("name", "city"),
            answers = mapOf(
                sara to mapOf("name" to "سارا", "city" to ""),
                reza to mapOf("name" to "رضا", "city" to "سمنان"),
            ),
        )
        assertEquals(10, points[sara]!!["name"])
        assertEquals(0, points[sara]!!["city"])
        assertEquals(0, points[reza]!!["name"])
        assertEquals(10, points[reza]!!["city"])
    }

    @Test
    fun `spacing differences do not let two players dodge the shared penalty`() {
        val sara = PlayerId("p_sara")
        val reza = PlayerId("p_reza")
        val points = EsmFamilSheet.scoreRound(
            letter = 'س',
            categories = listOf("name"),
            answers = mapOf(
                sara to mapOf("name" to "سید محمد"),
                reza to mapOf("name" to "سیدمحمد"),
            ),
        )
        assertEquals(5, points[sara]!!["name"])
        assertEquals(5, points[reza]!!["name"])
    }

    @Test
    fun `a full sheet is recognised`() {
        assertTrue(EsmFamilSheet.completedSheet(mapOf("name" to 10, "city" to 5)))
        assertFalse(EsmFamilSheet.completedSheet(mapOf("name" to 10, "city" to 0)))
        assertFalse(EsmFamilSheet.completedSheet(emptyMap()))
    }
}
