package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The card-back / table catalogue behind the Hokm shop. The designs are plain data, so the ids, the
 * fallbacks and the unlock rules are pinned on the JVM — a broken id would otherwise only show up as
 * the wrong card back after a restart.
 */
class HokmSkinsTest {

    @Test
    fun `unknown and empty ids fall back to the classic designs`() {
        assertEquals(HokmSkins.DefaultCardBack, HokmSkins.cardBack(null))
        assertEquals(HokmSkins.DefaultCardBack, HokmSkins.cardBack(""))
        assertEquals(HokmSkins.DefaultCardBack, HokmSkins.cardBack("no-such-design"))
        assertEquals(HokmSkins.DefaultTable, HokmSkins.table(null))
        assertEquals(HokmSkins.DefaultTable, HokmSkins.table("no-such-design"))
    }

    @Test
    fun `every design has a unique id and a Persian name`() {
        val cardIds = HokmSkins.cardBacks.map { it.id }
        assertEquals(cardIds.size, cardIds.toSet().size, "card-back ids must be unique")
        val tableIds = HokmSkins.tables.map { it.id }
        assertEquals(tableIds.size, tableIds.toSet().size, "table ids must be unique")
        HokmSkins.cardBacks.forEach { assertTrue(it.titleFa.isNotBlank(), "card back ${it.id} needs a name") }
        HokmSkins.tables.forEach { assertTrue(it.titleFa.isNotBlank(), "table ${it.id} needs a name") }
    }

    @Test
    fun `ids round-trip through the catalogue`() {
        HokmSkins.cardBacks.forEach { assertEquals(it, HokmSkins.cardBack(it.id)) }
        HokmSkins.tables.forEach { assertEquals(it, HokmSkins.table(it.id)) }
    }

    @Test
    fun `designs unlock with local wins only`() {
        val locked = HokmSkins.cardBacks.first { it.winsRequired > 0 }
        assertFalse(HokmSkins.unlocked(locked.winsRequired, locked.winsRequired - 1))
        assertTrue(HokmSkins.unlocked(locked.winsRequired, locked.winsRequired))

        // The first design of each list is the always-available one.
        assertEquals(0, HokmSkins.cardBacks.first().winsRequired)
        assertEquals(0, HokmSkins.tables.first().winsRequired)
        // Thresholds are reachable: a player who keeps winning opens the whole shop.
        val maxThreshold = (HokmSkins.cardBacks + HokmSkins.tables).maxOf { it.winsRequired }
        assertTrue(maxThreshold <= 12, "the last design must not need more than a dozen wins")
    }

    @Test
    fun `the default look is the classic set`() {
        val look = HokmLook()
        assertEquals(HokmSkins.DefaultCardBack, look.cardBack)
        assertEquals(HokmSkins.DefaultTable, look.table)
    }
}
