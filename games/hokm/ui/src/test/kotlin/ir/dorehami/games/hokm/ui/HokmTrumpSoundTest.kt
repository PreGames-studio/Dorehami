package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The trump "شترق" is not a second sound that happens to be loud: it is *the same* cue the ace makes
 * in the hakem draw — the same name, therefore the same `hokm_card_slam.wav`, at the same volume.
 *
 * These tests pin that identity so a later change cannot quietly give the trump its own sample again.
 */
class HokmTrumpSoundTest {

    @Test
    fun `the hakem's ace and every trump play the same cue`() {
        val ace = TableAudio.hakemDraw(Rank.ACE)
        val trumpLead = TableAudio.cardLands(trump = true, cut = false)
        val trumpCut = TableAudio.cardLands(trump = true, cut = true)
        assertEquals(TableAudio.SLAM, ace)
        assertEquals(ace, trumpLead)
        assertEquals(ace, trumpCut)
    }

    @Test
    fun `an ordinary card still lands quietly`() {
        assertEquals(TableAudio.PLAY, TableAudio.cardLands(trump = false, cut = false))
        assertEquals(TableAudio.DEAL, TableAudio.hakemDraw(Rank.SEVEN))
    }

    @Test
    fun `every cue the table asks for is a real bundled sound`() {
        val map = HokmSounds.map
        listOf(TableAudio.SLAM, TableAudio.PLAY, TableAudio.DEAL).forEach { name ->
            assertTrue(map.containsKey(name), "no bundled audio for '$name'")
        }
        // One slap asset only: a "card_trump" (or any second slap) would mean two different sounds.
        assertEquals(1, map.keys.count { it.contains("slam") }, "exactly one slam cue")
        assertTrue(!map.containsKey("card_trump"), "the trump must reuse the ace's slap, not a new one")
    }
}
