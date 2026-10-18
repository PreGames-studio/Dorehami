package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmAudioTest {

    @Test
    fun `muted blocks play`() {
        val p = HokmAudio.policy(true, HokmAudio.Focus.GAINED, HokmAudio.Lifecycle.FOREGROUND)
        assertTrue(!p.play)
    }

    @Test
    fun `background blocks play`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.GAINED, HokmAudio.Lifecycle.BACKGROUND)
        assertTrue(!p.play)
    }

    @Test
    fun `destroyed blocks play`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.GAINED, HokmAudio.Lifecycle.DESTROYED)
        assertTrue(!p.play)
    }

    @Test
    fun `focus lost blocks play`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.LOST, HokmAudio.Lifecycle.FOREGROUND)
        assertTrue(!p.play)
    }

    @Test
    fun `ducked plays at reduced volume`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.DUCK, HokmAudio.Lifecycle.FOREGROUND)
        assertTrue(p.play)
        assertTrue(p.ducked)
        assertTrue(HokmAudio.volumeFor(true) < 1.0f)
    }

    @Test
    fun `gained plays normally`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.GAINED, HokmAudio.Lifecycle.FOREGROUND)
        assertTrue(p.play)
        assertTrue(!p.ducked)
        assertEquals(1.0f, HokmAudio.volumeFor(false))
    }

    @Test
    fun `mayInterrupt when nothing playing`() {
        assertTrue(HokmAudio.mayInterrupt(false, 1, 2))
    }

    @Test
    fun `mayInterrupt only if higher priority`() {
        assertTrue(HokmAudio.mayInterrupt(true, 3, 2))
        assertTrue(!HokmAudio.mayInterrupt(true, 2, 3))
        assertTrue(!HokmAudio.mayInterrupt(true, 2, 2))
    }

    @Test
    fun `cooldown ok after 80ms`() {
        assertTrue(HokmAudio.cooldownOk(0, 80))
        assertTrue(!HokmAudio.cooldownOk(0, 79))
    }

    @Test
    fun `reasons are non-empty`() {
        val p = HokmAudio.policy(false, HokmAudio.Focus.GAINED, HokmAudio.Lifecycle.FOREGROUND)
        assertTrue(p.reason.isNotEmpty())
    }
}
