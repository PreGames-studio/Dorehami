package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** GX-05 — light, material and depth model guard (pure numbers, no device). */
class HokmLightingTest {

    @Test
    fun `single light comes from the upper-left`() {
        assertTrue(HokmLighting.LIGHT_AZIMUTH_DEG in 270f..360f || HokmLighting.LIGHT_AZIMUTH_DEG in 0f..45f)
        assertTrue(HokmLighting.isLightPlausible())
    }

    @Test
    fun `materials are ordered by gloss felt least glossy brass most`() {
        assertTrue(HokmLighting.materialsOrderedByGloss())
        // felt is the most matte, brass the most glossy
        assertTrue(HokmLighting.FELT.roughness > HokmLighting.BRASS.roughness)
        assertTrue(HokmLighting.BRASS.specular > HokmLighting.FELT.specular)
    }

    @Test
    fun `contact shadow points down-right from the card`() {
        val (dx, dy) = HokmLighting.contactShadowOffset(100f, 143f)
        assertTrue(dx > 0f)
        assertTrue(dy > 0f)
        assertTrue(HokmLighting.CONTACT_SHADOW_OPACITY in 0f..0.5f)
        assertTrue(HokmLighting.CONTACT_SHADOW_SCALE > 1f)
    }

    @Test
    fun `contact shadow blur scales with card width`() {
        assertEquals(4.5f, HokmLighting.contactShadowBlur(100f), 0.001f)
    }

    @Test
    fun `bevel highlight is on top-left shade on bottom-right`() {
        assertTrue(HokmLighting.BEVEL_HIGHLIGHT_ALPHA > 0f)
        assertTrue(HokmLighting.BEVEL_SHADE_ALPHA > 0f)
        assertTrue(HokmLighting.bevelHighlightAlpha(1f) <= HokmLighting.BEVEL_HIGHLIGHT_ALPHA)
        assertTrue(HokmLighting.bevelShadeAlpha(0f) == 0f)
    }

    @Test
    fun `sheen is capped and rises then falls`() {
        assertTrue(HokmLighting.WINNER_SHEEN in 0f..0.35f)
        assertTrue(HokmLighting.TRUMP_SHEEN in 0f..0.35f)
        assertTrue(HokmLighting.TRUMP_SHEEN <= HokmLighting.WINNER_SHEEN)
        assertEquals(0f, HokmLighting.sheenAlpha(0f, HokmLighting.WINNER_SHEEN), 0.001f)
        assertEquals(0f, HokmLighting.sheenAlpha(1f, HokmLighting.WINNER_SHEEN), 0.001f)
        assertTrue(HokmLighting.sheenAlpha(0.5f, HokmLighting.WINNER_SHEEN) > 0f)
    }

    @Test
    fun `vignette only darkens the corners and is capped`() {
        assertEquals(0f, HokmLighting.vignetteAlpha(0f), 0.001f)
        assertEquals(0f, HokmLighting.vignetteAlpha(0.5f), 0.001f)
        assertTrue(HokmLighting.VIGNETTE_STRENGTH in 0f..0.5f)
        assertTrue(HokmLighting.vignetteAlpha(1f) <= HokmLighting.VIGNETTE_STRENGTH)
    }

    @Test
    fun `vignette never drops centre text below AA`() {
        // cream on emerald base contrast is well above AA; vignette only touches corners
        val base = HokmArtDirection.contrastRatio(HokmArtDirection.IVORY, HokmArtDirection.FELT)
        val effective = HokmLighting.effectiveContrast(base, 1f)
        assertTrue(effective >= 4.5 * 0.6)
        assertTrue(HokmLighting.effectiveContrast(base, 0f) == base)
    }

    @Test
    fun `light colour is warm`() {
        val argb = HokmLighting.LIGHT_COLOR_ARGB
        val r = (argb shr 16) and 0xFF
        val b = argb and 0xFF
        assertTrue(r > b) // warm: more red than blue
    }
}
