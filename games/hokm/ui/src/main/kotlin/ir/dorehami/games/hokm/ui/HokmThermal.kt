package ir.dorehami.games.hokm.ui

/**
 * HK-026 (thermal slice) — thermal helpers as **pure, unit-testable** logic.
 */
object HokmThermal {

    enum class Level { COOL, WARM, HOT, CRITICAL }

    fun fromTemperature(celsius: Float): Level = when {
        celsius >= 45f -> Level.CRITICAL
        celsius >= 40f -> Level.HOT
        celsius >= 35f -> Level.WARM
        else -> Level.COOL
    }

    fun shouldReduceEffects(level: Level): Boolean = level == Level.HOT || level == Level.CRITICAL

    fun shouldReduceAnimations(level: Level): Boolean = level == Level.CRITICAL
}
