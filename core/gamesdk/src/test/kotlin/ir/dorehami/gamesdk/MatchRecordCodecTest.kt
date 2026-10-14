package ir.dorehami.gamesdk

import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM tests for the shared record codec (HK-011): the save/restore and `GET /matches` format. */
class MatchRecordCodecTest {

    private fun record(finished: Boolean = false) = MatchRecord(
        gameId = GameId("hokm"),
        seed = 42L,
        options = buildJsonObject { put("targetPoints", 7) },
        players = listOf(PlayerId("p0"), PlayerId("p1"), PlayerId("p2"), PlayerId("p3")),
        actions = listOf(
            LoggedAction(0, PlayerId("p0"), "choose_hokm", buildJsonObject { put("suit", "spades") }, 1000L),
            LoggedAction(1, PlayerId("p3"), "play_card", buildJsonObject { put("card", "AS") }, 1250L),
        ),
        result = if (finished) MatchResult(listOf(Standing(listOf(PlayerId("p0"), PlayerId("p2")), 1, 7), Standing(listOf(PlayerId("p1"), PlayerId("p3")), 2, 3))) else null,
        digest = "abc123",
    )

    @Test
    fun `encode-decode round trip preserves every field`() {
        for (finished in listOf(false, true)) {
            val r = record(finished)
            val decoded = assertIs<RecordDecode.Decoded>(MatchRecordCodec.decode(MatchRecordCodec.encode(r).toString()))
            assertEquals(r, decoded.record, "finished=$finished must survive the storage format")
        }
    }

    @Test
    fun `a blob from a newer schema is refused safely`() {
        val json = MatchRecordCodec.encode(record()).toString().replace("\"schema\":1", "\"schema\":2")
        val bad = assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode(json))
        assertEquals("save.unsupported_schema", bad.code)
        assertTrue(bad.messageFa.isNotBlank(), "the safe error carries a Persian message")
    }

    @Test
    fun `corrupt text and wrong shapes are refused, never thrown`() {
        assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode("{not json"))
        assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode("[]"))
        assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode("{}"))
        val noPlayers = MatchRecordCodec.encode(record()).toString().replace("\"players\"", "\"playersX\"")
        assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode(noPlayers))
    }

    @Test
    fun `an action missing fields refuses the whole save`() {
        val broken = MatchRecordCodec.encode(record()).toString().replace("\"atMs\"", "\"atMsX\"")
        val bad = assertIs<RecordDecode.Invalid>(MatchRecordCodec.decode(broken))
        assertEquals("save.corrupt", bad.code)
    }
}
