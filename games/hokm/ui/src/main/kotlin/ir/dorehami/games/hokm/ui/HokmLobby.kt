package ir.dorehami.games.hokm.ui

/**
 * HK-020 (lobby slice) — lobby/room model as **pure, unit-testable** logic.
 *
 * The roadmap asks for usable lobby (create room, invite, fill seats). This object
 * defines the room state, seat management, and readiness checks. The Android layer
 * drives networking.
 */
object HokmLobby {

    enum class SeatStatus { EMPTY, FILLED, BOT, READY }

    data class Seat(val index: Int, val name: String?, val status: SeatStatus)

    data class Room(
        val code: String,
        val seats: List<Seat>,
        val targetPoints: Int = 7
    )

    const val SEATS: Int = 4
    const val ROOM_CODE_LEN: Int = 6

    fun createRoom(code: String, targetPoints: Int = 7): Room {
        val seats = (0 until SEATS).map { Seat(it, null, SeatStatus.EMPTY) }
        return Room(code, seats, targetPoints)
    }

    fun fillSeat(room: Room, seatIndex: Int, name: String): Room {
        if (seatIndex !in room.seats.indices) return room
        val seats = room.seats.map { s ->
            if (s.index == seatIndex) s.copy(name = name, status = SeatStatus.FILLED) else s
        }
        return room.copy(seats = seats)
    }

    fun addBot(room: Room, seatIndex: Int): Room {
        if (seatIndex !in room.seats.indices) return room
        val seats = room.seats.map { s ->
            if (s.index == seatIndex) s.copy(name = "ربات", status = SeatStatus.BOT) else s
        }
        return room.copy(seats = seats)
    }

    fun markReady(room: Room, seatIndex: Int): Room {
        if (seatIndex !in room.seats.indices) return room
        val seats = room.seats.map { s ->
            if (s.index == seatIndex && (s.status == SeatStatus.FILLED || s.status == SeatStatus.BOT))
                s.copy(status = SeatStatus.READY) else s
        }
        return room.copy(seats = seats)
    }

    fun isFull(room: Room): Boolean = room.seats.all { it.status != SeatStatus.EMPTY }

    fun allReady(room: Room): Boolean = room.seats.all { it.status == SeatStatus.READY || it.status == SeatStatus.BOT }

    fun canStart(room: Room): Boolean = isFull(room) && allReady(room)

    fun emptySeatCount(room: Room): Int = room.seats.count { it.status == SeatStatus.EMPTY }

    fun filledSeatCount(room: Room): Int = room.seats.count { it.status != SeatStatus.EMPTY }

    fun isValidCode(code: String): Boolean = code.length == ROOM_CODE_LEN && code.all { it.isLetterOrDigit() }
}
