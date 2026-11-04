package ir.dorehami.games.hokm.ui

import android.content.Context
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.LoggedAction
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Standing
import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.runCatching

/**
 * The solo match is persisted as one JSON file ([MatchRecord]: seed + action log + result +
 * digest) so it survives killing the process and resumes exactly where it was (HK-011).
 *
 * Nothing here touches the rules: [GameEngine.replay] rebuilds the session deterministically from
 * the record. A tampered or unknown-schema record is treated as "no record" and the match
 * restarts fresh — a safe error, never a corrupted table.
 */
class HokmMatchStore(private val context: Context) {
    private val file: File = File(context.filesDir, SOLO_MATCH_FILE)

    fun save(record: MatchRecord) {
        file.writeText(record.toJson().toString(), Charsets.UTF_8)
    }

    /**
     * Loads the saved record, or `null` when there is nothing to resume. An invalid file is
     * deleted and `null` is returned.
     */
    fun load(): MatchRecord? {
        if (!file.exists()) return null
        val json = runCatching { Json {}.parseToJsonElement(file.readText(Charsets.UTF_8)) }.getOrNull()
        val obj = (json as? JsonObject) ?: return run { file.delete(); null }
        return obj.matchRecordFromJson() ?: run { file.delete(); null }
    }

    fun clear() {
        file.delete()
    }
}

private const val SOLO_MATCH_FILE = "hokm_solo_match.json"
private const val SOLO_MATCH_SCHEMA = 1

internal fun MatchRecord.toJson(): JsonObject = buildJsonObject {
    put("gameId", JsonPrimitive(gameId.value))
    put("seed", JsonPrimitive(seed))
    put("options", options)
    put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
    put("actions", buildJsonArray { actions.forEach { a ->
        add(buildJsonObject {
            put("seq", JsonPrimitive(a.seq))
            put("player", JsonPrimitive(a.player.value))
            put("type", JsonPrimitive(a.type))
            put("payload", a.payload)
            put("atMs", JsonPrimitive(a.atMs))
        })
    }})
    result?.let { put("result", it.matchResultToJson()) }
    put("digest", JsonPrimitive(digest))
    put("schema", JsonPrimitive(SOLO_MATCH_SCHEMA))
}

internal fun MatchResult.matchResultToJson(): JsonObject = buildJsonObject {
    put("standings", buildJsonArray { standings.forEach { s ->
        add(buildJsonObject {
            put("members", buildJsonArray { s.members.forEach { add(JsonPrimitive(it.value)) } })
            put("rank", JsonPrimitive(s.rank))
            put("score", JsonPrimitive(s.score))
        })
    }})
}

internal fun JsonObject.matchRecordFromJson(): MatchRecord? {
    if (schemaInt() != SOLO_MATCH_SCHEMA) return null
    val gameIdText = primitiveText(this["gameId"]) ?: return null
    if (gameIdText != HokmModule.ID) return null
    val seed = primitiveLong(this["seed"]) ?: return null
    val options = this["options"] as? JsonObject ?: return null
    val players = playerIds(this["players"]) ?: return null
    val actions = loggedActions(this["actions"]) ?: return null
    val result = (this["result"] as? JsonObject)?.matchResultFromJson()
    val digest = primitiveText(this["digest"]) ?: return null
    return MatchRecord(
        gameId = GameId(gameIdText),
        seed = seed,
        options = options,
        players = players,
        actions = actions,
        result = result,
        digest = digest,
        schema = SOLO_MATCH_SCHEMA,
    )
}

private fun JsonObject.matchResultFromJson(): MatchResult? {
    val arr = (this["standings"] as? JsonArray) ?: return null
    val standings = mutableListOf<Standing>()
    for (e in arr) {
        val o = (e as? JsonObject) ?: return null
        val members = playerIds(o["members"]) ?: return null
        val rank = primitiveInt(o["rank"]) ?: return null
        val score = primitiveInt(o["score"]) ?: return null
        standings += Standing(members, rank, score)
    }
    return MatchResult(standings)
}

private fun schemaInt(obj: JsonObject): Int = primitiveInt(obj["schema"]) ?: -1

private fun primitiveText(el: kotlinx.serialization.json.JsonElement?): String? = (el as? JsonPrimitive)?.content

private fun primitiveInt(el: kotlinx.serialization.json.JsonElement?): Int? =
    (el as? JsonPrimitive)?.content?.toIntOrNull()

private fun primitiveLong(el: kotlinx.serialization.json.JsonElement?): Long? =
    (el as? JsonPrimitive)?.content?.toLongOrNull()

private fun playerIds(el: kotlinx.serialization.json.JsonElement?): List<PlayerId>? {
    val arr = (el as? JsonArray) ?: return null
    val ids = mutableListOf<PlayerId>()
    for (e in arr) {
        val v = (e as? JsonPrimitive)?.content ?: return null
        ids += PlayerId(v)
    }
    return ids
}

private fun loggedActions(el: kotlinx.serialization.json.JsonElement?): List<LoggedAction>? {
    val arr = (el as? JsonArray) ?: return null
    val actions = mutableListOf<LoggedAction>()
    for (e in arr) {
        val o = (e as? JsonObject) ?: return null
        val seq = primitiveLong(o["seq"]) ?: return null
        val player = primitiveText(o["player"]) ?: return null
        val type = primitiveText(o["type"]) ?: return null
        val payload = (o["payload"] as? JsonObject) ?: return null
        val atMs = primitiveLong(o["atMs"]) ?: return null
        actions += LoggedAction(seq, PlayerId(player), type, payload, atMs)
    }
    return actions
}
