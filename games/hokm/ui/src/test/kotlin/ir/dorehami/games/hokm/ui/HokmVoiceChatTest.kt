package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmVoiceChatTest {

    @Test
    fun `can speak when connected and not muted`() {
        assertTrue(HokmVoiceChat.canSpeak(HokmVoiceChat.State.CONNECTED, false))
    }

    @Test
    fun `cannot speak when muted`() {
        assertTrue(!HokmVoiceChat.canSpeak(HokmVoiceChat.State.CONNECTED, true))
    }

    @Test
    fun `toggle mute`() {
        assertEquals(HokmVoiceChat.State.MUTED, HokmVoiceChat.toggleMute(HokmVoiceChat.State.CONNECTED))
        assertEquals(HokmVoiceChat.State.CONNECTED, HokmVoiceChat.toggleMute(HokmVoiceChat.State.MUTED))
    }

    @Test
    fun `labels non-empty`() {
        HokmVoiceChat.State.values().forEach { s ->
            assertTrue(HokmVoiceChat.label(s).isNotEmpty())
        }
    }
}
