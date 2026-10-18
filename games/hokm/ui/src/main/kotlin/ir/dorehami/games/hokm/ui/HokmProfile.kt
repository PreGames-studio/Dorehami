package ir.dorehami.games.hokm.ui

import ir.dorehami.uikit.toFa

/**
 * HK-044 (first slice) — player profile as **pure, unit-testable** logic.
 *
 * The roadmap asks for identity/profile (name, avatar, level). This object defines the
 * profile data class, validation (name length, avatar range), level/xp progression, and a
 * sanitizer. The Android layer persists and renders it.
 */
object HokmProfile {

    const val MIN_NAME_LEN: Int = 2
    const val MAX_NAME_LEN: Int = 20
    const val AVATAR_COUNT: Int = 12
    const val XP_PER_LEVEL: Int = 100

    data class Profile(
        val name: String = "بازیکن",
        val avatarId: Int = 0,
        val level: Int = 1,
        val xp: Int = 0,
        val matchesPlayed: Int = 0
    )

    fun sanitize(p: Profile): Profile {
        val name = p.name.trim().take(MAX_NAME_LEN).ifBlank { "بازیکن" }
        val safeName = if (name.length < MIN_NAME_LEN) "بازیکن" else name
        val avatar = p.avatarId.coerceIn(0, AVATAR_COUNT - 1)
        val level = p.level.coerceAtLeast(1)
        val xp = p.xp.coerceAtLeast(0)
        val matches = p.matchesPlayed.coerceAtLeast(0)
        return p.copy(name = safeName, avatarId = avatar, level = level, xp = xp, matchesPlayed = matches)
    }

    fun isValidName(name: String): Boolean =
        name.trim().length in MIN_NAME_LEN..MAX_NAME_LEN

    fun isValidAvatar(id: Int): Boolean = id in 0 until AVATAR_COUNT

    /** Add XP and return updated profile (level up if threshold reached). */
    fun addXp(p: Profile, amount: Int): Profile {
        var xp = p.xp + amount.coerceAtLeast(0)
        var level = p.level
        while (xp >= XP_PER_LEVEL) {
            xp -= XP_PER_LEVEL
            level += 1
        }
        return p.copy(xp = xp, level = level)
    }

    /** Record a completed match. */
    fun recordMatch(p: Profile): Profile = p.copy(matchesPlayed = p.matchesPlayed + 1)

    /** Display name with level. */
    fun displayName(p: Profile): String = "${p.name} (سطح ${p.level.toFa()})"

    /** XP progress 0..1 toward next level. */
    fun xpProgress(p: Profile): Float = p.xp.toFloat() / XP_PER_LEVEL.toFloat()
}
