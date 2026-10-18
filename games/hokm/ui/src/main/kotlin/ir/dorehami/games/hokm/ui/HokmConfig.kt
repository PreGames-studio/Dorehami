package ir.dorehami.games.hokm.ui

/**
 * HK-053 (config slice) — config helpers as **pure, unit-testable** logic.
 */
object HokmConfig {

    data class Config(val entries: Map<String, String> = emptyMap())

    fun get(config: Config, key: String, default: String = ""): String =
        config.entries[key] ?: default

    fun set(config: Config, key: String, value: String): Config =
        Config(config.entries + (key to value))

    fun merge(a: Config, b: Config): Config = Config(a.entries + b.entries)
}
