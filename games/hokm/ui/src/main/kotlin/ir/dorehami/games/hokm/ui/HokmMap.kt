package ir.dorehami.games.hokm.ui

object HokmMap {
    fun <K, V> put(map: Map<K, V>, key: K, value: V): Map<K, V> = map + (key to value)
    fun <K, V> get(map: Map<K, V>, key: K, default: V? = null): V? = map[key] ?: default
    fun <K, V> remove(map: Map<K, V>, key: K): Map<K, V> = map - key
    fun <K, V> size(map: Map<K, V>): Int = map.size
    fun <K, V> containsKey(map: Map<K, V>, key: K): Boolean = key in map
}
