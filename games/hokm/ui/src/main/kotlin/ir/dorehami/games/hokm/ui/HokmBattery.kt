package ir.dorehami.games.hokm.ui

/**
 * HK-026 (battery slice) — battery saver as **pure, unit-testable** logic.
 */
object HokmBattery {

    enum class Level { NORMAL, LOW, CRITICAL }

    fun level(percent: Int): Level = when {
        percent <= 10 -> Level.CRITICAL
        percent <= 20 -> Level.LOW
        else -> Level.NORMAL
    }

    fun shouldReduceEffects(level: Level): Boolean = level != Level.NORMAL

    fun shouldReduceAnimations(level: Level): Boolean = level == Level.CRITICAL
}
