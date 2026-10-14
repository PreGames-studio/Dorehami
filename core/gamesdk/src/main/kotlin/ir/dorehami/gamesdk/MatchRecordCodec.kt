package ir.dorehami.gamesdk

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Outcome of decoding a stored [MatchRecord] — never an exception (the save path must not crash). */
sealed class RecordDecode {
    data class Decoded(val record: MatchRecord) : RecordDecode()

    /** The blob cannot be trusted: corrupt, incomplete, or written by a newer format. */
    data class Invalid(val code: String, val messageFa: String) : RecordDecode()
}

/**
 * The one storage format of the platform (`seed + log + result + digest`, ARCHITECTURE 4.4),
 * both directions. The server's `GET /v1/matches/{code}` and the local save of an in-progress
 * game share this codec so a record means the same thing everywhere.
 *
 * Schema policy for now (the versioning ADR is HK-012): [MatchRecord.schema] must be in
 * [SUPPORTED_SCHEMAS] — a blob from a newer format is refused with `save.unsupported_schema`
 * (a *safe* error) instead of being guessed at. Known old schemas can be migrated in [decode]
 * before that check is relaxed.
 */
object MatchRecordCodec {

    const val SCHEMA_V1 = 1

    /** Format versions this build can read. Writing always uses [MatchRecord.schema]. */
    val SUPPORTED_SCHEMAS: Set<Int> = setOf(SCHEMA_V1)

    fun encode(record: MatchRecord): JsonObject = buildJsonObject {
        put("schema", record.schema)
        put("gameId", record.gameId.value)
        put("seed", record.seed)
        put("options", record.options)
        put("players", buildJsonArray { record.players.forEach { add(JsonPrimitive(it.value)) } })
        put("digest", record.digest)
        put(
            "actions",
            buildJsonArray {
                record.actions.forEach { a ->
                    add(
                        buildJsonObject {
                            put("seq", a.seq)
                            put("player", a.player.value)
                            put("type", a.type)
                            put("payload", a.payload)
                            put("atMs", a.atMs)
                        },
                    )
                }
            },
        )
        record.result?.let { r ->
            put(
                "standings",
                buildJsonArray {
                    r.standings.forEach { st ->
                        add(
                            buildJsonObject {
                                put("rank", st.rank)
                                put("score", st.score)
                                put("members", buildJsonArray { st.members.forEach { m -> add(JsonPrimitive(m.value)) } })
                            },
                        )
                    }
                },
            )
        }
    }

    /** Parses [text] as a stored record. Never throws; every failure is an [RecordDecode.Invalid]. */
    fun decode(text: String): RecordDecode {
        val json = try {
            Json.parseToJsonElement(text)
        } catch (e: Exception) {
            return invalid("save.corrupt", "ذخیرهٔ بازی خوانده نشد")
        }
        val obj = json as? JsonObject ?: return invalid("save.corrupt", "ذخیرهٔ بازی خوانده نشد")
        return decode(obj)
    }

