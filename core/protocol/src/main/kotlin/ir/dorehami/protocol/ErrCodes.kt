package ir.dorehami.protocol

/**
 * Error codes shared by every layer (docs/PROTOCOL.md section 5).
 *
 * The UI decides on the **code** and shows its own Persian text; the message that travels with a
 * code is for debugging only. Codes never change meaning — new ones are only ever added.
 */
object ErrCodes {
    // proto.*
    const val PROTO_VERSION = "proto.version"
    const val PROTO_MALFORMED = "proto.malformed"
    const val PROTO_TOO_LARGE = "proto.too_large"
    const val PROTO_UNKNOWN_TYPE = "proto.unknown_type"

    // room.*
    const val ROOM_NOT_FOUND = "room.not_found"
    const val ROOM_FULL = "room.full"
    const val ROOM_CLOSED = "room.closed"
    const val ROOM_IN_PROGRESS = "room.in_progress"

    // auth.*
    const val AUTH_UNAUTHORIZED = "auth.unauthorized"
    const val AUTH_TOKEN_EXPIRED = "auth.token_expired"
    const val AUTH_NOT_IN_ROOM = "auth.not_in_room"

    // game.*
    const val ILLEGAL_ACTION = "game.illegal_action"
    const val NOT_YOUR_TURN = "game.not_your_turn"
    const val UNKNOWN_PLAYER = "game.unknown_player"
    const val BAD_OPTIONS = "game.bad_options"
    const val BAD_PAYLOAD = "game.bad_payload"
    const val BAD_STATE = "game.bad_state"

    // transport.* / server.*
    const val TRANSPORT_PEER_TIMEOUT = "transport.peer_timeout"
    const val TRANSPORT_CLOSED = "transport.closed"
    const val SERVER_INTERNAL = "server.internal"
}
