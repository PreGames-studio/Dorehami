package ir.dorehami.games.hokm.ui

/**
 * GX-05 — the light, material and depth model of the Hokm table, as **pure, unit-testable**
 * numbers (no Compose, no Android, no device).
 *
 * The approved direction (GX-01 "Emerald Royale", HK-015 sample) is a single, warm light
 * coming from the upper-left of the table: the felt is matte emerald, the rim and medallion
 * are brass, the card stock is ivory paper with a bevelled edge. Until now that model lived
 * only in prose (ROADMAP §6 / G-8). This object turns it into one source of truth that the
 * Compose layer *reads* — exactly the boundary HK-013.a drew for [HokmMotion] and GX-04 drew
 * for [HokmChoreography].
 *
 * Contract (the numbers the tests pin):
 *  * **One light source.** A single directional light, described by an azimuth (horizontal)
 *    and an elevation (vertical). No second light is ever added — "2.5D" here means one
 *    source plus material response, not a scene graph.
 *  * **Materials** are described by ambient/diffuse/specular response and roughness, so the
 *    same light produces different sheens on felt (matte), wood (semi-gloss) and brass
 *    (glossy). Brass is the glossiest, felt the most matte.
 *  * **Contact shadow** sits *under* the card, offset down-right (the light comes from
 *    upper-left), soft and short — it sells "the card is on the table" without darkening
 *    the felt around it.
*  * **Edge bevel** is a highlight on the top-left edge and a shade on the bottom-right edge
 *    of every card; both are alpha-only so the card's ink is never re-coloured.
 *  * **Controlled sheen** (a brightening pass) is reserved for the winner of a trick and
 *    for the trump suit — never for every card, never constant. Its intensity is capped so
 *    it cannot wash out the index.
 *  * **Vignette** darkens only the corners of the table; its strength is capped so the
 *    centre of the felt — where the cards sit — stays bright enough for AA contrast.
 *  * **Readability guard:** every value here is checked against the approved palette so that
 *    lighting can never push cream-on-emerald or ink-on-ivory below AA.
 */
object HokmLighting {

    // ------------------------------------------------------------------ light source
    /** Horizontal direction of the single light, in degrees (0 = right, 90 = down on screen). */
    const val LIGHT_AZIMUTH_DEG: Float = 315f // upper-left

    /** Vertical elevation of the single light, in degrees (0 = grazing, 90 = overhead). */
    const val LIGHT_ELEVATION_DEG: Float = 52f

    /** Overall intensity of the single light (0..1). */
    const val LIGHT_INTENSITY: Float = 0.85f

    /** Warm brass-tinted light colour (ARGB). */
    const val LIGHT_COLOR_ARGB: Long = 0xFFFFF1D6

    // ------------------------------------------------------------------ materials
    /** A material response: ambient (base), diffuse (lit), specular (highlight), roughness. */
    data class Material(
        val ambient: Float,
        val diffuse: Float,
        val specular: Float,
        val roughness: Float,
    )

    /** Matte emerald felt: low specular, high roughness. */
    val FELT: Material = Material(ambient = 0.35f, diffuse = 0.70f, specular = 0.06f, roughness = 0.92f)

    /** Semi-gloss wood (rim/frame): medium specular. */
    val WOOD: Material = Material(ambient = 0.30f, diffuse = 0.60f, specular = 0.22f, roughness = 0.55f)

    /** Glossy brass (medallion/inlay): high specular, low roughness. */
    val BRASS: Material = Material(ambient = 0.32f, diffuse = 0.55f, specular = 0.62f, roughness = 0.28f)

    /** Ivory paper card stock: slight sheen, fine grain. */
    val CARD_STOCK: Material = Material(ambient = 0.55f, diffuse = 0.75f, specular = 0.18f, roughness = 0.40f)

    /** All materials, most matte first — used by tests to pin the ordering. */
    val MATERIALS_MOST_MATTE_FIRST: List<Material> = listOf(FELT, WOOD, CARD_STOCK, BRASS)

    // ------------------------------------------------------------------ contact shadow
    /** Horizontal offset of the contact shadow, as a fraction of card width (down-right). */
    const val CONTACT_SHADOW_OFFSET_X: Float = 0.018f

    /** Vertical offset of the contact shadow, as a fraction of card height (down). */
    const val CONTACT_SHADOW_OFFSET_Y: Float = 0.026f

    /** Blur radius of the contact shadow, as a fraction of card width. */
    const val CONTACT_SHADOW_BLUR: Float = 0.045f

    /** Opacity of the contact shadow (0..1). Kept soft so the felt stays readable. */
    const val CONTACT_SHADOW_OPACITY: Float = 0.34f

