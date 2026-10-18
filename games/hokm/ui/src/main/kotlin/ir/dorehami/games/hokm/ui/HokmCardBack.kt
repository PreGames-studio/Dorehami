package ir.dorehami.games.hokm.ui

/**
 * GX-03/GX-07 (card back slice) — card back catalog as **pure, unit-testable** logic.
 *
 * The roadmap mentions customizable card backs. This object defines the back designs
 * and a selector.
 */
object HokmCardBack {

    enum class Design { EMERALD_GIRIH, MIDNIGHT_BOTEH, TURQUOISE_STAR, CUSTOM_IMAGE }

    data class Back(val design: Design, val customUri: String? = null)

    fun defaultBack(): Back = Back(Design.EMERALD_GIRIH)

    fun isCustom(b: Back): Boolean = b.design == Design.CUSTOM_IMAGE && !b.customUri.isNullOrBlank()

    fun displayName(d: Design): String = when (d) {
        Design.EMERALD_GIRIH -> "گره ایرانی"
        Design.MIDNIGHT_BOTEH -> "بته جقه"
        Design.TURQUOISE_STAR -> "ستاره فیروزه‌ای"
        Design.CUSTOM_IMAGE -> "تصویر دلخواه"
    }
}
