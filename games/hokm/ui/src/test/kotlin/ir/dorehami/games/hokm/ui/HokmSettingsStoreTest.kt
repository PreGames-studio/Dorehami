package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSettingsStoreTest {

    @Test
    fun `encode non-empty`() {
        assertTrue(HokmSettingsStore.encode(HokmSettings.DEFAULTS).isNotEmpty())
    }

    @Test
    fun `round trip defaults`() {
        assertEquals(HokmSettings.DEFAULTS, HokmSettingsStore.roundTrip(HokmSettings.DEFAULTS))
    }

    @Test
    fun `round trip custom`() {
        val s = HokmSettings.Settings(muted = true, animSpeed = HokmSettings.AnimSpeed.FAST, tableSkin = HokmSettings.TableSkin.DARK_VELVET)
        assertEquals(s, HokmSettingsStore.roundTrip(s))
    }

    @Test
    fun `decode null returns defaults`() {
        assertEquals(HokmSettings.DEFAULTS, HokmSettingsStore.decode(null))
    }

    @Test
    fun `decode blank returns defaults`() {
        assertEquals(HokmSettings.DEFAULTS, HokmSettingsStore.decode(""))
    }

    @Test
    fun `decode corrupt returns defaults-ish`() {
        val s = HokmSettingsStore.decode("garbage")
        assertEquals(HokmSettings.DEFAULTS.muted, s.muted)
    }
}