    /** Scale of the shadow relative to the card (slightly larger = soft edge). */
    const val CONTACT_SHADOW_SCALE: Float = 1.06f

    // ------------------------------------------------------------------ edge bevel
    /** Alpha of the highlight on the top-left bevel edge (0..1). */
    const val BEVEL_HIGHLIGHT_ALPHA: Float = 0.32f

    /** Alpha of the shade on the bottom-right bevel edge (0..1). */
    const val BEVEL_SHADE_ALPHA: Float = 0.26f

    /** Width of the bevel band, as a fraction of card width. */
    const val BEVEL_BAND: Float = 0.012f

    // ------------------------------------------------------------------ controlled sheen
    /** Sheen intensity on a trick winner (0..1). Capped so the index is never washed out. */
    const val WINNER_SHEEN: Float = 0.22f

    /** Sheen intensity on the trump suit (0..1). */
    const val TRUMP_SHEEN: Float = 0.16f

    /** Duration of the sheen pass, in ms (matches a short tween). */
    const val SHEEN_DURATION_MS: Int = 480

    // ------------------------------------------------------------------ vignette
    /** Strength of the corner vignette (0..1). Capped for centre readability. */
    const val VIGNETTE_STRENGTH: Float = 0.38f

    /** Radius of the clear centre, as a fraction of the shorter screen side. */
    const val VIGNETTE_RADIUS: Float = 0.72f

    // ------------------------------------------------------------------ helpers (pure)
    /** Contact-shadow offset in pixels for a card of the given size (width, height). */
    fun contactShadowOffset(cardWidthPx: Float, cardHeightPx: Float): Pair<Float, Float> =
        Pair(cardWidthPx * CONTACT_SHADOW_OFFSET_X, cardHeightPx * CONTACT_SHADOW_OFFSET_Y)

    /** Contact-shadow blur in pixels for a card of the given width. */
    fun contactShadowBlur(cardWidthPx: Float): Float = cardWidthPx * CONTACT_SHADOW_BLUR

    /** Bevel highlight alpha, scaled by how "lit" the top-left edge is (0..1). */
    fun bevelHighlightAlpha(lightFactor: Float): Float =
        (BEVEL_HIGHLIGHT_ALPHA * lightFactor.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** Bevel shade alpha, scaled by how "shadowed" the bottom-right edge is (0..1). */
    fun bevelShadeAlpha(shadowFactor: Float): Float =
        (BEVEL_SHADE_ALPHA * shadowFactor.coerceIn(0f, 1f)).coerceIn(0f, 1f)

    /** Sheen alpha for a winner/trump pass at progress 0..1 (rises then falls). */
    fun sheenAlpha(progress: Float, intensity: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        // smooth rise-and-fall: sin(pi * p), capped by intensity
        val curve = kotlin.math.sin(p * kotlin.math.PI).toFloat().coerceIn(0f, 1f)
        return (curve * intensity.coerceIn(0f, 1f)).coerceIn(0f, 1f)
    }

    /** Vignette darkening at a normalised distance from centre (0 = centre, 1 = corner). */
    fun vignetteAlpha(distanceFromCentre: Float): Float {
        val d = distanceFromCentre.coerceIn(0f, 1f)
        if (d <= VIGNETTE_RADIUS) return 0f
        val t = ((d - VIGNETTE_RADIUS) / (1f - VIGNETTE_RADIUS)).coerceIn(0f, 1f)
        return (t * t * VIGNETTE_STRENGTH).coerceIn(0f, VIGNETTE_STRENGTH)
    }

    /**
     * Readability guard: the effective contrast of text on the felt once the vignette is
     * applied at the worst (corner) position. Returns the original contrast scaled by the
     * remaining light (1 - vignette). Used by tests to prove lighting never drops text
     * below AA.
     */
    fun effectiveContrast(baseContrast: Double, distanceFromCentre: Float): Double {
        val remaining = (1.0 - vignetteAlpha(distanceFromCentre)).coerceIn(0.0, 1.0)
        return baseContrast * remaining.coerceAtLeast(0.6)
    }

    /** True when the light elevation is physically plausible (above the table, not grazing). */
    fun isLightPlausible(): Boolean = LIGHT_ELEVATION_DEG in 30f..75f && LIGHT_INTENSITY in 0.5f..1f

    /** True when brass is glossier than wood, wood glossier than felt (material ordering). */
    fun materialsOrderedByGloss(): Boolean =
        BRASS.specular > WOOD.specular && WOOD.specular > FELT.specular &&
            FELT.roughness > WOOD.roughness && WOOD.roughness > BRASS.roughness
}
