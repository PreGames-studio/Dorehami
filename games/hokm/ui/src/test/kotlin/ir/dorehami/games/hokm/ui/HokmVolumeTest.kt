package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmVolumeTest {

    @Test
    fun `clamp range`() {
        assertEquals(0f, HokmVolume.clamp(-0.5f))
        assertEquals(1f, HokmVolume.clamp(1.5f))
        assertEquals(0.5f, HokmVolume.clamp(0.5f))
    }

    @Test
    fun `effective sfx`() {
        val v = HokmVolume.Volumes(sfx = 0.5f, master = 0.8f)
        assertEquals(0.4f, HokmVolume.effectiveSfx(v), 0.001f)
    }

    @Test
    fun `muted when master zero`() {
        val v = HokmVolume.Volumes(master = 0f)
        assertTrue(HokmVolume.muted(v))
    }

    @Test
    fun `not muted when master nonzero`() {
        val v = HokmVolume.Volumes(master = 0.5f)
        assertTrue(!HokmVolume.muted(v))
    }

    @Test
    fun `set sfx clamps`() {
        val v = HokmVolume.setSfx(HokmVolume.Volumes(), 2f)
        assertEquals(1f, v.sfx)
    }
}
