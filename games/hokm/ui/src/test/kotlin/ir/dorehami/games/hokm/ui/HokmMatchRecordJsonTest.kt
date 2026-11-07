package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.LoggedAction
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Standing
import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Pure-JVM tests for the solo-match JSON converters (HK-011). No Android involved.
 */
class HokmMatchRecordJsonTest {

    private val players = listOf(PlayerId("p_me"), PlayerId("p_bot1"))

    private fun emptyOptions() = buildJsonObject { put("targetPoints", JsonPrimitive(7)) }

    @Test
    fun `record round-trips through JSON without a result`() {
        val record = MatchRecord(
            gameId = GameId(HokmModule.ID),
            seed = 11L,
            options = emptyOptions(),
            players = players,
            actions = emptyList(),
            result = null,
            digest = "deadbeef",
            schema = 1,
        )
        val back = record.toJson().matchRecordFromJson()!!
        assertEquals(record, back)
    }

    @Test
    fun `record round-trips through JSON with a finished result`() {
        val record = MatchRecord(
            gameId = GameId(HokmModule.ID),
            seed = 11L,
            options = emptyOptions(),
            players = players,
            actions = listOf(
                LoggedAction(0, players[0], "choose_hokm", buildJsonObject { put("suit", JsonPrimitive("hearts")) }, 1000L),
            ),
            result = MatchResult(listOf(
                Standing(listOf(players[0]), 1, 9),
                Standing(listOf(players[1]), 2, 2),
            )),
            digest = "digest",
            schema = 1,
        )
        val back = record.toJson().matchRecordFromJson()!!
        assertEquals(record, back)
    }

    @Test
    fun `unknown schema is rejected`() {
        // A file written by a newer build has the same shape with schema 2. The loader must
        // refuse it instead of guessing. Built by hand because `toJson()` always stamps the
        // schema this build writes.
        val newer = buildJsonObject {
            put("gameId", JsonPrimitive(HokmModule.ID))
            put("seed", JsonPrimitive(11L))
            put("options", emptyOptions())
            put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
            put("actions", buildJsonArray {})
            put("digest", JsonPrimitive("x"))
            put("schema", JsonPrimitive(2))
        }
        assertNull(newer.matchRecordFromJson())
    }

    @Test
    fun `wrong game id is rejected`() {
        val tampered = buildJsonObject {
            put("gameId", JsonPrimitive("chess"))
            put("seed", JsonPrimitive(1L))
            put("options", emptyOptions())
            put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
            put("actions", buildJsonArray {})
            put("digest", JsonPrimitive("x"))
            put("schema", JsonPrimitive(1))
        }
        assertNull(tampered.matchRecordFromJson())
    }

    @Test
    fun `truncated action log round-trips`() {
        val full = MatchRecord(
            gameId = GameId(HokmModule.ID),
            seed = 1L,
            options = emptyOptions(),
            players = players,
            actions = listOf(
                LoggedAction(0, players[0], "choose_hokm", buildJsonObject { put("suit", JsonPrimitive("hearts")) }, 1000L),
            ),
            result = null,
            digest = "x",
            schema = 1,
        )
        val truncatedJson = buildJsonObject {
            put("gameId", JsonPrimitive(HokmModule.ID))
            put("seed", JsonPrimitive(1L))
            put("options", emptyOptions())
            put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
            put("actions", buildJsonArray {})
            put("digest", JsonPrimitive("x"))
            put("schema", JsonPrimitive(1))
        }
        assertNotNull(full.toJson().matchRecordFromJson())
        val back = truncatedJson.matchRecordFromJson()
        assertNotNull(back)
        assertEquals(emptyList<LoggedAction>(), back.actions)
    }
}
