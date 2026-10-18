package ir.dorehami.games.hokm.ui

/**
 * GX-05 (depth slice) — depth helpers as **pure, unit-testable** logic.
 */
object HokmDepth {

    fun zForCard(isSelected: Boolean, isDragging: Boolean): Float =
        when {
            isDragging -> 10f
            isSelected -> 5f
            else -> 1f
        }

    fun elevation(isSelected: Boolean): Float = if (isSelected) 8f else 2f
}
