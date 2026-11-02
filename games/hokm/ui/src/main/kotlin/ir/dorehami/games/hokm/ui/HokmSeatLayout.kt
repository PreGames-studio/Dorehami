package ir.dorehami.games.hokm.ui

/**
 * Player-relative positions around the table. Logical seat numbers are stable game identities;
 * this mapper rotates them so the local player is always at the bottom of the screen.
 */
internal object HokmSeatLayout {
    data class Direction(val x: Float, val y: Float)

    fun relativePosition(seat: Int, mySeat: Int, playerCount: Int): Int {
        require(playerCount in 2..4) { "Hokm tables support 2 to 4 players" }
        require(seat in 0 until playerCount) { "seat is outside the table" }
        require(mySeat in 0 until playerCount) { "mySeat is outside the table" }
        return (seat - mySeat + playerCount) % playerCount
    }

    fun seatAtOffset(mySeat: Int, offset: Int, playerCount: Int): Int {
        require(playerCount in 2..4) { "Hokm tables support 2 to 4 players" }
        require(mySeat in 0 until playerCount) { "mySeat is outside the table" }
        require(offset in 0 until playerCount) { "offset is outside the table" }
        return (mySeat + offset) % playerCount
    }

    /**
     * Where a seat's hand actually sits on screen, as a fraction of the table (0..1).
     *
     * The deal flies every card from the deck at the centre of the table to this point, so a card
     * visibly belongs to the chair it is dealt to. The values mirror the chair layout in
     * `HokmScreen`, not the abstract [direction] used for the trick slots.
     */
    fun fanTarget(seat: Int, mySeat: Int, playerCount: Int): Direction = when (playerCount) {
        2 -> if (relativePosition(seat, mySeat, playerCount) == 0) {
            Direction(0.5f, 0.90f)
        } else {
            Direction(0.5f, 0.13f)
        }
        3 -> when (relativePosition(seat, mySeat, playerCount)) {
            0 -> Direction(0.5f, 0.90f)
            1 -> Direction(0.84f, 0.17f)
            else -> Direction(0.16f, 0.17f)
        }
        4 -> when (relativePosition(seat, mySeat, playerCount)) {
            0 -> Direction(0.5f, 0.90f)
            1 -> Direction(0.93f, 0.34f)
            2 -> Direction(0.5f, 0.13f)
            else -> Direction(0.07f, 0.34f)
        }
        else -> error("Hokm tables support 2 to 4 players")
    }

    fun direction(seat: Int, mySeat: Int, playerCount: Int): Direction =
        when (playerCount) {
            2 -> if (relativePosition(seat, mySeat, playerCount) == 0) {
                Direction(0f, 1f)
            } else {
                Direction(0f, -1f)
            }
            3 -> when (relativePosition(seat, mySeat, playerCount)) {
                0 -> Direction(0f, 1f)
                1 -> Direction(0.8f, -0.7f)
                else -> Direction(-0.8f, -0.7f)
            }
            4 -> when (relativePosition(seat, mySeat, playerCount)) {
                0 -> Direction(0f, 1f)
                1 -> Direction(1f, 0f)
                2 -> Direction(0f, -1f)
                else -> Direction(-1f, 0f)
            }
            else -> error("Hokm tables support 2 to 4 players")
        }
}
