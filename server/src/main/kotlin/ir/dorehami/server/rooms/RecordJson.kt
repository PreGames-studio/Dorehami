package ir.dorehami.server.rooms

import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchRecordCodec
import kotlinx.serialization.json.JsonObject

/** Full record as served by `GET /v1/matches/{code}` (docs/ARCHITECTURE.md 4.4). */
fun MatchRecord.toJson(): JsonObject = MatchRecordCodec.encode(this)
