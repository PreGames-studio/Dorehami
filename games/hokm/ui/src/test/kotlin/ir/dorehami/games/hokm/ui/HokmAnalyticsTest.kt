package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmAnalyticsTest {

    @Test
    fun `create event`() {
        val e = HokmAnalytics.create(HokmAnalytics.Event.APP_OPEN, mapOf("v" to "1"), 100)
        assertEquals(HokmAnalytics.Event.APP_OPEN, e.name)
        assertEquals(100L, e.timestampMs)
    }

    @Test
    fun `append grows`() {
        val e = HokmAnalytics.create(HokmAnalytics.Event.APP_OPEN, emptyMap(), 0)
        val buf = HokmAnalytics.append(emptyList(), e)
        assertEquals(1, buf.size)
    }

    @Test
    fun `append caps`() {
        var buf = emptyList<HokmAnalytics.AnalyticsEvent>()
        repeat(HokmAnalytics.MAX_BUFFER + 5) { i ->
            val e = HokmAnalytics.create(HokmAnalytics.Event.CARD_PLAY, emptyMap(), i.toLong())
            buf = HokmAnalytics.append(buf, e)
        }
        assertEquals(HokmAnalytics.MAX_BUFFER, buf.size)
    }

    @Test
    fun `count by name`() {
        var buf = emptyList<HokmAnalytics.AnalyticsEvent>()
        buf = HokmAnalytics.append(buf, HokmAnalytics.create(HokmAnalytics.Event.APP_OPEN, emptyMap(), 0))
        buf = HokmAnalytics.append(buf, HokmAnalytics.create(HokmAnalytics.Event.CARD_PLAY, emptyMap(), 1))
        assertEquals(1, HokmAnalytics.count(buf, HokmAnalytics.Event.APP_OPEN))
    }

    @Test
    fun `event name lowercase`() {
        assertTrue(HokmAnalytics.eventName(HokmAnalytics.Event.APP_OPEN).isNotEmpty())
    }
}
