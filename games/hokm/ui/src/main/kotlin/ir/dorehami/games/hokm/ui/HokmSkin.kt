package ir.dorehami.games.hokm.ui

/**
 * HK-017 (first slice) — optimal table/card skin package as **pure, unit-testable** logic.
 *
 * The roadmap asks for the optimal table/card package. This object defines the skin catalog
 * (table + card + back combinations) that trending Hokm apps offer (classic emerald,
 * burgundy+walnut, teal Game Club, dark velvet) and a selector that picks a default based
 * on settings/quality tier. The Android layer renders the chosen skin.
 */
object HokmSkin {

    enum class TableSkin { EMERALD, BURGUNDY_WALNUT, TEAL_GAME_CLUB, DARK_VELVET }
    enum class CardSkin { CLASSIC_IVORY, CAFE_NIGHT, GAME_CLUB }
    enum class BackSkin { EMERALD_GIRIH, MIDNIGHT_BOTEH, TURQUOISE_STAR, CUSTOM }

    data class SkinPackage(
        val table: TableSkin,
        val card: CardSkin,
        val back: BackSkin,
        val nameFa: String
    )

    val PRESETS: List<SkinPackage> = listOf(
        SkinPackage(TableSkin.EMERALD, CardSkin.CLASSIC_IVORY, BackSkin.EMERALD_GIRIH, "کلاسیک ایرانی"),
        SkinPackage(TableSkin.BURGUNDY_WALNUT, CardSkin.CAFE_NIGHT, BackSkin.MIDNIGHT_BOTEH, "کافه شب"),
        SkinPackage(TableSkin.TEAL_GAME_CLUB, CardSkin.GAME_CLUB, BackSkin.TURQUOISE_STAR, "باشگاه بازی"),
        SkinPackage(TableSkin.DARK_VELVET, CardSkin.CLASSIC_IVORY, BackSkin.MIDNIGHT_BOTEH, "مخمل تیره")
    )

    fun byName(name: String): SkinPackage? = PRESETS.firstOrNull { it.nameFa == name }

    fun defaultForQuality(highQuality: Boolean): SkinPackage =
        if (highQuality) PRESETS[1] else PRESETS[0]

    fun defaultForLanguage(fa: Boolean): SkinPackage =
        if (fa) PRESETS[0] else PRESETS[2]

    fun count(): Int = PRESETS.size

    fun tableSkins(): List<TableSkin> = TableSkin.values().toList()
    fun cardSkins(): List<CardSkin> = CardSkin.values().toList()
    fun backSkins(): List<BackSkin> = BackSkin.values().toList()
}
