package ir.dorehami.games.pantohub

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Versioned local snapshot codec; unknown future fields are ignored for forward-compatible recovery. */
object GameStateJsonCodec {
    private val format = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = true
    }

    fun encode(state: GameState): String = format.encodeToString(state)

    fun decode(serialized: String): GameState {
        val state = format.decodeFromString<GameState>(serialized)
        require(state.schemaVersion == 1) { "نسخهٔ ذخیرهٔ بازی پشتیبانی نمی‌شود." }
        require(state.teams.size >= 2) { "فایل ذخیرهٔ بازی تیم کافی ندارد." }
        require(state.teams.all { team -> team.players.isNotEmpty() }) { "فایل ذخیرهٔ بازی بازیکن ندارد." }
        return state
    }
}
