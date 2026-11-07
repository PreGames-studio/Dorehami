package ir.dorehami.games.esmfamil.ui

import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.ClientState
import ir.dorehami.session.Member
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.RoomInfo
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The screen's pure glue: what a player sees, given the session views. */
class EsmFamilUiModelTest {

    private fun publicView(
        phase: String,
        revealed: Boolean,
        submitted: List<String>,
    ): JsonObject = buildJsonObject {
        put("round", 2)
        put("rounds", 5)
        put("letter", "س")
        put("phase", phase)
        put("finished", false)
        put("categories", buildJsonArray { listOf("name", "city").forEach { add(it.toJsonString()) } })
        put("submitted", buildJsonArray { submitted.forEach { add(it.toJsonString()) } })
        put("scores", buildJsonObject { put("p_1", 30); put("p_2", 10) })
        if (revealed) {
            put(
                "sheet",
                buildJsonObject {
                    put("p_1", buildJsonObject { put("name", "سارا"); put("city", "سمنان") })
                    put("p_2", buildJsonObject { put("name", "سهیل"); put("city", "") })
                },
            )
            put(
                "roundPoints",
                buildJsonObject {
                    put("p_1", buildJsonObject { put("name", 10); put("city", 5) })
                    put("p_2", buildJsonObject { put("name", 5); put("city", 0) })
                },
            )
        }
    }

    private val seats = listOf(
        EsmFamilSeat("p_1", "سارا", isMe = true),
        EsmFamilSeat("p_2", "سهیل", isMe = false),
    )

    @Test
    fun `the writing phase shows no answers and no points`() {
        val state = EsmFamilUiModel.public(publicView("WRITING", revealed = false, submitted = listOf("p_1")))
        assertEquals(EsmFamilPhase.WRITING, state.phase)
        assertEquals("س", state.letter)
        assertEquals(setOf("p_1"), state.submitted)
        assertTrue(state.sheet.isEmpty())
        assertTrue(state.roundPoints.isEmpty())
    }

    @Test
    fun `the reveal phase lines up answers, points and totals`() {
        val state = EsmFamilUiModel.public(publicView("REVEAL", revealed = true, submitted = listOf("p_1", "p_2")))
        assertEquals(EsmFamilPhase.REVEAL, state.phase)
        val rows = EsmFamilUiModel.rows(state, seats)
        assertEquals(listOf("سارا", "سهیل"), rows.map { it.name })
        assertEquals("سارا", rows[0].answers["name"])
        assertEquals(15, rows[0].points.values.sum())
        assertEquals(30, rows[0].total)
        assertTrue(rows[0].isMe)
    }

    @Test
    fun `a finished match without a table is still readable`() {
        val state = EsmFamilUiModel.public(
            buildJsonObject {
                put("round", 5)
                put("rounds", 5)
                put("phase", "FINISHED")
                put("finished", true)
                put("letter", "م")
                put("categories", buildJsonArray { add("name".toJsonString()) })
            },
        )
        assertEquals(EsmFamilPhase.FINISHED, state.phase)
        assertEquals(listOf("name"), state.categories)
    }

    @Test
    fun `badges bucket the three possible scores`() {
        assertEquals(EsmFamilBadge.Full, EsmFamilUiModel.badgeOf(10))
        assertEquals(EsmFamilBadge.Shared, EsmFamilUiModel.badgeOf(5))
        assertEquals(EsmFamilBadge.None, EsmFamilUiModel.badgeOf(0))
    }

    @Test
    fun `an empty draft counts its empty columns`() {
        assertEquals(2, EsmFamilUiModel.emptyColumns(emptyMap(), listOf("name", "city")))
        assertEquals(1, EsmFamilUiModel.emptyColumns(mapOf("name" to "سارا"), listOf("name", "city")))
    }

    @Test
    fun `the room hides bot seats and waits only for humans`() {
        val me = PlayerId("p_1")
        val room = RoomInfo(
            code = "ABCDE",
            name = "میز سارا",
            gameId = "esmfamil",
            members = listOf(
                Member(PlayerInfo(me, "سارا"), seat = 0, ready = true, connected = true, isHost = true),
                Member(
                    PlayerInfo(PlayerId("p_2"), "سهیل"),
                    seat = 1, ready = true, connected = true, isHost = false,
                ),
                Member(
                    PlayerInfo(PlayerId("bot_2"), "ربات آبی"),
                    seat = 2, ready = true, connected = true, isHost = false, isBot = true,
                ),
            ),
            started = true,
        )
        val clientState = ClientState(
            me = me,
            room = room,
            seat = 0,
            isHost = true,
            publicView = publicView("WRITING", revealed = false, submitted = listOf("p_1")),
            privateView = buildJsonObject { put("mine", buildJsonObject { put("name", "") }) },
        )
        val ui = EsmFamilRoomModel.state(clientState, "بلوتوث · نزدیک")
        assertEquals(listOf("سارا", "سهیل"), ui.seats.map { it.name })
        assertTrue(ui.seats.first().isMe)
        assertFalse(EsmFamilRoomModel.allHumansSubmitted(clientState), "سهیل هنوز برگه نداده")
    }

    @Test
    fun `the round can close as soon as every human handed a sheet in`() {
        val me = PlayerId("p_1")
        val room = RoomInfo(
            code = "ABCDE",
            name = "میز سارا",
            gameId = "esmfamil",
            members = listOf(
                Member(PlayerInfo(me, "سارا"), seat = 0, ready = true, connected = true, isHost = true),
                Member(
                    PlayerInfo(PlayerId("bot_1"), "ربات سبز"),
                    seat = 1, ready = true, connected = true, isHost = false, isBot = true,
                ),
            ),
            started = true,
        )
        val clientState = ClientState(
            me = me,
            room = room,
            seat = 0,
            isHost = true,
            publicView = publicView("WRITING", revealed = false, submitted = listOf("p_1")),
        )
        assertTrue(EsmFamilRoomModel.allHumansSubmitted(clientState))
    }
}

private fun String.toJsonString() = kotlinx.serialization.json.JsonPrimitive(this)
