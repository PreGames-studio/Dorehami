package ir.dorehami.games.hokm.ui

/**
 * HK-016 (extension) — accessibility helpers as **pure, unit-testable** logic.
 *
 * Complements [HokmA11y] (labels) with numeric checks: WCAG contrast ratio, focus-order
 * validation, and touch-target compliance. The Compose layer applies these; tests pin the
 * thresholds so regressions are caught without a device.
 */
object HokmAccessibility {

    /** Minimum touch target (dp) per HK-013 / Material. */
    const val MIN_TOUCH_DP: Int = 48

    /** WCAG AA thresholds. */
    const val CONTRAST_AA_NORMAL: Double = 4.5
    const val CONTRAST_AA_LARGE: Double = 3.0
    const val CONTRAST_AAA_NORMAL: Double = 7.0

    /** Relative luminance of an sRGB channel. */
    fun channelLuminance(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
    }

    /** Relative luminance of an RGB color (0..255 each). */
    fun luminance(r: Int, g: Int, b: Int): Double =
        0.2126 * channelLuminance(r) + 0.7152 * channelLuminance(g) + 0.0722 * channelLuminance(b)

    /** WCAG contrast ratio between two colors. */
    fun contrastRatio(fg: Triple<Int, Int, Int>, bg: Triple<Int, Int, Int>): Double {
        val l1 = luminance(fg.first, fg.second, fg.third)
        val l2 = luminance(bg.first, bg.second, bg.third)
        val hi = maxOf(l1, l2)
        val lo = minOf(l1, l2)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Whether contrast meets AA for normal text. */
    fun meetsAA(fg: Triple<Int, Int, Int>, bg: Triple<Int, Int, Int>, large: Boolean = false): Boolean {
        val ratio = contrastRatio(fg, bg)
        val threshold = if (large) CONTRAST_AA_LARGE else CONTRAST_AA_NORMAL
        return ratio >= threshold
    }

    /** Whether a touch target is large enough. */
    fun touchTargetOk(widthDp: Int, heightDp: Int): Boolean =
        widthDp >= MIN_TOUCH_DP && heightDp >= MIN_TOUCH_DP

    /** Focus order: returns true if indices are strictly increasing (no duplicate/out-of-order). */
    fun focusOrderValid(order: List<Int>): Boolean {
        if (order.isEmpty()) return true
        for (i in 1 until order.size) {
            if (order[i] <= order[i - 1]) return false
        }
        return true
    }

    /** Whether a content description is non-empty (screen reader needs it). */
    fun hasContentDescription(desc: String?): Boolean = !desc.isNullOrBlank()

    /** Minimum font scale support (200% = 2.0). */
    const val MAX_FONT_SCALE: Float = 2.0f

    fun fontScaleSupported(scale: Float): Boolean = scale <= MAX_FONT_SCALE
}
