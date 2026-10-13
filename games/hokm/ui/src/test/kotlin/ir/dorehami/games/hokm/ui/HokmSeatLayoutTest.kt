package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
