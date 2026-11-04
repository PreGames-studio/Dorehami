package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.LoggedAction
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Standing
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

    private fun emptyOptions() = buildJsonObject { put("targetPoints", 7) }

    @Test
    fun `record round-trips through JSON without a result`() {
        val record = MatchRecord(
            gameId = HokmModule.ID,
            seed = 11L,
            options = emptyOptions(),
            players = players,
            actions = emptyList(),
            result = null,
            digest = "deadbeef",
            schema = 1,
        )
        val back = matchRecordFromJson(matchRecordToJson(record))!!
        assertEquals(record, back)
    }

    @Test
    fun `record round-trips through JSON with a finished result`() {
        val record = MatchRecord(
            gameId = HokmModule.ID,
            seed = 11L,
            options = emptyOptions(),
            players = players,
            actions = listOf(
                LoggedAction(0, players[0], "choose_hokm", buildJsonObject { put("suit", "hearts") }, 1000L),
            ),
            result = MatchResult(listOf(
                Standing(listOf(players[0]), 1, 9),
                Standing(listOf(players[1]), 2, 2),
            )),
            digest = "digest",
            schema = 1,
        )
        val back = matchRecordFromJson(matchRecordToJson(record))!!
        assertEquals(record, back)
    }

    @Test
    fun `unknown schema is rejected`() {
        val record = MatchRecord(
            gameId = HokmModule.ID,
            seed = 11L,
            options = emptyOptions(),
            players = players,
            actions = emptyList(),
            result = null,
            digest = "x",
            schema = 2,
        )
        assertNull(matchRecordFromJson(matchRecordToJson(record)))
    }

    @Test
    fun `wrong game id is rejected`() {
        val tampered = buildJsonObject {
            put("gameId", "chess")
            put("seed", 1L)
            put("options", emptyOptions())
            put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
            put("actions", buildJsonArray {})
            put("digest", "x")
            put("schema", 1)
        }
        assertNull(matchRecordFromJson(tampered))
    }

    @Test
    fun `truncated action log is rejected`() {
        val full = matchRecordToJson(
            MatchRecord(
                gameId = HokmModule.ID,
                seed = 1L,
                options = emptyOptions(),
                players = players,
                actions = listOf(
                    LoggedAction(0, players[0], "choose_hokm", buildJsonObject { put("suit", "hearts") }, 1000L),
                ),
                result = null,
                digest = "x",
                schema = 1,
            )
        )
        val truncated = buildJsonObject {
            put("gameId", HokmModule.ID)
            put("seed", 1L)
            put("options", emptyOptions())
            put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
            put("actions", buildJsonArray {})
            put("digest", "x")
            put("schema", 1)
        }
        assertNotNull(matchRecordFromJson(full))
        val back = matchRecordFromJson(truncated)
        assertNotNull(back)
        assertEquals(emptyList<LoggedAction>(), back.actions)
    }
}
