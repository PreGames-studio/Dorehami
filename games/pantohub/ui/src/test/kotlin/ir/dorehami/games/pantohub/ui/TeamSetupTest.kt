package ir.dorehami.games.pantohub.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The team wizard replaced the old "type every name in one comma-separated box" flow, so the seat
 * maths, the empty-seat check and the on-device text format are pinned down here.
 */
class TeamSetupTest {

    private fun team(name: String = "تیم", vararg players: String, color: Int = 0xff112233.toInt()) =
        TeamDraft(name, players.toList(), color)

    @Test
    fun `changing the seat count keeps typed names and fills the rest`() {
        val grown = team("الف", "علی", "سارا").withSeatCount(4)
        assertEquals(listOf("علی", "سارا", "بازیکن ۳", "بازیکن ۴"), grown.players)
        assertEquals("الف", grown.name)

        val shrunk = grown.withSeatCount(1)
        assertEquals(listOf("علی"), shrunk.players)
    }

    @Test
    fun `a blank seat gets its default label instead of an empty box`() {
        val draft = TeamDraft("تیم", listOf("علی", "  "), 1).withSeatCount(2)
        assertEquals(listOf("علی", "بازیکن ۲"), draft.players)
    }

    @Test
    fun `a team has no upper limit and only the floor is clamped`() {
        assertEquals(MIN_PLAYERS_PER_TEAM, team().withSeatCount(0).players.size)
        assertEquals(1, team().withSeatCount(-4).players.size)
        // A big night: fifteen people on one side must be allowed, not silently cut to the
        // quick-pick buttons.
        assertEquals(15, team().withSeatCount(15).players.size)
        assertEquals(40, team().withSeatCount(40).players.size)
        assertEquals(QUICK_SEAT_PICKS, team().withSeatCount(QUICK_SEAT_PICKS).players.size)
    }

    @Test
    fun `resizing applies to every team of the table`() {
        val table = listOf(team("الف", "علی"), team("ب", "مریم", "رضا", "نیلوفر"))
        val resized = resizeTeams(table, 2)
        assertEquals(2, resized[0].players.size)
        assertEquals(listOf("مریم", "رضا"), resized[1].players)
    }

    @Test
    fun `default table is two teams with the requested number of seats`() {
        val colors = listOf(0xff000001.toInt(), 0xff000002.toInt())
        val teams = defaultTeams(perTeam = 3, colors = colors)
        assertEquals(MIN_TEAMS, teams.size)
        assertEquals(listOf("بازیکن ۱", "بازیکن ۲", "بازیکن ۳"), teams[0].players)
        assertEquals(0xff000001.toInt(), teams[0].colorArgb)
        assertEquals(0xff000002.toInt(), teams[1].colorArgb)
    }

    @Test
    fun `names are trimmed, duplicates drop and order is kept`() {
        assertEquals(
            listOf("علی", "سارا"),
            sanitizePlayerNames(listOf(" علی ", "سارا", "علی", "   ")),
        )
    }

    @Test
    fun `the first blank seat is reported for the error message`() {
        val filled = listOf(team("الف", "علی", "سارا"), team("ب", "مریم", "رضا"))
        assertNull(firstBlankSeat(filled))

        val broken = listOf(team("الف", "علی", "سارا"), team("ب", "مریم", " "))
        assertEquals(1 to 1, firstBlankSeat(broken))
    }

    @Test
    fun `the setup survives a save and reload with every seat`() {
        val table = listOf(
            TeamDraft("تیم ۱", listOf("علی", "سارا"), 0xffd94f52.toInt()),
            TeamDraft("تیم ۲", listOf("مریم", "رضا"), 0xff3777c8.toInt()),
        )
        val restored = decodeTeams(encodeTeams(table), perTeam = 2)
        assertNotNull(restored)
        assertEquals(table, restored)
    }

    @Test
    fun `the old comma separated string is not read as teams`() {
        // Legacy prefs value (a JSON blob of `playersText`) is ignored rather than half-parsed.
        assertNull(decodeTeams("[{\"name\":\"تیم ۱\",\"players\":\"علی,سارا\"}]", perTeam = 2))
        assertNull(decodeTeams("", perTeam = 2))
        assertNull(decodeTeams(null, perTeam = 2))
    }

    @Test
    fun `tabs and newlines inside a name cannot corrupt the format`() {
        val nasty = listOf(TeamDraft("تیم\t۱", listOf("علی\nرضا", "سارا"), 7), team("ب"))
        val restored = decodeTeams(encodeTeams(nasty), perTeam = 2)
        assertNotNull(restored)
        assertEquals(MIN_TEAMS, restored.size)
        assertEquals("تیم ۱", restored[0].name)
        assertEquals("علی رضا", restored[0].players[0])
    }

    @Test
    fun `a corrupt line is skipped instead of failing the whole restore`() {
        val good = "تیم ۱\t5\tعلی\tسارا"
        val restored = decodeTeams("$good\nبلااستفاده\nتیم ۲\tnot-a-color\tمریم\tرضا", perTeam = 2)
        assertNull(restored, "one valid team is below the minimum, so nothing is restored")

        val two = decodeTeams("$good\nتیم ۲\t6\tمریم\tرضا", perTeam = 2)
        assertNotNull(two)
        assertEquals(2, two.size)
        assertTrue(two.all { it.players.size == 2 })
    }

    @Test
    fun `a big team of ten seats is allowed`() {
        // Ten people on one side is a normal Friday night; the wizard now labels all of them.
        val big = team("جمع").withSeatCount(10)
        assertEquals(10, big.players.size)
        assertEquals("بازیکن ۱", big.players.first())
        assertEquals("بازیکن ۱۰", big.players.last())
        assertEquals(QUICK_SEAT_PICKS, big.withSeatCount(QUICK_SEAT_PICKS).players.size)
    }
}
