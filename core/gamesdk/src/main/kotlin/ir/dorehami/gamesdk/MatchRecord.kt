package ir.dorehami.gamesdk

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * تنها قالبِ ذخیره‌سازیِ کل پلتفرم: یک دست = یک رکورد.
 *
 * رکورد شامل سید و لاگِ کاملِ حرکت‌هاست، بنابراین هر دست را می‌توان دوباره از اول اجرا کرد و با
 * [MatchVerifier] فهمید که نتیجه واقعی است یا دستکاری شده. این همان چیزی است که بعداً تورنمنت و
 * جدولِ امتیازات روی آن سوار می‌شود.
 *
 * @property digest امضای قطعیِ `(gameId, seed, options, players, actions)`.
 */
data class MatchRecord(
    val schema: Int = SCHEMA,
    val matchId: String,
    val gameId: String,
    val mode: String,
    val seed: Long,
    val options: JsonObject,
    val players: List<Player>,
    val actions: List<LoggedAction>,
    val result: MatchResult?,
    val digest: String,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("schema", JsonPrimitive(schema))
        put("matchId", JsonPrimitive(matchId))
        put("gameId", JsonPrimitive(gameId))
        put("mode", JsonPrimitive(mode))
        put("seed", JsonPrimitive(seed))
        put("options", options)
        put("players", jsonPlayers(players))
        put("actions", JsonArray(actions.map { it.toJson() }))
        put("result", result?.toJson() ?: JsonNull)
        put("digest", JsonPrimitive(digest))
    }

    private fun jsonPlayers(list: List<Player>) = JsonArray(
        list.map { player ->
            buildJsonObject {
                put("id", JsonPrimitive(player.id.value))
                put("name", JsonPrimitive(player.displayName))
                put("seat", JsonPrimitive(player.seat))
                put("bot", JsonPrimitive(player.isBot))
                put("team", JsonPrimitive(player.team))
            }
        },
    )

    private fun MatchResult.toJson(): JsonObject = buildJsonObject {
        put("finishedAtMs", JsonPrimitive(finishedAtMs))
        put("standings", JsonArray(
            standings.map { s ->
                buildJsonObject {
                    put("player", JsonPrimitive(s.player.value))
                    put("rank", JsonPrimitive(s.rank))
                    put("score", JsonPrimitive(s.score))
                    put("detail", s.detail)
                }
            },
        ))
        put("teams", teams?.let { list ->
            JsonArray(list.map { t ->
                buildJsonObject {
                    put("team", JsonPrimitive(t.team))
                    put("rank", JsonPrimitive(t.rank))
                    put("score", JsonPrimitive(t.score))
                    put("members", JsonArray(t.members.map { JsonPrimitive(it.value) }))
                    put("detail", t.detail)
                }
            })
        } ?: JsonNull)
        put("summary", summary)
    }

    companion object {
        /** نسخهٔ قالب رکورد؛ هر تغییرِ شِما باید این عدد را بالا ببرد. */
        const val SCHEMA = 1

        /**
         * ساخت رکورد و محاسبهٔ امضا.
         *
         * امضا روی زمانِ هر حرکت (`atMs`) حساب **نمی‌شود**، چون ساعت در بازپخش فرق می‌کند؛
         * چیزی که باید ثابت بماند «چه کسی، چه حرکتی، با چه داده‌ای» است.
         */
        fun of(
            matchId: String,
            gameId: String,
            mode: String,
            seed: Long,
            options: JsonObject,
            players: List<Player>,
            actions: List<LoggedAction>,
            result: MatchResult?,
        ): MatchRecord {
            val digestSource = buildJsonObject {
                put("gameId", JsonPrimitive(gameId))
                put("seed", JsonPrimitive(seed))
                put("options", options)
                put(
                    "players",
                    JsonArray(
                        players.map { p ->
                            buildJsonObject {
                                put("id", JsonPrimitive(p.id.value))
                                put("seat", JsonPrimitive(p.seat))
                                put("team", JsonPrimitive(p.team))
                            }
                        },
                    ),
                )
                put(
                    "actions",
                    JsonArray(
                        actions.map { a ->
                            buildJsonObject {
                                put("seq", JsonPrimitive(a.seq))
                                put("player", JsonPrimitive(a.player))
                                put("type", JsonPrimitive(a.type))
                                put("payload", a.payload)
                            }
                        },
                    ),
                )
            }
            return MatchRecord(
                matchId = matchId,
                gameId = gameId,
                mode = mode,
                seed = seed,
                options = options,
                players = players,
                actions = actions,
                result = result,
                digest = sha256Hex(canonicalJson(digestSource)),
            )
        }
    }
}
