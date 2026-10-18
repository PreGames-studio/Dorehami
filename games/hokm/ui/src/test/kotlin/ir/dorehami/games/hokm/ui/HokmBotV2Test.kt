package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmBotV2Test {

    @Test
    fun `single legal returns it`() {
        assertEquals("7♥", HokmBotV2.choose(listOf("7♥"), listOf("7♥"), '♠', '♥', false))
    }

    @Test
    fun `empty legal returns empty`() {
        assertEquals("", HokmBotV2.choose(emptyList(), emptyList(), '♠', '♥', false))
    }

    @Test
    fun `partner winning dumps`() {
        val r = HokmBotV2.choose(listOf("A♥", "3♥"), listOf("A♥", "3♥"), '♠', '♥', true)
        assertTrue(r in listOf("A♥", "3♥"))
    }

    @Test
    fun `dumpCard creates void when possible`() {
        // hand has one club and two hearts; trump spades; lead hearts -> can create void in clubs
        val legal = listOf("5♣", "3♥", "7♥")
        val r = HokmBotV2.dumpCard(legal, '♠', '♥')
        assertEquals("5♣", r)
    }

    @Test
    fun `dumpCard falls back to weakest of lead suit`() {
        val legal = listOf("3♥", "7♥", "9♥")
        val r = HokmBotV2.dumpCard(legal, '♠', '♥')
        // weakest = highest rankValue = '9'? rankValue: 9 is index 7 (0-based in RANKS). '3' index 11. maxBy rankValue = '3' (weakest). Actually max rankValue = weakest. 3 has rankValue 11, 9 has 7. So max is 3.
        assertEquals("3♥", r)
    }

    @Test
    fun `isTrump check`() {
        assertTrue(HokmBotV2.isTrump("A♠", '♠'))
        assertTrue(!HokmBotV2.isTrump("A♥", '♠'))
    }

    @Test
    fun `rankValue ordering`() {
        assertTrue(HokmBotV2.rankValue('A') < HokmBotV2.rankValue('2'))
    }
}
