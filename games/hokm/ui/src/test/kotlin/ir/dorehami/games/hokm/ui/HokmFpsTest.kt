package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmFpsTest {

    @Test
    fun `smooth at 60`() {
        assertTrue(HokmFps.isSmooth(60f))
    }

    @Test
    fun `not smooth at 30`() {
        assertTrue(!HokmFps.isSmooth(30f))
    }

    @Test
    fun `frame time at 60`() {
        assertEquals(1000f / 60f, HokmFps.frameTimeMs(60f), 0.001f)
    }

    @Test
    fun `quality for fps`() {
        assertEquals("high", HokmFps.qualityForFps(60f))
        assertEquals("medium", HokmFps.qualityForFps(45f))
        assertEquals("low", HokmFps.qualityForFps(30f))
    }
}
