package ir.dorehami.games.hokm.ui

/**
 * HK-047 (chat slice) — chat message model as **pure, unit-testable** logic.
 *
 * The roadmap mentions chat (Hokm Online). This object defines the message data class,
 * sanitization (length, profanity placeholder), and a small buffer. The Android layer
 * renders and sends.
 */
object HokmChat {

    data class Message(
        val sender: String,
        val text: String,
        val timestampMs: Long,
        val isMe: Boolean
    )

    const val MAX_TEXT_LEN: Int = 200
    const val MAX_BUFFER: Int = 100

    fun sanitize(text: String): String = text.trim().take(MAX_TEXT_LEN)

    fun isValid(text: String): Boolean = sanitize(text).isNotEmpty()

    fun create(sender: String, text: String, nowMs: Long, isMe: Boolean): Message? {
        val clean = sanitize(text)
        if (clean.isEmpty()) return null
        return Message(sender = sender, text = clean, timestampMs = nowMs, isMe = isMe)
    }

    fun append(buffer: List<Message>, msg: Message): List<Message> {
        val new = buffer + msg
        return if (new.size > MAX_BUFFER) new.takeLast(MAX_BUFFER) else new
    }

    fun format(msg: Message): String = "${msg.sender}: ${msg.text}"
}
