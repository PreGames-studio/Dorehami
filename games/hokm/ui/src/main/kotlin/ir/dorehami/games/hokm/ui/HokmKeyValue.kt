package ir.dorehami.games.hokm.ui

/**
 * HK-011 (key-value slice) — key-value store helpers as **pure, unit-testable** logic.
 */
object HokmKeyValue {

    fun get(store: Map<String, String>, key: String, default: String = ""): String =
        store[key] ?: default

    fun set(store: Map<String, String>, key: String, value: String): Map<String, String> =
        store + (key to value)

    fun remove(store: Map<String, String>, key: String): Map<String, String> =
        store - key

    fun has(store: Map<String, String>, key: String): Boolean = key in store
}
