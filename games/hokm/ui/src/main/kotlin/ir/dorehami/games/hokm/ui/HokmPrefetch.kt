package ir.dorehami.games.hokm.ui

/**
 * HK-031 (prefetch slice) — prefetch helpers as **pure, unit-testable** logic.
 */
object HokmPrefetch {

    fun shouldPrefetch(wifi: Boolean, batteryOk: Boolean): Boolean = wifi && batteryOk

    fun priority(asset: String): Int = when {
        asset.endsWith(".png") || asset.endsWith(".jpg") -> 1
        asset.endsWith(".mp3") || asset.endsWith(".ogg") -> 2
        else -> 3
    }
}
