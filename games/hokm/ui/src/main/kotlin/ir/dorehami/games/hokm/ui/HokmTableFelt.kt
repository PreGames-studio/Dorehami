package ir.dorehami.games.hokm.ui

/**
 * GX-01/GX-07 (table felt slice) — table felt catalog as **pure, unit-testable** logic.
 */
object HokmTableFelt {

    enum class Felt { EMERALD, BURGUNDY, TEAL, DARK_VELVET }

    fun displayName(f: Felt): String = when (f) {
        Felt.EMERALD -> "سبز ایرانی"
        Felt.BURGUNDY -> "عنابی"
        Felt.TEAL -> "فیروزه‌ای"
        Felt.DARK_VELVET -> "مخمل تیره"
    }

    fun isDark(f: Felt): Boolean = f == Felt.DARK_VELVET

    fun defaultFelt(): Felt = Felt.EMERALD
}