    fun decode(json: JsonObject): RecordDecode {
        val schema = (json["schema"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: return invalid("save.corrupt", "ذخیرهٔ بازی نسخهٔ ساخت ندارد")
        if (schema !in SUPPORTED_SCHEMAS) {
            // Newer (or unknown) format: refuse safely — guessing would corrupt the match.
            return invalid("save.unsupported_schema", "ذخیرهٔ بازی با نسخهٔ جدیدتری ساخته شده و در این نسخه قابل خواندن نیست")
        }
        // Migration point: when SCHEMA_V2 exists, upgrade v1 blobs here and then accept them.
        return decodeV1(json)
    }

    // ------------------------------------------------------------------ schema 1

    private fun decodeV1(json: JsonObject): RecordDecode {
        fun str(key: String): String? = (json[key] as? JsonPrimitive)?.contentOrNull
        val gameId = str("gameId") ?: return invalid("save.corrupt", "ذخیرهٔ بازی بازی ندارد")
        val seed = (json["seed"] as? JsonPrimitive)?.content?.toLongOrNull()
            ?: return invalid("save.corrupt", "ذخیرهٔ بازی بذر بازی ندارد")
        val options = json["options"] as? JsonObject
            ?: return invalid("save.corrupt", "ذخیرهٔ بازی گزینه‌ها را ندارد")
        val playersEl = json["players"] as? JsonArray
            ?: return invalid("save.corrupt", "ذخیرهٔ بازی بازیکنان را ندارد")
        val players = ArrayList<ir.dorehami.protocol.PlayerId>(playersEl.size)
        for (p in playersEl) {
            val v = (p as? JsonPrimitive)?.contentOrNull ?: return invalid("save.corrupt", "شناسهٔ بازیکن ذخیره خراب است")
            players += ir.dorehami.protocol.PlayerId(v)
        }
        if (players.isEmpty()) return invalid("save.corrupt", "ذخیرهٔ بازی بازیکنی ندارد")
        val digest = str("digest") ?: return invalid("save.corrupt", "ذخیرهٔ بازی اثرانگشت ندارد")
        val actionsEl = json["actions"] as? JsonArray
            ?: return invalid("save.corrupt", "ذخیرهٔ بازی حرکت‌ها را ندارد")
        val actions = ArrayList<LoggedAction>(actionsEl.size)
        for (a in actionsEl) {
            val o = a as? JsonObject ?: return invalid("save.corrupt", "حرکت ذخیره‌شده خراب است")
            val seq = (o["seq"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: return invalid("save.corrupt", "حرکت ذخیره‌شده شماره ندارد")
            val player = (o["player"] as? JsonPrimitive)?.contentOrNull
                ?: return invalid("save.corrupt", "حرکت ذخیره‌شده بازیکن ندارد")
            val type = (o["type"] as? JsonPrimitive)?.contentOrNull
                ?: return invalid("save.corrupt", "حرکت ذخیره‌شده نوع ندارد")
            val payload = o["payload"] as? JsonObject
                ?: return invalid("save.corrupt", "حرکت ذخیره‌شده محتوا ندارد")
            val atMs = (o["atMs"] as? JsonPrimitive)?.content?.toLongOrNull()
                ?: return invalid("save.corrupt", "حرکت ذخیره‌شده زمان ندارد")
            actions += LoggedAction(seq, ir.dorehami.protocol.PlayerId(player), type, payload, atMs)
        }
        val result = (json["standings"] as? JsonArray)?.let { st ->
            val standings = ArrayList<Standing>(st.size)
            for (s in st) {
                val so = s as? JsonObject ?: return invalid("save.corrupt", "رتبه‌بندی ذخیره‌شده خراب است")
                val rank = (so["rank"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: return invalid("save.corrupt", "رتبهٔ ذخیره‌شده خراب است")
                val score = (so["score"] as? JsonPrimitive)?.content?.toIntOrNull()
                    ?: return invalid("save.corrupt", "امتیاز ذخیره‌شده خراب است")
                val membersEl = so["members"] as? JsonArray
                    ?: return invalid("save.corrupt", "اعضای رتبهٔ ذخیره‌شده خراب است")
                val members = ArrayList<ir.dorehami.protocol.PlayerId>(membersEl.size)
                for (m in membersEl) {
                    val v = (m as? JsonPrimitive)?.contentOrNull ?: return invalid("save.corrupt", "عضو رتبهٔ ذخیره‌شده خراب است")
                    members += ir.dorehami.protocol.PlayerId(v)
                }
                standings += Standing(members, rank, score)
            }
            MatchResult(standings)
        }
        return RecordDecode.Decoded(
            MatchRecord(
                gameId = ir.dorehami.protocol.GameId(gameId),
                seed = seed,
                options = options,
                players = players,
                actions = actions,
                result = result,
                digest = digest,
                schema = SCHEMA_V1,
            ),
        )
    }

    private fun invalid(code: String, messageFa: String): RecordDecode.Invalid = RecordDecode.Invalid(code, messageFa)
}
