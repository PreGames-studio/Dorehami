package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** HK-018 first slice — adaptive layout guards (pure numbers, no device). */
class HokmLayoutTest {

    @Test
    fun `window class breakpoints`() {
        assertEquals(HokmLayout.WindowClass.COMPACT, HokmLayout.windowClass(360))
        assertEquals(HokmLayout.WindowClass.MEDIUM, HokmLayout.windowClass(700))
        assertEquals(HokmLayout.WindowClass.EXPANDED, HokmLayout.windowClass(900))
    }

    @Test
    fun `17 cards fit on a 360dp screen without overflow`() {
        val w = HokmLayout.cardWidthDp(360, 17)
        assertTrue(w >= HokmLayout.MIN_TOUCH_DP)
        assertTrue(HokmLayout.handRows(17, HokmLayout.maxCardsPerRow(HokmLayout.WindowClass.COMPACT)) >= 2)
    }

    @Test
    fun `13 cards fit on a 360dp screen`() {
        val w = HokmLayout.cardWidthDp(360, 13)
        assertTrue(w >= HokmLayout.MIN_TOUCH_DP)
    }

    @Test
    fun `5 cards fit in a single row on compact`() {
        assertTrue(HokmLayout.fitsSingleRow(360, 5))
    }

    @Test
    fun `17 cards do not fit in a single row on compact`() {
        assertTrue(!HokmLayout.fitsSingleRow(360, 17))
    }

    @Test
    fun `card height follows aspect ratio`() {
        val w = 100
        val h = HokmLayout.cardHeightDp(w)
        assertTrue(h > w)
    }

    @Test
    fun `readable mode scales cards up`() {
        val normal = HokmLayout.cardWidthDp(360, 13, false)
        val readable = HokmLayout.cardWidthDp(360, 13, true)
        assertTrue(readable >= normal)
    }

    @Test
    fun `touch target never below 48dp`() {
        for (size in listOf(5, 13, 17)) {
            for (screen in listOf(320, 360, 411, 600, 900)) {
                val w = HokmLayout.cardWidthDp(screen, size)
                assertTrue(HokmLayout.touchTargetOk(w), "screen=$screen size=$size width=$w")
            }
        }
    }

    @Test
    fun `opponent fan capped at 5`() {
        assertEquals(5, HokmLayout.opponentFanCount(13))
        assertEquals(3, HokmLayout.opponentFanCount(3))
        assertEquals(0, HokmLayout.opponentFanCount(0))
    }

    @Test
    fun `hand rows calculation`() {
        assertEquals(1, HokmLayout.handRows(5, 7))
        assertEquals(2, HokmLayout.handRows(8, 7))
        assertEquals(3, HokmLayout.handRows(17, 7))
        assertEquals(0, HokmLayout.handRows(0, 7))
    }

    @Test
    fun `compact detection`() {
        assertTrue(HokmLayout.isCompact(360))
        assertTrue(!HokmLayout.isCompact(700))
    }
}
