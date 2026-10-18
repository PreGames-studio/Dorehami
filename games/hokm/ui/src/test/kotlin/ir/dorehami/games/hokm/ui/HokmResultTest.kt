package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmResultTest {

    @Test
    fun `win when our score reaches target`() {
        val r = HokmResult.compute(7, 3, 7, 10)
        assertEquals(HokmResult.Status.WIN, r.status)
        assertTrue(r.headline.contains("پیروزی"))
    }

    @Test
    fun `loss when their score reaches target`() {
        val r = HokmResult.compute(3, 7, 7, 10)
        assertEquals(HokmResult.Status.LOSS, r.status)
    }

    @Test
    fun `draw when both reach target`() {
        val r = HokmResult.compute(7, 7, 7, 10)
        assertEquals(HokmResult.Status.DRAW, r.status)
    }

    @Test
    fun `in progress when no one reached target`() {
        val r = HokmResult.compute(4, 3, 7, 7)
        assertEquals(HokmResult.Status.IN_PROGRESS, r.status)
        assertTrue(!HokmResult.isTerminal(r))
    }

    @Test
    fun `abandoned is not a loss`() {
        val r = HokmResult.compute(2, 5, 7, 5, true)
        assertEquals(HokmResult.Status.ABANDONED, r.status)
        assertTrue(!HokmResult.isWin(r))
        assertTrue(r.detail.contains("باخت ثبت نشد"))
    }

    @Test
    fun `win is terminal and isWin`() {
        val r = HokmResult.compute(7, 2, 7, 9)
        assertTrue(HokmResult.isTerminal(r))
        assertTrue(HokmResult.isWin(r))
    }

    @Test
    fun `score line contains both scores`() {
        val r = HokmResult.compute(7, 2, 7, 9)
        assertTrue(r.scoreLine.contains("۷"))
        assertTrue(r.scoreLine.contains("۲"))
    }

    @Test
    fun `abandoned headline is unfinished`() {
        val r = HokmResult.compute(1, 2, 7, 3, true)
        assertTrue(r.headline.contains("ناتمام"))
    }
}
