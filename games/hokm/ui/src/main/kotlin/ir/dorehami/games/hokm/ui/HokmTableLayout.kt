package ir.dorehami.games.hokm.ui

/**
 * HK-015 (first slice) — table seat layout as **pure, unit-testable** logic.
 *
 * The roadmap asks for the optimal table visual. This object defines the 4-seat
 * positioning on a rectangular table (compact/medium/expanded) and the trick pile
 * center, so the Compose layer only reads numbers. Positions are normalized 0..1.
 */
object HokmTableLayout {

    data class Point(val x: Float, val y: Float)

    /** Seat index -> normalized position for a 4-player table. */
    fun seatPosition(seat: Int, totalSeats: Int = 4): Point = when (seat % totalSeats) {
        0 -> Point(0.5f, 0.85f)   // bottom (me)
        1 -> Point(0.85f, 0.5f)   // right
        2 -> Point(0.5f, 0.15f)   // top (partner)
        3 -> Point(0.15f, 0.5f)   // left
        else -> Point(0.5f, 0.5f)
    }

    /** Trick pile center. */
    val TRICK_CENTER: Point = Point(0.5f, 0.5f)

    /** Whether a seat is "me" (bottom). */
    fun isMe(seat: Int): Boolean = seat == 0

    /** Whether two seats are partners (opposite). */
    fun arePartners(a: Int, b: Int): Boolean = (a + 2) % 4 == b % 4

    /** Distance between two normalized points. */
    fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    /** Card play target position (slightly offset per seat around center). */
    fun playTarget(seat: Int): Point {
        val base = seatPosition(seat)
        return Point(
            (base.x + TRICK_CENTER.x) / 2f,
            (base.y + TRICK_CENTER.y) / 2f
        )
    }

    /** Number of seats supported. */
    const val SEATS: Int = 4
}
