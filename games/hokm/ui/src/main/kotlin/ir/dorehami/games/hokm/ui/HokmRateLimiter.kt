package ir.dorehami.games.hokm.ui

/**
 * HK-010/HK-034 (rate limiter slice) — rate limiter as **pure, unit-testable** logic.
 */
object HokmRateLimiter {

    data class Bucket(val tokens: Int, val lastRefillMs: Long, val capacity: Int, val refillRatePerSec: Int)

    fun create(capacity: Int, refillRatePerSec: Int, nowMs: Long): Bucket =
        Bucket(tokens = capacity, lastRefillMs = nowMs, capacity = capacity, refillRatePerSec = refillRatePerSec)

    fun refill(b: Bucket, nowMs: Long): Bucket {
        val elapsed = (nowMs - b.lastRefillMs) / 1000.0
        val added = (elapsed * b.refillRatePerSec).toInt()
        val newTokens = (b.tokens + added).coerceAtMost(b.capacity)
        return b.copy(tokens = newTokens, lastRefillMs = nowMs)
    }

    fun tryConsume(b: Bucket, nowMs: Long, cost: Int = 1): Pair<Boolean, Bucket> {
        val refilled = refill(b, nowMs)
        return if (refilled.tokens >= cost) {
            true to refilled.copy(tokens = refilled.tokens - cost)
        } else {
            false to refilled
        }
    }
}
