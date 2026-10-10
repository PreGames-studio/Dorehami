package ir.dorehami.gamesdk

import ir.dorehami.protocol.GameId

/** All games the app (or the server) knows about. Rejects duplicate ids at construction. */
class GameRegistry(modules: List<GameModule>) {
    constructor(vararg modules: GameModule) : this(modules.toList())

    private val byId: Map<GameId, GameModule>

    init {
        val seen = HashSet<GameId>()
        for (m in modules) {
            require(seen.add(m.meta.id)) { "duplicate game id: ${m.meta.id}" }
        }
        byId = modules.associateBy { it.meta.id }
    }

    val all: List<GameModule> get() = byId.values.toList()

    operator fun get(id: GameId): GameModule? = byId[id]

    fun require(id: GameId): GameModule =
        byId[id] ?: throw GameConfigException("game.unknown", "بازی «${id.value}» شناخته‌شده نیست")
}
