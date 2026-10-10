package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Shared helpers for the Hokm contract tests. No network, no sleeps, no mocks. */
internal object HokmTestSupport {
    val players = listOf(PlayerId("p_a"), PlayerId("p_b"), PlayerId("p_c"), PlayerId("p_d"))

    fun config(seed: Long, options: JsonObject = HokmModule.defaultOptions()) = GameConfig(
        seed = seed,
        players = players,
        options = options,
        clock = ManualClock(),
        rng = SplitMixRng(seed),
    )

    fun engine(seed: Long, options: JsonObject = HokmModule.defaultOptions()) =
        GameEngine.start(HokmModule, config(seed, options))

    fun session(seed: Long): HokmSession = HokmModule.create(config(seed)) as HokmSession

    fun chooseHokm(suit: Suit) = GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", suit.id) })

    fun playCard(code: String) = GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", code) })

    fun JsonObject.int(key: String) = this[key]!!.jsonPrimitive.int

    fun JsonObject.str(key: String) = this[key]!!.jsonPrimitive.content

    fun JsonObject.cards(key: String) = this[key]!!.jsonArray.map { it.jsonPrimitive.content }

    fun JsonObject.ints(key: String) = this[key]!!.jsonArray.map { it.jsonPrimitive.int }

    /** Player whose turn it is right now (hakem while choosing hokm). */
    fun whoseTurn(engine: GameEngine): PlayerId {
        val v = engine.session.publicView()
        val seat = if (v.str("handPhase") == HandPhase.CHOOSING_HOKM.id) v.int("hakem") else v.int("turn")
        return players[seat]
    }

    /** Plays a whole match with four bots, collecting every emission. */
    fun playWithBots(engine: GameEngine, maxActions: Int = 10_000): List<Emission> {
        val all = ArrayList<Emission>()
        var n = 0
        while (engine.session.phase != Phase.FINISHED) {
            check(n++ < maxActions) { "bot game did not finish in $maxActions actions" }
            val player = whoseTurn(engine)
            when (val r = engine.submitBot(player)) {
                is ApplyResult.Ok -> all += r.emissions
                is ApplyResult.Rejected -> error("bot move rejected: ${r.code} ${r.messageFa}")
                null -> error("bot had no move for $player")
            }
        }
        return all
    }
}
