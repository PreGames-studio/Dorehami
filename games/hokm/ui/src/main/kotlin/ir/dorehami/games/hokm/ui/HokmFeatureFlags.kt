package ir.dorehami.games.hokm.ui

/**
 * HK-026 (feature flags slice) — feature flags as **pure, unit-testable** logic.
 *
 * The roadmap mentions feature gates. This object defines a simple feature-flag store
 * with defaults and overrides, so new features (coaching, chat, stickers) can be toggled
 * without a release.
 */
object HokmFeatureFlags {

    enum class Flag { COACHING, CHAT, STICKERS, DARK_MODE, LANDSCAPE, DAILY_REWARD, LEADERBOARD }

    private val DEFAULTS: Map<Flag, Boolean> = mapOf(
        Flag.COACHING to false,
        Flag.CHAT to true,
        Flag.STICKERS to true,
        Flag.DARK_MODE to false,
        Flag.LANDSCAPE to false,
        Flag.DAILY_REWARD to true,
        Flag.LEADERBOARD to false
    )

    data class Flags(val overrides: Map<Flag, Boolean> = emptyMap())

    fun isEnabled(flags: Flags, flag: Flag): Boolean =
        flags.overrides[flag] ?: DEFAULTS[flag] ?: false

    fun set(flags: Flags, flag: Flag, value: Boolean): Flags =
        flags.copy(overrides = flags.overrides + (flag to value))

    fun reset(flags: Flags, flag: Flag): Flags =
        flags.copy(overrides = flags.overrides - flag)

    fun resetAll(): Flags = Flags()

    fun allEnabled(flags: Flags): List<Flag> = Flag.values().filter { isEnabled(flags, it) }

    fun defaultOf(flag: Flag): Boolean = DEFAULTS[flag] ?: false
}
