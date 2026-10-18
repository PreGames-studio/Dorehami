package ir.dorehami.games.hokm.ui

/**
 * HK-011 (stack slice) — stack helpers as **pure, unit-testable** logic.
 */
object HokmStack {

    fun <T> push(stack: List<T>, value: T): List<T> = stack + value

    fun <T> pop(stack: List<T>): Pair<T?, List<T>> =
        if (stack.isEmpty()) null to emptyList() else stack.last() to stack.dropLast(1)

    fun <T> peek(stack: List<T>): T? = stack.lastOrNull()

    fun <T> size(stack: List<T>): Int = stack.size
}
