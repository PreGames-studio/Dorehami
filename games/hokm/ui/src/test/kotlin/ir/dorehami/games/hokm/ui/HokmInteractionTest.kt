package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HokmInteractionTest {

    @Test
    fun `illegal card yields error with reason`() {
        val s = HokmInteraction.reduce(HokmInteraction.State.Idle, HokmInteraction.Action.Select("X", false))
        assertIs<HokmInteraction.State.Error>(s)
        assertTrue((s as HokmInteraction.State.Error).reason.isNotEmpty())
    }

    @Test
    fun `legal select in confirm mode goes to Selected`() {
        val s = HokmInteraction.reduce(HokmInteraction.State.Idle, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.CONFIRM)
        assertIs<HokmInteraction.State.Selected>(s)
        assertEquals("A♠", (s as HokmInteraction.State.Selected).card)
    }

    @Test
    fun `legal select in quick mode goes straight to Playing`() {
        val s = HokmInteraction.reduce(HokmInteraction.State.Idle, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        assertIs<HokmInteraction.State.Playing>(s)
    }

    @Test
    fun `confirm flow Selected to Confirming to Playing`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("K♥", true))
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Confirm)
        assertIs<HokmInteraction.State.Confirming>(s)
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Confirm)
        assertIs<HokmInteraction.State.Playing>(s)
    }

    @Test
    fun `cancel returns to Idle`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("K♥", true))
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Cancel)
        assertIs<HokmInteraction.State.Idle>(s)
    }

    @Test
    fun `double send is blocked`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        // try to select another card while playing
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("K♥", true), HokmInteraction.Mode.QUICK)
        assertIs<HokmInteraction.State.Error>(s)
        assertEquals(HokmInteraction.REASON_DOUBLE_SEND, (s as HokmInteraction.State.Error).reason)
    }

    @Test
    fun `confirm while playing is blocked`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Confirm)
        assertIs<HokmInteraction.State.Error>(s)
    }

    @Test
    fun `reset clears playing state`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Reset)
        assertIs<HokmInteraction.State.Idle>(s)
    }

    @Test
    fun `play result ok returns to Idle`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        s = HokmInteraction.reduce(s, HokmInteraction.Action.PlayResult(true))
        assertIs<HokmInteraction.State.Idle>(s)
    }

    @Test
    fun `play result fail yields error`() {
        var s: HokmInteraction.State = HokmInteraction.State.Idle
        s = HokmInteraction.reduce(s, HokmInteraction.Action.Select("A♠", true), HokmInteraction.Mode.QUICK)
        s = HokmInteraction.reduce(s, HokmInteraction.Action.PlayResult(false, HokmInteraction.REASON_ILLEGAL_MOVE))
        assertIs<HokmInteraction.State.Error>(s)
    }

    @Test
    fun `confirm with nothing selected yields error`() {
        val s = HokmInteraction.reduce(HokmInteraction.State.Idle, HokmInteraction.Action.Confirm)
        assertIs<HokmInteraction.State.Error>(s)
    }

    @Test
    fun `canSelect and isBusy helpers`() {
        assertTrue(HokmInteraction.canSelect(HokmInteraction.State.Idle))
        assertTrue(HokmInteraction.isBusy(HokmInteraction.State.Playing("X")))
        assertEquals("X", HokmInteraction.inFlightCard(HokmInteraction.State.Playing("X")))
    }
}
