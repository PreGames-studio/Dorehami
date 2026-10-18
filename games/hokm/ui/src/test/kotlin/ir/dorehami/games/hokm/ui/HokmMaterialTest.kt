package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmMaterialTest {

    @Test
    fun `gloss highest reflectivity`() {
        assertTrue(HokmMaterial.reflectivity(HokmMaterial.Finish.GLOSS) > HokmMaterial.reflectivity(HokmMaterial.Finish.MATTE))
    }

    @Test
    fun `card finish satin`() {
        assertEquals(HokmMaterial.Finish.SATIN, HokmMaterial.cardFinish())
    }
}
