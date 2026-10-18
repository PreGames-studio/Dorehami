package ir.dorehami.games.hokm.ui

/**
 * HK-053 (analytics slice) — analytics event model as **pure, unit-testable** logic.
 *
 * The roadmap mentions observability. This object defines event names and a small
 * buffer for batching. The Android layer sends to the backend.
 */
object HokmAnalytics {

    enum class Event {
        APP_OPEN, MATCH_START, MATCH_END, CARD_PLAY, TRUMP_SELECT,
        SETTINGS_CHANGE, STICKER_SEND, ERROR_OCCURRED
    }

    data class AnalyticsEvent(val name: Event, val params: Map<String, String>, val timestampMs: Long)

    const val MAX_BUFFER: Int = 200

    fun create(name: Event, params: Map<String, String> = emptyMap(), nowMs: Long): AnalyticsEvent =
        AnalyticsEvent(name, params, nowMs)

    fun append(buffer: List<AnalyticsEvent>, event: AnalyticsEvent): List<AnalyticsEvent> {
        val new = buffer + event
        return if (new.size > MAX_BUFFER) new.takeLast(MAX_BUFFER) else new
    }

    fun eventName(e: Event): String = e.name.lowercase()

    fun count(buffer: List<AnalyticsEvent>, name: Event): Int = buffer.count { it.name == name }
}
