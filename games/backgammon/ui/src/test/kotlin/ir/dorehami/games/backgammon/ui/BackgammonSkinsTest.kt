package ir.dorehami.games.backgammon.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The board/checker catalogue of the backgammon shop. */
class BackgammonSkinsTest {

    @Test
    fun `unknown ids fall back to the classic set`() {
        assertEquals(BackgammonSkins.DefaultBoard, BackgammonSkins.board(null))
        assertEquals(BackgammonSkins.DefaultBoard, BackgammonSkins.board("missing"))
        assertEquals(BackgammonSkins.DefaultPieces, BackgammonSkins.pieces(""))
    }

    @Test
    fun `every design round-trips and has a name`() {
        assertEquals(BackgammonSkins.boards.size, BackgammonSkins.boards.map { it.id }.toSet().size)
        assertEquals(BackgammonSkins.pieces.size, BackgammonSkins.pieces.map { it.id }.toSet().size)
        BackgammonSkins.boards.forEach {
            assertTrue(it.titleFa.isNotBlank())
            assertEquals(it, BackgammonSkins.board(it.id))
        }
        BackgammonSkins.pieces.forEach {
            assertTrue(it.titleFa.isNotBlank())
            assertEquals(it, BackgammonSkins.pieces(it.id))
        }
    }

    @Test
    fun `designs unlock with wins`() {
        val locked = BackgammonSkins.boards.first { it.winsRequired > 0 }
        assertFalse(BackgammonSkins.unlocked(locked, locked.winsRequired - 1))
        assertTrue(BackgammonSkins.unlocked(locked, locked.winsRequired))
        assertEquals(0, BackgammonSkins.DefaultBoard.winsRequired)
        assertEquals(0, BackgammonSkins.DefaultPieces.winsRequired)
    }

    @Test
    fun `the default look keeps the classic board and checkers`() {
        val look = BackgammonLook()
        assertEquals(BackgammonSkins.DefaultBoard, look.board)
        assertEquals(BackgammonSkins.DefaultPieces, look.pieces)
    }
}
