package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmMigrationTest {

    @Test
    fun `needs migration when older`() {
        assertTrue(HokmMigration.needsMigration(1, 2))
    }

    @Test
    fun `no migration when current`() {
        assertTrue(!HokmMigration.needsMigration(2, 2))
    }

    @Test
    fun `migrate step increments`() {
        assertEquals(2, HokmMigration.migrateStep(1, 3))
    }

    @Test
    fun `migrate step caps at target`() {
        assertEquals(3, HokmMigration.migrateStep(3, 3))
    }

    @Test
    fun `is complete`() {
        assertTrue(HokmMigration.isComplete(3, 3))
        assertTrue(!HokmMigration.isComplete(2, 3))
    }
}
