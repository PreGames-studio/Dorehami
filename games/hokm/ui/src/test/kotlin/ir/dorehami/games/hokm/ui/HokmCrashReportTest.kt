package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmCrashReportTest {

    @Test
    fun `create report`() {
        val r = HokmCrashReport.create(IllegalStateException("boom"), 100, "game")
        assertTrue(HokmCrashReport.shouldSend(r))
    }

    @Test
    fun `format non-empty`() {
        val r = HokmCrashReport.create(IllegalStateException("boom"), 100, "game")
        assertTrue(HokmCrashReport.format(r).isNotEmpty())
    }
}
