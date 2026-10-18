package ir.dorehami.games.hokm.ui

/**
 * HK-031 (network message slice) — network message model as **pure, unit-testable** logic.
 */
object HokmNetworkMessage {

    enum class Type { JOIN, LEAVE, PLAY, TRUMP, CHAT, STICKER, PING, PONG, STATE, ERROR }

    data class Message(val type: Type, val payload: String, val sender: String, val timestampMs: Long)

    fun create(type: Type, payload: String, sender: String, nowMs: Long): Message =
        Message(type, payload, sender, nowMs)

    fun isGameMessage(m: Message): Boolean = m.type in setOf(Type.PLAY, Type.TRUMP, Type.STATE)

    fun isSocialMessage(m: Message): Boolean = m.type in setOf(Type.CHAT, Type.STICKER)
}
