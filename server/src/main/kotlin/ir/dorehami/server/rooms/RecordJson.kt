package ir.dorehami.server.rooms

import ir.dorehami.gamesdk.MatchRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Full record as served by `GET /v1/matches/{code}` (docs/ARCHITECTURE.md 4.4). */
fun MatchRecord.toJson(): JsonObject = buildJsonObject {
    put("schema", schema)
    put("gameId", gameId.value)
    put("seed", seed)
    put("options", options)
    put("players", buildJsonArray { players.forEach { add(JsonPrimitive(it.value)) } })
    put("digest", digest)
    put(
        "actions",
        buildJsonArray {
            actions.forEach { a ->
                add(buildJsonObject { put("seq", a.seq); put("player", a.player.value); put("type", a.type); put("payload", a.payload); put("atMs", a.atMs) })
            }
        },
    )
    result?.let { r ->
        put(
            "standings",
            buildJsonArray {
                r.standings.forEach { st ->
                    add(buildJsonObject {
                        put("rank", st.rank); put("score", st.score)
                        put("members", buildJsonArray { st.members.forEach { add(JsonPrimitive(it.value)) } })
                    })
                }
            },
        )
    }
}
