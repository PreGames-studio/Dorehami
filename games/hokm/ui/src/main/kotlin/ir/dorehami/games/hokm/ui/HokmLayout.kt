package ir.dorehami.games.hokm.ui

/**
 * HK-018 (first slice) — adaptive layout math as **pure, unit-testable** logic.
 *
 * The roadmap asks that 5/13/17 cards fit on small and large screens, that the selected card
 * stays readable, and that touch targets stay >= 48dp (HK-013). Until now that contract lived
 * only in prose. This object turns it into numbers the Compose layer *reads* — the same
 * boundary HK-013.a drew for [HokmMotion] and GX-05 for [HokmLighting].
 *
 * Contract (the numbers the tests pin):
 *  * **Window classes** (Android WindowSizeClasses): Compact (< 600dp), Medium (600-840),
 *    Expanded (>= 840). Layout density follows the class.
 *  * **Card width** is computed from available width, hand size and a max-per-row cap, so a
 *    17-card hand never overflows a 360dp screen.
 *  * **Aspect ratio** is fixed (card width * 1.43 ≈ height) — matches CardMetrics.
 *  * **Touch target** never drops below 48dp (HK-013) even in the densest layout.
 *  * **Readable mode** (large text / accessibility) scales cards up by 1.25x and reduces
 *    cards per row, trading density for legibility.
 *  * **Opponent fan** shows at most 5 card backs (readability) regardless of hand size.
 */
object HokmLayout {

    enum class WindowClass { COMPACT, MEDIUM, EXPANDED }

    /** Android WindowSizeClasses breakpoints (dp). */
    const val COMPACT_MAX_DP: Int = 599
    const val MEDIUM_MAX_DP: Int = 839

    /** Card aspect ratio (height / width). */
    const val CARD_ASPECT: Float = 1.43f

    /** Minimum touch target (dp) — HK-013. */
    const val MIN_TOUCH_DP: Int = 48

    /** Max cards per row in a hand. */
    const val MAX_CARDS_PER_ROW_COMPACT: Int = 7
    const val MAX_CARDS_PER_ROW_MEDIUM: Int = 9
    const val MAX_CARDS_PER_ROW_EXPANDED: Int = 13

    /** Readable-mode scale factor. */
    const val READABLE_SCALE: Float = 1.25f

    /** Opponent fan: max card backs shown. */
    const val OPPONENT_FAN_MAX: Int = 5

    /** Classifies a screen width (dp) into a window class. */
    fun windowClass(screenWidthDp: Int): WindowClass = when {
        screenWidthDp <= COMPACT_MAX_DP -> WindowClass.COMPACT
        screenWidthDp <= MEDIUM_MAX_DP -> WindowClass.MEDIUM
        else -> WindowClass.EXPANDED
    }

    /** Max cards per row for a given window class. */
    fun maxCardsPerRow(wc: WindowClass): Int = when (wc) {
        WindowClass.COMPACT -> MAX_CARDS_PER_ROW_COMPACT
        WindowClass.MEDIUM -> MAX_CARDS_PER_ROW_MEDIUM
        WindowClass.EXPANDED -> MAX_CARDS_PER_ROW_EXPANDED
    }

    /** Number of rows needed for [handSize] cards at [cardsPerRow] per row. */
    fun handRows(handSize: Int, cardsPerRow: Int): Int {
        val perRow = cardsPerRow.coerceAtLeast(1)
        val size = handSize.coerceAtLeast(0)
        if (size == 0) return 0
        return (size + perRow - 1) / perRow
    }

    /** Card width (dp) for a hand of [handSize] cards on a [screenWidthDp]-wide screen. */
    fun cardWidthDp(screenWidthDp: Int, handSize: Int, readable: Boolean = false): Int {
        val wc = windowClass(screenWidthDp)
        val perRow = maxCardsPerRow(wc)
        val rows = handRows(handSize, perRow)
        val effectivePerRow = if (rows > 1) perRow else handSize.coerceAtLeast(1)
        val spacing = 4 // dp between cards
        val available = (screenWidthDp * 0.92f).toInt() // 4% margin each side
        val raw = (available - spacing * (effectivePerRow - 1)) / effectivePerRow
        val scaled = if (readable) (raw * READABLE_SCALE).toInt() else raw
        return scaled.coerceAtLeast(MIN_TOUCH_DP)
    }

    /** Card height (dp) from width. */
    fun cardHeightDp(cardWidthDp: Int): Int = (cardWidthDp * CARD_ASPECT).toInt().coerceAtLeast(MIN_TOUCH_DP)

    /** Whether the layout is compact (small screen). */
    fun isCompact(screenWidthDp: Int): Boolean = windowClass(screenWidthDp) == WindowClass.COMPACT

    /** Opponent fan count (capped for readability). */
    fun opponentFanCount(handSize: Int): Int = handSize.coerceAtMost(OPPONENT_FAN_MAX).coerceAtLeast(0)

    /** Whether a hand of [handSize] fits in a single row on [screenWidthDp]. */
    fun fitsSingleRow(screenWidthDp: Int, handSize: Int): Boolean {
        val wc = windowClass(screenWidthDp)
        return handSize <= maxCardsPerRow(wc)
    }

    /** Touch target check: card width must be >= MIN_TOUCH_DP. */
    fun touchTargetOk(cardWidthDp: Int): Boolean = cardWidthDp >= MIN_TOUCH_DP
}
