package ir.dorehami.games.hokm.ui

/**
 * HK-013/HK-020 (permissions slice) — permission policy as **pure, unit-testable** logic.
 *
 * The roadmap mentions permissions (microphone for voice chat, notifications). This object
 * defines the permission state and rationale policy. The Android layer requests.
 */
object HokmPermissions {

    enum class Permission { MICROPHONE, NOTIFICATIONS, STORAGE }

    enum class Status { GRANTED, DENIED, NOT_ASKED, PERMANENTLY_DENIED }

    data class State(val statuses: Map<Permission, Status> = emptyMap())

    fun status(s: State, p: Permission): Status = s.statuses[p] ?: Status.NOT_ASKED

    fun isGranted(s: State, p: Permission): Boolean = status(s, p) == Status.GRANTED

    fun shouldShowRationale(s: State, p: Permission): Boolean =
        status(s, p) == Status.DENIED

    fun update(s: State, p: Permission, st: Status): State =
        s.copy(statuses = s.statuses + (p to st))

    fun allGranted(s: State, perms: List<Permission>): Boolean =
        perms.all { isGranted(s, it) }

    fun rationaleText(p: Permission): String = when (p) {
        Permission.MICROPHONE -> "برای چت صوتی به میکروفون نیاز است."
        Permission.NOTIFICATIONS -> "برای اعلان‌های بازی به اعلان نیاز است."
        Permission.STORAGE -> "برای ذخیرهٔ تصویر به حافظه نیاز است."
    }
}
