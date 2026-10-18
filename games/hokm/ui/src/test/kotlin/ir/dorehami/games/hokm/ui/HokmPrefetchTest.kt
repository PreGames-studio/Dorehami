package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmPrefetchTest {

    @Test
    fun `prefetch on wifi and battery ok`() {
        assertTrue(HokmPrefetch.shouldPrefetch(true, true))
    }

    @Test
    fun `no prefetch on mobile`() {
        assertTrue(!HokmPrefetch.shouldPrefetch(false, true))
    }

    @Test
    fun `no prefetch on low battery`() {
        assertTrue(!HokmPrefetch.shouldPrefetch(true, false))
    }

    @Test
    fun `priority images higher than audio`() {
        assertTrue(HokmPrefetch.priority("card.png") < HokmPrefetch.priority("sound.mp3"))
    }
}
