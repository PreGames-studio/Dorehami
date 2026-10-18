package ir.dorehami.games.hokm.ui

/**
 * HK-031 (bandwidth slice) — bandwidth helpers as **pure, unit-testable** logic.
 */
object HokmBandwidth {

    fun isLow(kbps: Int): Boolean = kbps < 100

    fun shouldReduceAssets(kbps: Int): Boolean = isLow(kbps)

    fun assetQuality(kbps: Int): String = if (isLow(kbps)) "low" else "high"
}
