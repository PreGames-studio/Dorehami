package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmCoachingTest {

    @Test
    fun `empty legal returns empty card`() {
        val h = HokmCoaching.suggest(emptyList(), '♠', '♥')
        assertEquals("", h.card)
    }

    @Test
    fun `single legal returns it`() {
        val h = HokmCoaching.suggest(listOf("7♥"), '♠', '♥')
        assertEquals("7♥", h.card)
        assertTrue(h.reason.contains("تنها"))
    }

    @Test
    fun `leading returns lowest`() {
        val h = HokmCoaching.suggest(listOf("A♥", "3♥", "K♥"), '♠', '♥', null)
        assertEquals("3♥", h.card)
    }

    @Test
    fun `partner winning dumps lowest`() {
        val h = HokmCoaching.suggest(listOf("A♥", "3♥"), '♠', '♥', "K♥", true)
        assertEquals("3♥", h.card)
        assertTrue(h.reason.contains("هم‌تیمی"))
    }

    @Test
    fun `can win plays lowest winning`() {
        // current best 7♥, we have 9♥ and A♥; lowest winning is 9♥
        val h = HokmCoaching.suggest(listOf("9♥", "A♥", "3♣"), '♠', '♥', "7♥", false)
        assertEquals("9♥", h.card)
        assertTrue(h.reason.contains("برنده"))
    }

    @Test
    fun `cannot win dumps lowest of lead suit`() {
        // current best K♥, we have 3♥ and 5♣; cannot win with 3♥ (lower), dump lowest of lead suit
        val h = HokmCoaching.suggest(listOf("3♥", "5♣"), '♠', '♥', "K♥", false)
        assertEquals("3♥", h.card)
    }

    @Test
    fun `trump beats non-trump`() {
        assertTrue(HokmCoaching.beats("A♠", "K♥", '♠', '♥'))
    }

    @Test
    fun `non-trump cannot beat trump`() {
        assertTrue(!HokmCoaching.beats("A♥", "K♠", '♠', '♥'))
    }

    @Test
    fun `same suit lower rank wins`() {
        assertTrue(HokmCoaching.beats("K♥", "9♥", '♠', '♥'))
    }

    @Test
    fun `different non-trump cannot beat`() {
        assertTrue(!HokmCoaching.beats("A♦", "K♥", '♠', '♥'))
    }

    @Test
    fun `reasons are non-empty persian`() {
        val h = HokmCoaching.suggest(listOf("A♥", "K♥"), '♠', '♥', "7♥", false)
        assertTrue(h.reason.isNotEmpty())
    }
}
