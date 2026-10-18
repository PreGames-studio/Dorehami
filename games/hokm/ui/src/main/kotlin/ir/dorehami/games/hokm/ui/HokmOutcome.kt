package ir.dorehami.games.hokm.ui

/**
 * HK-011 (outcome slice) — outcome helpers as **pure, unit-testable** logic.
 */
object HokmOutcome {

    sealed class Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>()
        data class Err(val message: String) : Outcome<Nothing>()
    }

    fun <T> ok(value: T): Outcome<T> = Outcome.Ok(value)
    fun err(message: String): Outcome<Nothing> = Outcome.Err(message)

    fun <T> isOk(r: Outcome<T>): Boolean = r is Outcome.Ok
    fun <T> isErr(r: Outcome<T>): Boolean = r is Outcome.Err

    fun <T> getOrNull(r: Outcome<T>): T? = (r as? Outcome.Ok)?.value
    fun <T> errorOrNull(r: Outcome<T>): String? = (r as? Outcome.Err)?.message
}
