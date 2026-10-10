package ir.dorehami.gamesdk

import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import java.security.MessageDigest

/** One accepted action, as stored in the record (docs/ARCHITECTURE.md section 4.4). */
data class LoggedAction(
    val seq: Long,
    val player: PlayerId,
    val type: String,
    val payload: JsonObject,
    val atMs: Long,
)

/**
 * The only storage format of the platform: `seed + log + result + digest`.
 * Replaying it through [GameEngine.replay] must reproduce the same [digest].
 */
data class MatchRecord(
    val gameId: GameId,
    val seed: Long,
    val options: JsonObject,
    val players: List<PlayerId>,
    val actions: List<LoggedAction>,
    val result: MatchResult?,
    val digest: String,
    val schema: Int = 1,
)

/**
 * The official loop and the *only* writer of state: applies an action, logs it, and hands the
 * emissions to whoever owns the room (session controller, server, or a local solo screen).
 */
class GameEngine private constructor(
    val module: GameModule,
    val config: GameConfig,
    val session: GameSession,
) {
    private val log = ArrayList<LoggedAction>()

    /** Accepted actions so far, in order. */
    val actions: List<LoggedAction> get() = log

    /** Applies [action] for [player]; on success it is appended to the log. */
    fun submit(player: PlayerId, action: GameAction): ApplyResult {
        if (player !in config.players) {
            return ApplyResult.Rejected("game.unknown_player", "این بازیکن در این بازی نیست")
        }
        val before = session.version
        val result = session.apply(player, action)
        if (result is ApplyResult.Ok) {
            check(session.version == before + 1) {
                "${module.meta.id}: version must advance by exactly one per accepted action"
            }
            log += LoggedAction(
                seq = log.size.toLong(),
                player = player,
                type = action.type,
                payload = action.payload,
                atMs = config.clock.nowMillis(),
            )
        } else {
            check(session.version == before) { "${module.meta.id}: version changed on a rejected action" }
        }
        return result
    }

    /**
     * Lets the game's bot policy move for [player]. Returns the result, or `null` when the game has
     * no bots or the bot has nothing to do. The bot's action goes through [submit] like anyone else's.
     */
    fun submitBot(player: PlayerId): ApplyResult? {
        val policy = module.botAction ?: return null
        val action = policy(session, player) ?: return null
        return submit(player, action)
    }

    /** Stable fingerprint of the current state: SHA-256 of the canonical snapshot. */
    fun digest(): String = sha256(session.snapshot().toString())

    fun record(): MatchRecord = MatchRecord(
        gameId = module.meta.id,
        seed = config.seed,
        options = config.options,
        players = config.players,
        actions = log.toList(),
        result = session.result(),
        digest = digest(),
    )

    companion object {
        /** Starts a fresh match. Rng/clock come from the config so tests and servers control them. */
        fun start(module: GameModule, config: GameConfig): GameEngine {
            val options = module.validateOptions(config.options)
            val cfg = config.copy(options = options)
            return GameEngine(module, cfg, module.create(cfg))
        }

        /**
         * Rebuilds a match from a record by replaying every logged action from the seed.
         * Throws if a logged action is rejected — that means the record was tampered with.
         */
        fun replay(module: GameModule, record: MatchRecord): GameEngine {
            require(record.gameId == module.meta.id) { "record is for ${record.gameId}, not ${module.meta.id}" }
            val clock = ManualClock()
            val engine = start(
                module,
                GameConfig(
                    seed = record.seed,
                    players = record.players,
                    options = record.options,
                    clock = clock,
                    rng = SplitMixRng(record.seed),
                ),
            )
            for (a in record.actions) {
                clock.set(a.atMs)
                when (val r = engine.submit(a.player, GameAction(a.type, a.payload))) {
                    is ApplyResult.Ok -> Unit
                    is ApplyResult.Rejected -> throw IllegalStateException(
                        "replay rejected action #${a.seq} (${a.type}) by ${a.player}: ${r.code} — ${r.messageFa}",
                    )
                }
            }
            return engine
        }

        fun sha256(text: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                val v = b.toInt() and 0xff
                sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
            }
            return sb.toString()
        }

        private val HEX = "0123456789abcdef".toCharArray()
    }
}

/** Verdict of [MatchVerifier]. */
sealed interface Verdict {
    data class Verified(val result: MatchResult?, val digest: String) : Verdict
    data class Rejected(val reason: String) : Verdict
}

/** Replays a record and compares the digest — the anti-cheat primitive every authority uses. */
class MatchVerifier(private val registry: GameRegistry) {
    fun verify(record: MatchRecord): Verdict {
        val module = registry[record.gameId] ?: return Verdict.Rejected("unknown game ${record.gameId}")
        val engine = try {
            GameEngine.replay(module, record)
        } catch (e: IllegalStateException) {
            return Verdict.Rejected(e.message ?: "replay failed")
        } catch (e: GameConfigException) {
            return Verdict.Rejected(e.messageFa)
        }
        val digest = engine.digest()
        if (digest != record.digest) return Verdict.Rejected("digest mismatch")
        if (engine.session.result() != record.result) return Verdict.Rejected("result mismatch")
        return Verdict.Verified(engine.session.result(), digest)
    }
}
