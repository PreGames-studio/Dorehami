package ir.dorehami.gamesdk

import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RegistryTest {
    private fun fake(id: String) = object : GameModule {
        override val meta = GameMeta(
            id = GameId(id), titleFa = id, titleEn = id, taglineFa = "", minPlayers = 2, maxPlayers = 2,
            iconKey = id, estimatedMinutes = 1, capabilities = emptySet(), defaultOptions = EMPTY_JSON,
        )
        override fun validateOptions(options: kotlinx.serialization.json.JsonObject) = options
        override fun create(config: GameConfig): GameSession = throw UnsupportedOperationException()
        override val botAction: ((GameSession, PlayerId) -> GameAction?)? = null
    }

    @Test
    fun `duplicate ids are rejected`() {
        assertFailsWith<IllegalArgumentException> { GameRegistry(fake("a"), fake("a")) }
    }

    @Test
    fun `lookup by id`() {
        val reg = GameRegistry(fake("a"), fake("b"))
        assertEquals("b", reg[GameId("b")]!!.meta.titleEn)
        assertNull(reg[GameId("zzz")])
        assertFailsWith<GameConfigException> { reg.require(GameId("zzz")) }
    }
}
