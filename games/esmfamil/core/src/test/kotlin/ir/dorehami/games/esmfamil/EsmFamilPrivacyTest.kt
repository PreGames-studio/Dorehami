package ir.dorehami.games.esmfamil

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Hidden information, tested structurally (AGENTS.md rule 4): during the writing phase another
 * player's answer must not exist anywhere in what I can see — not as an empty string, not as a key.
 */
class EsmFamilPrivacyTest {

    @Test
    fun `the public view carries no answers while the sheets are being written`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheetOf("name" to "${letter}ارا"))

        val public = session.publicView()
        assertNull(public[EsmFamilSession.Keys.SHEET], "no sheet key before the reveal")
        assertNull(public[EsmFamilSession.Keys.ROUND_POINTS], "no points key before the reveal")
        assertFalse(
            public.toString().contains("${letter}ارا"),
            "sara's answer must not be readable by everybody",
        )
    }

    @Test
    fun `my own sheet is mine alone`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheetOf("name" to "${letter}ارا"))
        session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.sheetOf("name" to "${letter}عید"))

        val saraView = session.privateView(EsmFamilTestSupport.sara)
        val mine = saraView[EsmFamilSession.Keys.MINE] as JsonObject
        assertEquals("${letter}ارا", mine["name"]!!.jsonPrimitive.content)
        assertFalse(
            saraView.toString().contains("${letter}عید"),
            "reza's answer must not leak into sara's private view",
        )

        val rezaMine = session.privateView(EsmFamilTestSupport.reza)[EsmFamilSession.Keys.MINE] as JsonObject
        assertEquals("${letter}عید", rezaMine["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `opening the sheets makes every answer public on purpose`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheetOf("name" to "${letter}ارا"))
        session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.sheetOf("name" to "${letter}عید"))
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.finishRound())

        val public = session.publicView()
        val sheet = public[EsmFamilSession.Keys.SHEET] as JsonObject
        val saraRow = sheet[EsmFamilTestSupport.sara.value] as JsonObject
        val rezaRow = sheet[EsmFamilTestSupport.reza.value] as JsonObject
        assertEquals("${letter}ارا", saraRow["name"]!!.jsonPrimitive.content)
        assertEquals("${letter}عید", rezaRow["name"]!!.jsonPrimitive.content)
        assertTrue(public.containsKey(EsmFamilSession.Keys.ROUND_POINTS))
    }

    @Test
    fun `the seat number in my private view is mine`() {
        val session = EsmFamilTestSupport.session()
        assertEquals(0, (session.privateView(EsmFamilTestSupport.sara)[EsmFamilSession.Keys.SEAT] as JsonPrimitive).content.toInt())
        assertEquals(2, (session.privateView(EsmFamilTestSupport.mina)[EsmFamilSession.Keys.SEAT] as JsonPrimitive).content.toInt())
    }
}
