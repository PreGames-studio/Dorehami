package ir.dorehami.games.hokm.ui

/**
 * HK-026 (memory slice) — memory helpers as **pure, unit-testable** logic.
 */
object HokmMemory {

    fun isLowMemory(availableMb: Int, thresholdMb: Int = 200): Boolean = availableMb < thresholdMb

    fun shouldReduceCache(availableMb: Int): Boolean = isLowMemory(availableMb)

    fun cacheSizeMb(availableMb: Int): Int = if (isLowMemory(availableMb)) 16 else 64
}
