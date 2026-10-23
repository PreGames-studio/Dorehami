package ir.dorehami.games.chess.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChessAppearanceTest {
    @Test
    fun freeAppearancesStayAvailableAndStoreAppearancesFollowOwnership() {
        assertTrue(ChessBoardStyle.WOOD.isUnlocked(emptySet()))
        assertTrue(ChessPieceStyle.CLASSIC.isUnlocked(emptySet()))
        assertFalse(ChessBoardStyle.LUXURY.isUnlocked(emptySet()))
        assertFalse(ChessPieceStyle.GLOSSY_METAL.isUnlocked(emptySet()))

        val owned = setOf(ChessShopItems.BOARD_LUXURY, ChessShopItems.PIECES_GLOSSY_METAL)
        assertTrue(ChessBoardStyle.LUXURY.isUnlocked(owned))
        assertTrue(ChessPieceStyle.GLOSSY_METAL.isUnlocked(owned))
        assertFalse(ChessBoardStyle.TRADITIONAL.isUnlocked(owned))
    }
}
