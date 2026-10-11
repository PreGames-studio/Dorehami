package ir.dorehami.server.rooms

import ir.dorehami.session.PlayerInfo

/**
 * "Random opponent" queue: players waiting for the same game + party size are grouped; when a group
 * is full a room is created and every ticket learns its code. Long-polled by `GET /v1/matchmaking/{id}`.
 */
class Matchmaker(private val hub: RoomHub, private val nowMs: () -> Long = System::currentTimeMillis) {
    data class Ticket(val id: String, val player: PlayerInfo, val gameId: String, val partySize: Int, val createdAtMs: Long) {
        @Volatile var roomCode: String? = null
    }

    private val tickets = LinkedHashMap<String, Ticket>()
    private var counter = 0L

    @Synchronized
    fun enqueue(player: PlayerInfo, gameId: String, partySize: Int): Ticket {
        // One ticket per player per game.
        tickets.values.firstOrNull { it.player.id == player.id && it.gameId == gameId && it.roomCode == null }?.let { return it }
        val t = Ticket("mm_${++counter}", player, gameId, partySize, nowMs())
        tickets[t.id] = t
        val group = tickets.values.filter { it.roomCode == null && it.gameId == gameId && it.partySize == partySize }
        if (group.size >= partySize) {
            val room = hub.create(gameId, partySize, null, "بازی تصادفی")
            group.take(partySize).forEach { it.roomCode = room.code }
        }
        return t
    }

    @Synchronized
    fun get(id: String): Ticket? = tickets[id]

    @Synchronized
    fun cancel(id: String) { tickets.remove(id) }

    /** Players who waited too long get a room with bots instead of an endless spinner. */
    @Synchronized
    fun fillWithBotsOlderThan(ms: Long) {
        val t = nowMs()
        for (tk in tickets.values) {
            if (tk.roomCode == null && t - tk.createdAtMs > ms) {
                tk.roomCode = hub.create(tk.gameId, tk.partySize, null, "بازی با ربات").code
            }
        }
    }

    @Synchronized
    fun waiting(): Int = tickets.values.count { it.roomCode == null }
}
