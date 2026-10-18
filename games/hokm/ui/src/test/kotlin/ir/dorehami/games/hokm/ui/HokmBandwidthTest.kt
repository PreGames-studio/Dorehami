package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmBandwidthTest {

    @Test
    fun `low bandwidth`() {
        assertTrue(HokmBandwidth.isLow(50))
        assertTrue(!HokmBandwidth.isLow(200))
    }

    @Test
    fun `reduce assets on low`() {
        assertTrue(HokmBandwidth.shouldReduceAssets(50))
        assertTrue(!HokmBandwidth.shouldReduceAssets(200))
    }

    @Test
    fun `asset quality`() {
        assertEquals("low", HokmBandwidth.assetQuality(50))
        assertEquals("high", HokmBandwidth.assetQuality(200))
    }
}
