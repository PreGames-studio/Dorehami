package ir.dorehami.games.hokm.ui

/**
 * HK-021/HK-011 (first launch slice) — first-launch detection as **pure, unit-testable** logic.
 *
 * The roadmap asks for onboarding and reliable save. This object tracks whether this is the
 * first launch and exposes a flag. The Android layer persists the flag.
 */
object HokmFirstLaunch {

    data class State(val launchedBefore: Boolean = false, val version: String = "")

    fun isFirstLaunch(s: State): Boolean = !s.launchedBefore

    fun markLaunched(s: State, version: String): State = s.copy(launchedBefore = true, version = version)

    fun needsOnboarding(s: State): Boolean = !s.launchedBefore

    fun needsMigration(s: State, currentVersion: String): Boolean =
        s.launchedBefore && s.version != currentVersion && s.version.isNotEmpty()
}
