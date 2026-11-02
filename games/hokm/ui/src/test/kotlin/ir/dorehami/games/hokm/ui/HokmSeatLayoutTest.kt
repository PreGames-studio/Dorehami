package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HokmSeatLayoutTest {
    @Test
    fun `every player count maps logical seats to unique positions for every local seat`() {
        for (playerCount in 2..4) {
            val expectedOffsets = (0 until playerCount).toSet()
            for (mySeat in 0 until playerCount) {
                val offsets = (0 until playerCount).map { seat ->
                    HokmSeatLayout.relativePosition(seat, mySeat, playerCount)
                }
                assertEquals(expectedOffsets, offsets.toSet(), "playerCount=$playerCount mySeat=$mySeat")
                assertEquals(0, HokmSeatLayout.relativePosition(mySeat, mySeat, playerCount))

                for (offset in 0 until playerCount) {
                    val seat = HokmSeatLayout.seatAtOffset(mySeat, offset, playerCount)
                    assertEquals(offset, HokmSeatLayout.relativePosition(seat, mySeat, playerCount))
                }
            }
        }
    }

    @Test
    fun `each local seat stays at the bottom and rotates all other table directions`() {
        val expectedDirections = mapOf(
            2 to listOf(
                HokmSeatLayout.Direction(0f, 1f),
                HokmSeatLayout.Direction(0f, -1f),
            ),
            3 to listOf(
                HokmSeatLayout.Direction(0f, 1f),
                HokmSeatLayout.Direction(0.8f, -0.7f),
                HokmSeatLayout.Direction(-0.8f, -0.7f),
            ),
            4 to listOf(
                HokmSeatLayout.Direction(0f, 1f),
                HokmSeatLayout.Direction(1f, 0f),
                HokmSeatLayout.Direction(0f, -1f),
                HokmSeatLayout.Direction(-1f, 0f),
            ),
        )

        for ((playerCount, expected) in expectedDirections) {
            for (mySeat in 0 until playerCount) {
                val actual = (0 until playerCount).map { offset ->
                    val seat = HokmSeatLayout.seatAtOffset(mySeat, offset, playerCount)
                    HokmSeatLayout.direction(seat, mySeat, playerCount)
                }
                assertEquals(expected, actual, "playerCount=$playerCount mySeat=$mySeat")
                assertEquals(HokmSeatLayout.Direction(0f, 1f), actual.first())
                assertEquals(playerCount, actual.toSet().size)
            }
        }
    }

    @Test
    fun `the deal aims at the chair, not at the trick slot`() {
        // Four players: my fan is at the bottom, the rest are reached going round the table.
        val me = HokmSeatLayout.fanTarget(seat = 1, mySeat = 1, playerCount = 4)
        assertEquals(HokmSeatLayout.Direction(0.5f, 0.90f), me)
        // Counting round the table from my seat: the next chair is on my right, then the far
        // chair, then the one on my left.
        assertEquals(HokmSeatLayout.Direction(0.93f, 0.34f), HokmSeatLayout.fanTarget(seat = 2, mySeat = 1, playerCount = 4))
        assertEquals(HokmSeatLayout.Direction(0.5f, 0.13f), HokmSeatLayout.fanTarget(seat = 3, mySeat = 1, playerCount = 4))
        assertEquals(HokmSeatLayout.Direction(0.07f, 0.34f), HokmSeatLayout.fanTarget(seat = 0, mySeat = 1, playerCount = 4))
    }

    @Test
    fun `every seat has its own target and only my own seat is at the bottom`() {
        for (playerCount in 2..4) {
            for (mySeat in 0 until playerCount) {
                val targets = (0 until playerCount).map { seat ->
                    HokmSeatLayout.fanTarget(seat, mySeat, playerCount)
                }
                assertEquals(playerCount, targets.toSet().size, "playerCount=$playerCount mySeat=$mySeat")
                assertEquals(
                    HokmSeatLayout.Direction(0.5f, 0.90f),
                    HokmSeatLayout.fanTarget(mySeat, mySeat, playerCount),
                    "my own fan must always be at the bottom",
                )
                targets.forEach { t ->
                    assertTrue(t.x in 0f..1f && t.y in 0f..1f, "target off the table: $t")
                }
            }
        }
    }
}
