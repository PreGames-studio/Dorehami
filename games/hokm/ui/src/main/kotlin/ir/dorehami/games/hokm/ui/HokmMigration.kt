package ir.dorehami.games.hokm.ui

/**
 * HK-012 (migration slice) — migration as **pure, unit-testable** logic.
 */
object HokmMigration {

    fun needsMigration(storedVersion: Int, currentVersion: Int): Boolean = storedVersion < currentVersion

    fun migrateStep(from: Int, to: Int): Int = (from + 1).coerceAtMost(to)

    fun isComplete(storedVersion: Int, currentVersion: Int): Boolean = storedVersion >= currentVersion
}
