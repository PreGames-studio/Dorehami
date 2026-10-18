package ir.dorehami.games.hokm.ui

/**
 * GX-02 (palette slice) — palette helpers as **pure, unit-testable** logic.
 */
object HokmPalette {

    val EMERALD = HokmColor.RGB(16, 122, 84)
    val BURGUNDY = HokmColor.RGB(128, 0, 32)
    val TEAL = HokmColor.RGB(0, 128, 128)
    val GOLD = HokmColor.RGB(212, 175, 55)

    fun forTable(felt: HokmTableFelt.Felt): HokmColor.RGB = when (felt) {
        HokmTableFelt.Felt.EMERALD -> EMERALD
        HokmTableFelt.Felt.BURGUNDY -> BURGUNDY
        HokmTableFelt.Felt.TEAL -> TEAL
        HokmTableFelt.Felt.DARK_VELVET -> HokmColor.RGB(40, 40, 60)
    }
}
