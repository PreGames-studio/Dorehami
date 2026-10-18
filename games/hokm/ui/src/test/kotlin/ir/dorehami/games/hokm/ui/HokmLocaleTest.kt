package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmLocaleTest {

    @Test
    fun `fa is rtl`() {
        assertTrue(HokmLocale.isRtl(HokmLocale.Locale.FA))
    }

    @Test
    fun `en is ltr`() {
        assertTrue(!HokmLocale.isRtl(HokmLocale.Locale.EN))
    }

    @Test
    fun `default is fa`() {
        assertEquals(HokmLocale.Locale.FA, HokmLocale.defaultLocale())
    }

    @Test
    fun `fromCode fa`() {
        assertEquals(HokmLocale.Locale.FA, HokmLocale.fromCode("fa"))
        assertEquals(HokmLocale.Locale.FA, HokmLocale.fromCode("fa-IR"))
    }

    @Test
    fun `fromCode en`() {
        assertEquals(HokmLocale.Locale.EN, HokmLocale.fromCode("en"))
    }

    @Test
    fun `round trip code`() {
        HokmLocale.Locale.values().forEach { l ->
            assertEquals(l, HokmLocale.fromCode(HokmLocale.toCode(l)))
        }
    }

    @Test
    fun `display names non-empty`() {
        HokmLocale.Locale.values().forEach { l ->
            assertTrue(HokmLocale.displayName(l).isNotEmpty())
        }
    }
}
