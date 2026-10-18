package ir.dorehami.games.hokm.ui

/**
 * HK-020 (deep link slice) — deep link parsing as **pure, unit-testable** logic.
 *
 * The roadmap mentions invite links. This object parses deep-link URIs into actions.
 */
object HokmDeepLink {

    sealed class Action {
        data class JoinRoom(val code: String) : Action()
        data class OpenProfile(val userId: String) : Action()
        object OpenHome : Action()
        object Unknown : Action()
    }

    fun parse(uri: String): Action {
        val trimmed = uri.trim()
        if (trimmed.isEmpty()) return Action.Unknown
        // hokm://join/CODE
        if (trimmed.startsWith("hokm://join/")) {
            val code = trimmed.removePrefix("hokm://join/").trim()
            if (code.isNotEmpty()) return Action.JoinRoom(code)
        }
        // hokm://profile/ID
        if (trimmed.startsWith("hokm://profile/")) {
            val id = trimmed.removePrefix("hokm://profile/").trim()
            if (id.isNotEmpty()) return Action.OpenProfile(id)
        }
        if (trimmed == "hokm://home" || trimmed == "hokm://") return Action.OpenHome
        return Action.Unknown
    }

    fun buildJoinLink(code: String): String = "hokm://join/$code"
}
