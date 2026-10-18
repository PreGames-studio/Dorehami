package ir.dorehami.games.hokm.ui

/**
 * HK-011 (memoize slice) — memoize helpers as **pure, unit-testable** logic.
 */
object HokmMemoize {

    fun <K, V> getOrPut(cache: MutableMap<K, V>, key: K, compute: () -> V): V {
        val existing = cache[key]
        if (existing != null) return existing
        val v = compute()
        cache[key] = v
        return v
    }

    fun size(cache: Map<*, *>): Int = cache.size

    fun clear(cache: MutableMap<*, *>): MutableMap<*, *> { cache.clear(); return cache }
}
