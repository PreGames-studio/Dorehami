package ir.dorehami.games.hokm.ui

/**
 * GX-02 — the Hokm design language as named, unit-tested tokens.
 *
 * One source of truth for colour, type, spacing, radius and motion across the table, entry and
 * lobby, so GX-03 (cards) and GX-07 (screens) build on one language instead of scattered
 * literals. Values are deliberately the ones already shipped and approved (GX-01 direction +
 * HK-015 sample): introducing tokens must not change a single pixel. Compose code reads these
 * through `HokmPalette` / `CardColors` / the table palette, which are now thin mappings over
 * this file.
 *
 * Pure Kotlin (ARGB longs and numbers) so the JVM suite can guard the language: semantic
 * contrast pairs, the 4dp spacing grid, a monotone type ladder, and motion durations that
 * stay compatible with the HK-013.a reduced-motion policy ([HokmMotion]).
 */
object HokmTokens {

    /** Semantic colours as opaque ARGB. Art-direction colours come from [HokmArtDirection]. */
    object Color {
        const val NIGHT: Long = 0xFF061913
        const val SURFACE_DEEP: Long = HokmArtDirection.DEEP
        const val SURFACE_FELT: Long = 0xFF0D3B30
        const val SURFACE_PANEL: Long = 0xFF102D25 // used with alpha in Compose (panel)
        const val SURFACE_PANEL_LIGHT: Long = 0xFF19483A
        const val ACCENT_EMERALD: Long = 0xFF14936A
        const val ACCENT_MINT: Long = 0xFF75DAB4
        const val ACCENT_BRASS: Long = HokmArtDirection.BRASS
        const val GOLD: Long = 0xFFE8C779
        const val GOLD_LIGHT: Long = 0xFFFFE9A8
        const val GOLD_DARK: Long = 0xFF936825
        const val IVORY: Long = 0xFFF8F4E9
        const val TEXT: Long = 0xFFF8F5EA
        const val TEXT_MUTED: Long = 0xFFB7CCC1
        const val DANGER: Long = 0xFFE47B69
        const val FELT: Long = HokmArtDirection.FELT
        const val FELT_SPOTLIGHT: Long = HokmArtDirection.FELT_SPOTLIGHT
        const val FELT_EDGE: Long = HokmArtDirection.FELT_EDGE
        const val TURQUOISE: Long = HokmArtDirection.TURQUOISE
        const val CARD_IVORY: Long = 0xFFFDFBF4 // shipped card stock (near-white, distinct from board cream)
        const val CARD_IVORY_SHADE: Long = 0xFFEFEBE0 // bottom of the stock gradient
        const val CARD_EDGE: Long = 0xFFCFC9B8 // hairline + side thickness of a card
        const val INK_BLACK: Long = HokmArtDirection.INK_BLACK
        const val INK_RED: Long = HokmArtDirection.INK_RED

        // GX-03 — the four-colour deck inks and the "on the felt" variants (all AA-checked).
        const val INK_BLUE: Long = 0xFF0F4C9B
        const val INK_GREEN: Long = 0xFF0F6B3F
        const val SKY_ON_DARK: Long = 0xFF8FB8FF

        /** Court-card parchment (the inset the figures sit on) and its frame gold. */
        const val COURT_PARCHMENT_TOP: Long = 0xFFF7EFD9
        const val COURT_PARCHMENT_BOTTOM: Long = 0xFFEADFBF
        const val CARD_GOLD: Long = 0xFFD4AF37
    }

    /** Persian type ladder (sp). Caption is the floor — nothing renders smaller. */
    object Type {
        const val DISPLAY_SP: Float = 26f
        const val TITLE_SP: Float = 20f
        const val HEADING_SP: Float = 17f
        const val BODY_SP: Float = 14f
        const val LABEL_SP: Float = 12f
        const val CAPTION_SP: Float = 10f

        /** The ladder, largest first; the test pins it strictly decreasing. */
        val LADDER: List<Float> = listOf(DISPLAY_SP, TITLE_SP, HEADING_SP, BODY_SP, LABEL_SP, CAPTION_SP)
    }

    /** The 4dp spacing grid. Every value is a multiple of [GRID_DP]. */
    object Space {
        const val GRID_DP: Int = 4
        const val XS_DP: Int = 4
        const val SM_DP: Int = 8
        const val MD_DP: Int = 12
        const val LG_DP: Int = 16
        const val XL_DP: Int = 24
        const val XXL_DP: Int = 32

        val ALL_DP: List<Int> = listOf(XS_DP, SM_DP, MD_DP, LG_DP, XL_DP, XXL_DP)
    }

    /** Corner radii (dp) for panels, buttons and cards. */
    object Radius {
        const val SM_DP: Int = 10
        const val MD_DP: Int = 16
        const val LG_DP: Int = 24
        const val PILL_DP: Int = 999
    }

    /**
     * Named motion durations (ms) — the only durations new UI code may use. Under the system
     * reduced-motion setting, [HokmMotion] compresses every one of these to at most
     * [HokmMotion.REDUCED_MAX_MS] instead of removing them; the test pins that contract.
     */
    object Motion {
        const val PRESS_MS: Int = 125 // the shipped button-press duration, now named
        const val FADE_MS: Int = 200
        const val CARD_MS: Int = 320
        const val COLLECT_MS: Int = 520
        const val BANNER_MS: Int = 1400

        val ALL_MS: List<Int> = listOf(PRESS_MS, FADE_MS, CARD_MS, COLLECT_MS, BANNER_MS)
    }
}
