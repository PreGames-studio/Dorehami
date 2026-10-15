package ir.dorehami.games.hokm.ui

/**
 * The locked GX-01 art direction ("Emerald Royale", approved by the owner on 2026-10-15 —
 * see `games/hokm/design/gx01/README.md`) as pure ARGB values plus the readability math that
 * guards it. Keeping the hexes here (instead of only inside Compose `Color`s) lets JVM tests
 * prove the direction stays readable: cream on emerald, ink on ivory, brass on deep green.
 *
 * These are the *starting* values from the approved board; GX-02 turns them into the full
 * named token system. Do not restyle screens from here directly — the table sample (HK-015)
 * is the only consumer for now.
 */
object HokmArtDirection {
    // Emerald felt: edge → base → spotlight centre (light comes from B, readability from C).
    const val FELT_EDGE: Long = 0xFF06382A
    const val FELT: Long = 0xFF0B5D43
    const val FELT_SPOTLIGHT: Long = 0xFF12805C

    // Brass rim and medallion (from A), turquoise inlay (Iranian accent, used sparingly).
    const val BRASS: Long = 0xFFC9A227
    const val BRASS_LIGHT: Long = 0xFFD9B55A
    const val TURQUOISE: Long = 0xFF2BB3A3

    // Card stock and inks (jumbo indices must stay high-contrast at real size).
    const val IVORY: Long = 0xFFF5EEDD
    const val INK_BLACK: Long = 0xFF1C1C1E
    const val INK_RED: Long = 0xFFB3202B

    // Deep surround behind the table and the lacquer-red accent.
    const val DEEP: Long = 0xFF09271F
    const val NAVY: Long = 0xFF14264C
    const val LACQUER: Long = 0xFFA31621

    /** Corner-index height as a fraction of card width. GX-01 locks "jumbo" (>= 0.24). */
    const val JUMBO_INDEX_FRACTION: Float = 0.27f

    /** WCAG contrast ratio (1.0 – 21.0) between two opaque ARGB colours. */
    fun contrastRatio(first: Long, second: Long): Double {
        val a = relativeLuminance(first)
        val b = relativeLuminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun relativeLuminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val s = ((argb shr shift) and 0xFF) / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
