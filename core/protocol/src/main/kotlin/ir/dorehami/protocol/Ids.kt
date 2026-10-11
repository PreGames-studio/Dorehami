package ir.dorehami.protocol

/** Identifier of a player inside a room (`p_xxxxxxxxxx` on the wire; tests may use any string). */
@JvmInline
value class PlayerId(val value: String) {
    init {
        require(value.isNotBlank()) { "PlayerId must not be blank" }
    }

    override fun toString(): String = value
}

/** Stable identifier of a game module (`hokm`, `pantohub`, …). Never changes once published. */
@JvmInline
value class GameId(val value: String) {
    init {
        require(value.isNotBlank()) { "GameId must not be blank" }
    }

    override fun toString(): String = value
}
