package ir.dorehami.games.hokm.ui

/**
 * HK-033/HK-034 (retry slice) — retry policy as **pure, unit-testable** logic.
 *
 * The roadmap asks for reliable network. This object defines retry/backoff policy.
 */
object HokmRetry {

    data class Policy(val maxAttempts: Int, val baseDelayMs: Long, val maxDelayMs: Long)

    val DEFAULT: Policy = Policy(maxAttempts = 3, baseDelayMs = 500, maxDelayMs = 8000)

    fun delayFor(attempt: Int, policy: Policy = DEFAULT): Long {
        val raw = policy.baseDelayMs * (1L shl attempt.coerceAtMost(6))
        return raw.coerceAtMost(policy.maxDelayMs)
    }

    fun shouldRetry(attempt: Int, policy: Policy = DEFAULT): Boolean =
        attempt < policy.maxAttempts

    fun isLastAttempt(attempt: Int, policy: Policy = DEFAULT): Boolean =
        attempt == policy.maxAttempts - 1
}
