package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchRecordCodec
import ir.dorehami.gamesdk.RecordDecode
import ir.dorehami.games.hokm.HokmModule

/**
 * HK-011 — saving an in-progress local match and getting it back after rotation or process
 * death. The save is the platform's [MatchRecord] (seed + action log) through the shared
 * [MatchRecordCodec]; [load] rebuilds the match by deterministic replay, so continuing is
 * exact — same deal, same hand, same turn — and a save this build cannot trust is refused
 * with a *safe* Persian error instead of a crash or a half-restored table.
 */
object HokmSave {

    sealed class Load {
        /** No save on disk — start a fresh match. */
        object Empty : Load()

        /** The match rebuilt exactly where it was (possibly already finished). */
        data class Restored(val engine: GameEngine, val record: MatchRecord) : Load()

        /** The save exists but cannot be trusted; show [messageFa] and start fresh. */
        data class Failed(val messageFa: String) : Load()
    }

    /** Serialises a match record (the engine's, or one just restored) for storage. */
    fun save(record: MatchRecord): String = MatchRecordCodec.encode(record).toString()

    /**
     * Restores a saved match. Never throws: corrupt text, a newer save schema, a tampered
     * action log and a digest that no longer matches all come back as [Load.Failed].
     */
    fun load(text: String?): Load {
        if (text.isNullOrBlank()) return Load.Empty
        val decoded = MatchRecordCodec.decode(text)
        if (decoded is RecordDecode.Invalid) return Load.Failed(decoded.messageFa)
        val record = (decoded as RecordDecode.Decoded).record
        return try {
            val engine = GameEngine.replay(HokmModule, record)
            if (engine.digest() != record.digest) {
                Load.Failed("ذخیرهٔ بازی با وضعیت بازی نمی‌خواند و قابل اعتماد نیست")
            } else {
                Load.Restored(engine, record)
            }
        } catch (e: Exception) {
            // replay refuses a record whose logged actions no longer apply — treat as unsafe.
            Load.Failed("ذخیرهٔ بازی خراب است و قابل بازیابی نیست")
        }
    }
}
